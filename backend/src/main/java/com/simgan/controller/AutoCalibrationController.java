package com.simgan.controller;

import com.simgan.entity.NdviCalibrationJob;
import com.simgan.entity.NdviRecord;
import com.simgan.repository.NdviCalibrationJobRepository;
import com.simgan.repository.NdviRecordRepository;
import com.simgan.service.NdviAutoCalibrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ndvi/calibration/auto")
@RequiredArgsConstructor
@Slf4j
public class AutoCalibrationController {

    private final NdviAutoCalibrationService autoCalibrationService;
    private final NdviCalibrationJobRepository jobRepository;
    private final NdviRecordRepository ndviRecordRepository;

    /** Start (or resume) a 12-month auto-calibration job. Returns immediately. */
    @PostMapping("/{terrainId}")
    public ResponseEntity<Map<String, Object>> start(@PathVariable Long terrainId) {
        NdviCalibrationJob job = autoCalibrationService.startJob(terrainId);
        return ResponseEntity.ok(toJobMap(job));
    }

    /**
     * Job status + timeline. The frontend polls this while a job is running and
     * also uses it to render the historical NDVI line + thresholds when the
     * job is finished.
     */
    @GetMapping("/{terrainId}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long terrainId) {
        Map<String, Object> response = new LinkedHashMap<>();

        var jobOpt = jobRepository.findFirstByTerrainIdOrderByStartedAtDesc(terrainId);
        if (jobOpt.isEmpty()) {
            response.put("hasJob", false);
            response.put("timeline", List.of());
            return ResponseEntity.ok(response);
        }

        NdviCalibrationJob job = jobOpt.get();
        response.put("hasJob", true);
        response.putAll(toJobMap(job));

        // Timeline: aggregate records by date (one mean NDVI value per scene
        // across the parcels that had pixels) — that's what the chart needs.
        if (job.getRangeStart() != null && job.getRangeEnd() != null) {
            List<NdviRecord> records = ndviRecordRepository
                    .findByTerrainIdAndCaptureDateBetweenOrderByCaptureDate(terrainId, job.getRangeStart(), job.getRangeEnd());

            Map<String, double[]> byDate = new LinkedHashMap<>();
            for (NdviRecord r : records) {
                if (r.getMeanNdvi() == null) continue;
                String key = r.getCaptureDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
                double[] acc = byDate.computeIfAbsent(key, k -> new double[]{0.0, 0.0});
                acc[0] += r.getMeanNdvi();
                acc[1] += 1.0;
            }
            List<Map<String, Object>> timeline = new ArrayList<>();
            for (var entry : byDate.entrySet()) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("date", entry.getKey());
                point.put("meanNdvi", entry.getValue()[0] / entry.getValue()[1]);
                timeline.add(point);
            }
            response.put("timeline", timeline);
            response.put("recordCount", records.size());
        } else {
            response.put("timeline", List.of());
        }

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toJobMap(NdviCalibrationJob job) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobId", job.getId());
        m.put("terrainId", job.getTerrain().getId());
        m.put("status", job.getStatus().name());
        m.put("weeksTotal", job.getWeeksTotal());
        m.put("weeksCompleted", job.getWeeksCompleted());
        m.put("scenesProcessed", job.getScenesProcessed());
        m.put("rangeStart", job.getRangeStart() != null ? job.getRangeStart().toString() : null);
        m.put("rangeEnd", job.getRangeEnd() != null ? job.getRangeEnd().toString() : null);
        m.put("currentWeekStart", job.getCurrentWeekStart() != null ? job.getCurrentWeekStart().toString() : null);
        m.put("thresholdLow", job.getThresholdLow());
        m.put("thresholdHigh", job.getThresholdHigh());
        m.put("errorMessage", job.getErrorMessage());
        m.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
        m.put("finishedAt", job.getFinishedAt() != null ? job.getFinishedAt().toString() : null);
        return m;
    }
}
