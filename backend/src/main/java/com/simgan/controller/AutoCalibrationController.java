package com.simgan.controller;

import com.simgan.entity.NdviCalibrationJob;
import com.simgan.entity.NdviTerrainRecord;
import com.simgan.repository.NdviCalibrationJobRepository;
import com.simgan.repository.NdviTerrainRecordRepository;
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
    private final NdviTerrainRecordRepository terrainRecordRepository;

    /**
     * Start (or resume) an auto-calibration job over the past {@code months}
     * months (default 12). The range is configurable so the user can look
     * further back. Returns immediately; the job runs asynchronously.
     */
    @PostMapping("/{terrainId}")
    public ResponseEntity<Map<String, Object>> start(
            @PathVariable Long terrainId,
            @RequestParam(defaultValue = "12") int months) {
        NdviCalibrationJob job = autoCalibrationService.startJob(terrainId, months);
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

        // Timeline is the full terrain-level series (across all runs + on-demand
        // weekly fetches), so re-running calibration accumulates instead of resetting.
        List<NdviTerrainRecord> records = terrainRecordRepository.findByTerrainIdOrderByCaptureDate(terrainId);
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (NdviTerrainRecord r : records) {
            if (r.getMeanNdvi() == null) continue;
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", r.getCaptureDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            point.put("meanNdvi", r.getMeanNdvi());
            point.put("sceneId", r.getSceneId());
            timeline.add(point);
        }
        response.put("timeline", timeline);
        response.put("recordCount", records.size());

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
