package com.simgan.service;

import com.simgan.dto.SentinelTerrainAnalyzeResponse;
import com.simgan.entity.NdviCalibration;
import com.simgan.entity.NdviCalibrationJob;
import com.simgan.entity.NdviTerrainRecord;
import com.simgan.entity.Terrain;
import com.simgan.repository.NdviCalibrationJobRepository;
import com.simgan.repository.NdviCalibrationRepository;
import com.simgan.repository.NdviTerrainRecordRepository;
import com.simgan.repository.TerrainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 12-month auto-calibration: aggregates NDVI over the whole terrain polygon
 * (not per-parcel), so the user can calibrate before defining parcels.
 *
 * Per week:
 *   1. STAC search the best Sentinel scene (lowest cloud cover ≤ 30%).
 *   2. Ask processing-api for the terrain-level mean NDVI for that scene.
 *   3. Persist an NdviTerrainRecord linked to the job.
 *
 * Finalize:
 *   p25 / p75 of all collected mean-NDVI values → ALERT / OPTIM thresholds
 *   stored on ndvi_calibrations with parcel_id=NULL (terrain-level).
 *
 * Runs in its own component so @Async / @Transactional fire through the
 * Spring proxy (self-invocation inside the same bean would silently bypass
 * both — would run synchronously without a transaction).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NdviAutoCalibrationRunner {

    private static final int CALIBRATION_WEEKS = 52;
    private static final String SOURCE_AUTO = "SENTINEL_AUTO";
    private static final double MAX_CLOUD_COVER = 0.30;

    private final TerrainRepository terrainRepository;
    private final NdviCalibrationJobRepository jobRepository;
    private final NdviCalibrationRepository calibrationRepository;
    private final NdviTerrainRecordRepository terrainRecordRepository;
    private final SentinelApiService sentinelApi;
    private final ImageProcessingClientService imageProcessingClientService;
    private final EmailAlertService emailAlertService;

    @Async
    public void runJob(Long jobId, Long terrainId, LocalDate rangeStart, LocalDate rangeEnd) {
        log.info("Iniciando auto-calibración terreno={} job={} rango={} -> {}", terrainId, jobId, rangeStart, rangeEnd);

        Terrain terrain = terrainRepository.findById(terrainId).orElse(null);
        if (terrain == null) {
            markFailed(jobId, "Terreno no encontrado: " + terrainId);
            return;
        }
        String geoJson = terrain.getGeoJson();

        LocalDate cursor = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weeksDone = 0;
        int scenesProcessed = 0;

        while (!cursor.isAfter(rangeEnd) && weeksDone < CALIBRATION_WEEKS) {
            LocalDate weekStart = cursor;
            LocalDate weekEnd = cursor.plusDays(6).isBefore(rangeEnd) ? cursor.plusDays(6) : rangeEnd;

            updateProgress(jobId, weeksDone, scenesProcessed, weekStart);

            try {
                Integer added = processWeek(jobId, terrainId, geoJson, weekStart, weekEnd);
                if (added != null) {
                    scenesProcessed += added;
                }
            } catch (Exception ex) {
                log.warn("Auto-calibración job={} semana={} falló: {}", jobId, weekStart, ex.getMessage());
            }

            weeksDone++;
            cursor = cursor.plusWeeks(1);
        }

        try {
            finalizeJob(jobId, terrainId, weeksDone, scenesProcessed);
        } catch (Exception ex) {
            log.error("Error finalizando auto-calibración job={}: {}", jobId, ex.getMessage(), ex);
            markFailed(jobId, ex.getMessage());
        }
    }

    /**
     * Searches Sentinel for the week, picks the best (lowest-cloud) scene,
     * asks processing-api for the terrain-level NDVI, persists it.
     * Returns the number of scenes successfully processed (0 or 1).
     */
    private Integer processWeek(Long jobId, Long terrainId, String geoJson, LocalDate weekStart, LocalDate weekEnd) throws IOException {
        List<Map<String, Object>> scenes = sentinelApi.searchScenes(geoJson, weekStart, weekEnd, MAX_CLOUD_COVER);
        if (scenes == null || scenes.isEmpty()) {
            log.info("Auto-calibración job={} semana={} -> {}: no hay escenas Sentinel", jobId, weekStart, weekEnd);
            return 0;
        }

        scenes.sort(Comparator.comparingDouble(this::extractCloudCover));
        Map<String, Object> best = scenes.get(0);
        String sceneId = (String) best.get("id");
        Double cloudCover = null;
        Object cloudCoverValue = best.get("cloud_cover");
        if (cloudCoverValue instanceof Number n) {
            cloudCover = n.doubleValue();
        }
        LocalDate captureDate = extractCaptureDate(best, weekStart);

        Terrain terrain = terrainRepository.findById(terrainId).orElseThrow();
        SentinelTerrainAnalyzeResponse response = imageProcessingClientService.processTerrainScene(
                terrain, best, captureDate, sceneId, cloudCover);

        if (response.getMeanNdvi() == null || response.getPixelCount() == null || response.getPixelCount() == 0) {
            log.warn("Auto-calibración job={} semana={} escena={} sin NDVI utilizable ({})",
                    jobId, weekStart, sceneId, response.getWarning());
            return 0;
        }

        persistTerrainRecord(jobId, terrain, captureDate, sceneId, cloudCover, response);
        log.info("Auto-calibración job={} semana={} escena={} mean={} pixels={}",
                jobId, weekStart, sceneId, response.getMeanNdvi(), response.getPixelCount());
        return 1;
    }

    @Transactional
    public void persistTerrainRecord(Long jobId, Terrain terrain, LocalDate captureDate, String sceneId,
                                     Double cloudCover, SentinelTerrainAnalyzeResponse response) {
        NdviCalibrationJob job = jobRepository.findById(jobId).orElse(null);
        NdviTerrainRecord record = NdviTerrainRecord.builder()
                .terrain(terrain)
                .job(job)
                .captureDate(captureDate)
                .meanNdvi(response.getMeanNdvi())
                .minNdvi(response.getMinNdvi())
                .maxNdvi(response.getMaxNdvi())
                .stdNdvi(response.getStdNdvi())
                .medianNdvi(response.getMedianNdvi())
                .pixelCount(response.getPixelCount())
                .vegetationCoverPercent(response.getVegetationCoverPercent())
                .sceneId(sceneId)
                .cloudCoverPercent(cloudCover)
                .source("SENTINEL")
                .build();
        terrainRecordRepository.save(record);
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
    public void finalizeJob(Long jobId, Long terrainId, int weeksDone, int scenesProcessed) {
        NdviCalibrationJob job = jobRepository.findById(jobId).orElseThrow();

        List<NdviTerrainRecord> records = terrainRecordRepository.findByJobIdOrderByCaptureDate(jobId);

        List<Double> values = new ArrayList<>();
        for (NdviTerrainRecord r : records) {
            if (r.getMeanNdvi() != null && r.getMeanNdvi() >= -1.0 && r.getMeanNdvi() <= 1.0) {
                values.add(r.getMeanNdvi());
            }
        }

        if (values.size() < 4) {
            String msg = "Datos insuficientes (" + values.size() + " escenas Sentinel utilizables en 12 meses). "
                    + "Verifica las credenciales de Copernicus en processing-api o que el terreno tenga cobertura Sentinel-2.";
            job.setStatus(NdviCalibrationJob.Status.FAILED);
            job.setErrorMessage(msg);
            job.setWeeksCompleted(weeksDone);
            job.setScenesProcessed(scenesProcessed);
            job.setFinishedAt(LocalDateTime.now());
            jobRepository.save(job);
            log.warn("Auto-calibración job={} falló: {}", jobId, msg);
            emailAlertService.sendAutoCalibrationCompletedEmail(jobId);
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
        log.info("Auto-calibración job={} completada. p25={} p75={} escenas={}",
                jobId, p25, p75, values.size());
        emailAlertService.sendAutoCalibrationCompletedEmail(jobId);
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
        emailAlertService.sendAutoCalibrationCompletedEmail(jobId);
    }

    private double extractCloudCover(Map<String, Object> scene) {
        Object value = scene.get("cloud_cover");
        if (value instanceof Number n) return n.doubleValue();
        return Double.MAX_VALUE;
    }

    private LocalDate extractCaptureDate(Map<String, Object> scene, LocalDate fallback) {
        Object raw = scene.get("datetime");
        if (raw instanceof String s && s.length() >= 10) {
            try { return LocalDate.parse(s.substring(0, 10)); } catch (Exception ignored) {}
        }
        return fallback;
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
