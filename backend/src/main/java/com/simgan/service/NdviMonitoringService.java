package com.simgan.service;

import com.simgan.entity.Parcel;
import com.simgan.repository.ParcelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Weekly NDVI monitoring for parcels with monitoringEnabled=true.
 *
 * Runs every Monday at 06:00 (server time): for each terrain that has at
 * least one monitoring-enabled parcel, asks the orchestrator to fetch the
 * best Sentinel scene of the past 7 days. The orchestrator already creates
 * NdviRecords per parcel and fires alert emails when the threshold (the
 * p25 from auto-calibration, or the manual ALERT calibration) is breached.
 *
 * Manual fetch endpoint mirrors the same logic on demand.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NdviMonitoringService {

    private final ParcelRepository parcelRepository;
    private final AnalysisOrchestrator analysisOrchestrator;
    private final EmailAlertService emailAlertService;
    private final NdviAutoCalibrationRunner autoCalibrationRunner;

    /**
     * Toggle monitoring for a single parcel. The terrain-level scheduler is
     * triggered as long as ANY parcel of the terrain has monitoring on, so
     * turning a parcel off doesn't stop the rest of its terrain.
     */
    @Transactional
    public Parcel setMonitoring(Long parcelId, boolean enabled) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new RuntimeException("Potrero no encontrado: " + parcelId));
        parcel.setMonitoringEnabled(enabled);
        return parcelRepository.save(parcel);
    }

    /** Manual one-shot fetch of the current week — used by "Pedir imagen ahora". */
    public Map<String, Object> fetchCurrentWeek(Long parcelId) {
        return fetchCurrentWeek(parcelId, 0);
    }

    /**
     * Manual one-shot fetch for a week {@code weeksBack} weeks before the current
     * one. Lets the user look at a previous week when the current week has no
     * usable Sentinel scene (very common — revisit timing + cloud cover).
     */
    public Map<String, Object> fetchCurrentWeek(Long parcelId, int weeksBack) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new RuntimeException("Potrero no encontrado: " + parcelId));
        Long terrainId = parcel.getTerrain().getId();

        int back = Math.max(0, weeksBack);
        LocalDate weekEnd = LocalDate.now().minusDays(7L * back);
        LocalDate weekStart = weekEnd.minusDays(6);
        log.info("Fetch manual NDVI parcelId={} terrainId={} semanasAtras={} rango={} -> {}",
                parcelId, terrainId, back, weekStart, weekEnd);
        return analysisOrchestrator.runAnalysis(terrainId, weekStart, weekEnd, "DEFAULT");
    }

    /**
     * Runs the full weekly NDVI flow for a terrain on demand — the "Revisar NDVI
     * de esta semana" button. Computes the terrain-level NDVI (updates the Línea
     * NDVI chart), runs the per-parcel analysis (creating NdviRecords and firing
     * alerts when a parcel is below threshold), and sends the weekly summary email
     * with rotation recommendations. Lets the user anticipate the Monday run.
     */
    public Map<String, Object> fetchCurrentWeekForTerrain(Long terrainId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        log.info("Revisión NDVI semanal a demanda terreno={} rango={} -> {}", terrainId, weekStart, today);

        // Terrain-level NDVI for the chart (dedup by date inside the runner).
        Integer terrainScenes = autoCalibrationRunner.fetchTerrainWeek(terrainId, weekStart, today);

        // Per-parcel analysis + alerts, then the weekly summary email.
        Map<String, Object> result = analysisOrchestrator.runAnalysis(terrainId, weekStart, today, "DEFAULT");
        emailAlertService.sendWeeklyNdviSummaryEmail(terrainId, result);

        result.put("terrainScenesAdded", terrainScenes);
        return result;
    }

    @Scheduled(cron = "${ndvi.monitoring.cron:0 0 6 * * MON}")
    public void weeklyScheduledRun() {
        List<Parcel> enabled = parcelRepository.findByMonitoringEnabledTrue();
        if (enabled.isEmpty()) {
            log.info("Monitoreo NDVI: no hay potreros activos.");
            return;
        }

        // Group by terrain — one orchestrator run per terrain covers all its parcels
        // (downloading the Sentinel scene only once per terrain).
        Set<Long> terrainIds = new HashSet<>();
        Map<Long, String> terrainNames = new LinkedHashMap<>();
        for (Parcel p : enabled) {
            terrainIds.add(p.getTerrain().getId());
            terrainNames.put(p.getTerrain().getId(), p.getTerrain().getName());
        }

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);

        for (Long terrainId : terrainIds) {
            try {
                log.info("Monitoreo NDVI semanal terreno={} ({}) rango={} -> {}",
                        terrainId, terrainNames.get(terrainId), weekStart, today);
                Map<String, Object> result = analysisOrchestrator.runAnalysis(terrainId, weekStart, today, "DEFAULT");
                emailAlertService.sendWeeklyNdviSummaryEmail(terrainId, result);
            } catch (Exception ex) {
                log.warn("Error monitoreo NDVI terreno={}: {}", terrainId, ex.getMessage());
            }
        }
    }
}
