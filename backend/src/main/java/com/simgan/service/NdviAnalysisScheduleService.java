package com.simgan.service;

import com.simgan.entity.Terrain;
import com.simgan.repository.NdviRecordRepository;
import com.simgan.repository.TerrainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NdviAnalysisScheduleService {

    private final TerrainRepository terrainRepository;
    private final NdviRecordRepository ndviRecordRepository;
    private final AnalysisOrchestrator analysisOrchestrator;

    public Map<String, Object> configureSchedule(Long terrainId, int days) {
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("El número de días debe estar entre 1 y 365.");
        }

        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

        long ndviRecords = ndviRecordRepository.countByTerrainId(terrainId);
        if (ndviRecords == 0) {
            throw new IllegalStateException("Primero debes ejecutar al menos un análisis NDVI manual.");
        }

        LocalDate today = LocalDate.now();
        LocalDate nextRunDate = today.plusDays(days);

        terrain.setAnalysisScheduleDays(days);
        terrain.setNextAnalysisDueDate(nextRunDate);
        terrainRepository.save(terrain);

        return buildScheduleResponse(terrain, ndviRecords);
    }

    public Map<String, Object> getSchedule(Long terrainId) {
        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));
        long ndviRecords = ndviRecordRepository.countByTerrainId(terrainId);
        return buildScheduleResponse(terrain, ndviRecords);
    }

    // Ejecuta una vez al iniciar para no depender de que el backend esté encendido en una hora exacta.
    @EventListener(ApplicationReadyEvent.class)
    public void runDueScheduledAnalysesAtStartup() {
        runDueScheduledAnalyses();
    }

    @Scheduled(cron = "${ndvi.schedule.cron:0 0 */6 * * *}")
    public void runDueScheduledAnalyses() {
        LocalDate today = LocalDate.now();
        List<Terrain> dueTerrains = terrainRepository
                .findByAnalysisScheduleDaysIsNotNullAndNextAnalysisDueDateLessThanEqual(today);

        for (Terrain terrain : dueTerrains) {
            Integer days = terrain.getAnalysisScheduleDays();
            if (days == null || days < 1) {
                continue;
            }

            LocalDate dueDate = terrain.getNextAnalysisDueDate();
            if (dueDate == null || !dueDate.equals(today)) {
                // Solo se ejecuta en la fecha programada exacta.
                continue;
            }

            long ndviRecords = ndviRecordRepository.countByTerrainId(terrain.getId());
            if (ndviRecords == 0) {
                log.warn("Se omite programación NDVI para terreno {}: no hay análisis inicial.", terrain.getId());
                continue;
            }

            try {
                LocalDate endDate = today;
                LocalDate startDate = today.minusDays(days);
                analysisOrchestrator.runAnalysis(terrain.getId(), startDate, endDate, "DEFAULT");

                terrain.setNextAnalysisDueDate(today.plusDays(days));
                terrainRepository.save(terrain);

                log.info("Análisis NDVI programado ejecutado para terreno {} (cada {} días).", terrain.getId(), days);
            } catch (Exception ex) {
                log.error("Error ejecutando análisis NDVI programado para terreno {}: {}", terrain.getId(), ex.getMessage());
            }
        }
    }

    private Map<String, Object> buildScheduleResponse(Terrain terrain, long ndviRecords) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("terrainId", terrain.getId());
        response.put("terrainName", terrain.getName());
        response.put("analysisScheduleDays", terrain.getAnalysisScheduleDays());
        response.put("nextAnalysisDueDate", terrain.getNextAnalysisDueDate() != null ? terrain.getNextAnalysisDueDate().toString() : null);
        response.put("hasInitialAnalysis", ndviRecords > 0);
        return response;
    }
}
