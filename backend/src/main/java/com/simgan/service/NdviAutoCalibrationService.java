package com.simgan.service;

import com.simgan.entity.NdviCalibrationJob;
import com.simgan.entity.Terrain;
import com.simgan.repository.NdviCalibrationJobRepository;
import com.simgan.repository.TerrainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Entry point used by the controller. Persists the Job, then hands off to
 * NdviAutoCalibrationRunner which holds the actual @Async loop. This split
 * is required because Spring's @Async / @Transactional don't fire on
 * self-invocation within the same bean.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NdviAutoCalibrationService {

    private static final int DEFAULT_MONTHS = 12;
    private static final int MIN_MONTHS = 1;
    private static final int MAX_MONTHS = 60; // up to 5 years back

    private final TerrainRepository terrainRepository;
    private final NdviCalibrationJobRepository jobRepository;
    private final NdviAutoCalibrationRunner runner;

    @Transactional
    public NdviCalibrationJob startJob(Long terrainId) {
        return startJob(terrainId, DEFAULT_MONTHS);
    }

    /**
     * Starts an auto-calibration over the past {@code months} months. The range
     * is configurable so the user can look further back than the default 12.
     */
    @Transactional
    public NdviCalibrationJob startJob(Long terrainId, int months) {
        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

        var existing = jobRepository.findFirstByTerrainIdAndStatus(terrainId, NdviCalibrationJob.Status.RUNNING);
        if (existing.isPresent()) {
            log.info("Auto-calibración ya en curso para terreno {} (job={})", terrainId, existing.get().getId());
            return existing.get();
        }

        int safeMonths = Math.max(MIN_MONTHS, Math.min(MAX_MONTHS, months));
        LocalDate today = LocalDate.now();
        LocalDate end = today;
        LocalDate start = today.minusMonths(safeMonths);
        int weeksTotal = (int) Math.ceil(java.time.temporal.ChronoUnit.DAYS.between(start, end) / 7.0);

        NdviCalibrationJob job = NdviCalibrationJob.builder()
                .terrain(terrain)
                .status(NdviCalibrationJob.Status.RUNNING)
                .weeksTotal(weeksTotal)
                .weeksCompleted(0)
                .scenesProcessed(0)
                .rangeStart(start)
                .rangeEnd(end)
                .build();
        job = jobRepository.save(job);

        log.info("Auto-calibración terreno={} job={} rango={} meses ({} -> {}, {} semanas)",
                terrainId, job.getId(), safeMonths, start, end, weeksTotal);
        runner.runJob(job.getId(), terrainId, start, end);
        return job;
    }
}
