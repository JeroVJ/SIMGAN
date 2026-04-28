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

    private static final int CALIBRATION_WEEKS = 52;

    private final TerrainRepository terrainRepository;
    private final NdviCalibrationJobRepository jobRepository;
    private final NdviAutoCalibrationRunner runner;

    @Transactional
    public NdviCalibrationJob startJob(Long terrainId) {
        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

        var existing = jobRepository.findFirstByTerrainIdAndStatus(terrainId, NdviCalibrationJob.Status.RUNNING);
        if (existing.isPresent()) {
            log.info("Auto-calibración ya en curso para terreno {} (job={})", terrainId, existing.get().getId());
            return existing.get();
        }

        LocalDate today = LocalDate.now();
        LocalDate end = today;
        LocalDate start = today.minusWeeks(CALIBRATION_WEEKS);

        NdviCalibrationJob job = NdviCalibrationJob.builder()
                .terrain(terrain)
                .status(NdviCalibrationJob.Status.RUNNING)
                .weeksTotal(CALIBRATION_WEEKS)
                .weeksCompleted(0)
                .scenesProcessed(0)
                .rangeStart(start)
                .rangeEnd(end)
                .build();
        job = jobRepository.save(job);

        runner.runJob(job.getId(), terrainId, start, end);
        return job;
    }
}
