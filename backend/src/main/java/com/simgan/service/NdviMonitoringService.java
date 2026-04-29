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

    /** Manual one-shot fetch — used by the "Pedir imagen ahora" button. */
    public Map<String, Object> fetchCurrentWeek(Long parcelId) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new RuntimeException("Potrero no encontrado: " + parcelId));
        Long terrainId = parcel.getTerrain().getId();

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        log.info("Fetch manual NDVI parcelId={} terrainId={} rango={} -> {}", parcelId, terrainId, weekStart, today);
        return analysisOrchestrator.runAnalysis(terrainId, weekStart, today, "DEFAULT");
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
