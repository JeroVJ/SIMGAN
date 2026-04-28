package com.simgan.service;

import com.simgan.entity.NdviCalibration;
import com.simgan.entity.NdviCalibrationJob;
import com.simgan.entity.NdviRecord;
import com.simgan.entity.Terrain;
import com.simgan.repository.NdviCalibrationJobRepository;
import com.simgan.repository.NdviCalibrationRepository;
import com.simgan.repository.NdviRecordRepository;
import com.simgan.repository.TerrainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Lives in its own component so that @Async / @Transactional are invoked
 * through the Spring proxy (self-invocation from within the same bean
 * silently bypasses both — would run synchronously without a transaction).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NdviAutoCalibrationRunner {

    private static final int CALIBRATION_WEEKS = 52;
    private static final String SOURCE_AUTO = "SENTINEL_AUTO";

    private final TerrainRepository terrainRepository;
    private final NdviCalibrationJobRepository jobRepository;
    private final NdviCalibrationRepository calibrationRepository;
    private final NdviRecordRepository ndviRecordRepository;
    private final AnalysisOrchestrator analysisOrchestrator;

    @Async
    public void runJob(Long jobId, Long terrainId, LocalDate rangeStart, LocalDate rangeEnd) {
        log.info("Iniciando auto-calibración terreno={} job={} rango={} -> {}", terrainId, jobId, rangeStart, rangeEnd);

        LocalDate cursor = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weeksDone = 0;
        int scenesProcessed = 0;

        while (!cursor.isAfter(rangeEnd) && weeksDone < CALIBRATION_WEEKS) {
            LocalDate weekStart = cursor;
            LocalDate weekEnd = cursor.plusDays(6).isBefore(rangeEnd) ? cursor.plusDays(6) : rangeEnd;

            updateProgress(jobId, weeksDone, scenesProcessed, weekStart);

            try {
                Map<String, Object> summary = analysisOrchestrator.runAnalysis(terrainId, weekStart, weekEnd, "DEFAULT");
                Object processed = summary.get("scenesProcessed");
                if (processed instanceof Number n) {
                    scenesProcessed += n.intValue();
                }
                log.info("Auto-calibración job={} semana={} -> {} resumen={}",
                        jobId, weekStart, weekEnd, summary.get("message"));
            } catch (Exception ex) {
                log.warn("Auto-calibración job={} semana={} falló: {}", jobId, weekStart, ex.getMessage());
            }

            weeksDone++;
            cursor = cursor.plusWeeks(1);
        }

        try {
            finalizeJob(jobId, terrainId, rangeStart, rangeEnd, weeksDone, scenesProcessed);
        } catch (Exception ex) {
            log.error("Error finalizando auto-calibración job={}: {}", jobId, ex.getMessage(), ex);
            markFailed(jobId, ex.getMessage());
        }
    }

    @Transactional
    public void updateProgress(Long jobId, int weeksDone, int scenesProcessed, LocalDate currentWeekStart) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setWeeksCompleted(weeksDone);
            job.setScenesProcessed(scenesProcessed);
            job.setCurrentWeekStart(currentWeekStart);
            jobRepository.save(job);
        });
    }

    @Transactional
    public void finalizeJob(Long jobId, Long terrainId, LocalDate rangeStart, LocalDate rangeEnd, int weeksDone, int scenesProcessed) {
        NdviCalibrationJob job = jobRepository.findById(jobId).orElseThrow();

        List<NdviRecord> records = ndviRecordRepository
                .findByTerrainIdAndCaptureDateBetweenOrderByCaptureDate(terrainId, rangeStart, rangeEnd);

        List<Double> values = new ArrayList<>();
        for (NdviRecord r : records) {
            if (r.getMeanNdvi() != null && r.getMeanNdvi() >= -1.0 && r.getMeanNdvi() <= 1.0) {
                values.add(r.getMeanNdvi());
            }
        }

        if (values.size() < 4) {
            String msg = "Datos insuficientes (" + values.size() + " registros NDVI). Verifica las credenciales de Copernicus o intenta más tarde.";
            job.setStatus(NdviCalibrationJob.Status.FAILED);
            job.setErrorMessage(msg);
            job.setWeeksCompleted(weeksDone);
            job.setScenesProcessed(scenesProcessed);
            job.setFinishedAt(LocalDateTime.now());
            jobRepository.save(job);
            log.warn("Auto-calibración job={} falló: {}", jobId, msg);
            return;
        }

        Collections.sort(values);
        double p25 = percentile(values, 25);
        double p75 = percentile(values, 75);

        Terrain terrain = terrainRepository.findById(terrainId).orElseThrow();
        LocalDate today = LocalDate.now();

        upsertCalibration(terrain, "OPTIM", p75, today, scenesProcessed);
        upsertCalibration(terrain, "ALERT", p25, today, scenesProcessed);

        job.setStatus(NdviCalibrationJob.Status.COMPLETED);
        job.setWeeksCompleted(weeksDone);
        job.setScenesProcessed(scenesProcessed);
        job.setThresholdLow(p25);
        job.setThresholdHigh(p75);
        job.setFinishedAt(LocalDateTime.now());
        jobRepository.save(job);
        log.info("Auto-calibración job={} completada. p25={} p75={} registros={}",
                jobId, p25, p75, values.size());
    }

    private void upsertCalibration(Terrain terrain, String type, double value, LocalDate date, int scenesProcessed) {
        NdviCalibration row = calibrationRepository
                .findByTerrainIdAndParcelIdIsNullAndCalibrationType(terrain.getId(), type)
                .orElseGet(() -> NdviCalibration.builder()
                        .terrain(terrain)
                        .calibrationType(type)
                        .build());
        row.setReferenceNdvi(value);
        row.setCalibrationDate(date);
        row.setSource(SOURCE_AUTO);
        row.setSceneId(null);
        row.setCloudCoverPercent(null);
        row.setPixelCount(scenesProcessed);
        calibrationRepository.save(row);
    }

    @Transactional
    public void markFailed(Long jobId, String message) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(NdviCalibrationJob.Status.FAILED);
            job.setErrorMessage(message != null && message.length() > 1000 ? message.substring(0, 1000) : message);
            job.setFinishedAt(LocalDateTime.now());
            jobRepository.save(job);
        });
    }

    private static double percentile(List<Double> sortedValues, double p) {
        if (sortedValues.isEmpty()) return 0.0;
        if (sortedValues.size() == 1) return sortedValues.get(0);
        double rank = (p / 100.0) * (sortedValues.size() - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) return sortedValues.get(lo);
        double frac = rank - lo;
        return sortedValues.get(lo) * (1 - frac) + sortedValues.get(hi) * frac;
    }
}
