package com.simgan.controller;

import com.simgan.entity.NdviRecord;
import com.simgan.entity.Parcel;
import com.simgan.repository.NdviCalibrationRepository;
import com.simgan.repository.NdviRecordRepository;
import com.simgan.repository.ParcelRepository;
import com.simgan.service.NdviMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ndvi/monitoring")
@RequiredArgsConstructor
@Slf4j
public class MonitoringController {

    private final NdviMonitoringService monitoringService;
    private final ParcelRepository parcelRepository;
    private final NdviRecordRepository ndviRecordRepository;
    private final NdviCalibrationRepository calibrationRepository;

    /** Per-parcel monitoring view: status, latest NDVI, history, terrain thresholds. */
    @GetMapping("/{parcelId}")
    public ResponseEntity<Map<String, Object>> getMonitoring(@PathVariable Long parcelId) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new RuntimeException("Potrero no encontrado: " + parcelId));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parcelId", parcel.getId());
        body.put("parcelName", parcel.getName());
        body.put("terrainId", parcel.getTerrain().getId());
        body.put("terrainName", parcel.getTerrain().getName());
        body.put("monitoringEnabled", Boolean.TRUE.equals(parcel.getMonitoringEnabled()));

        // Thresholds come from the terrain-level calibrations (auto or manual).
        Double thresholdHigh = calibrationRepository
                .findByTerrainIdAndParcelIdIsNullAndCalibrationType(parcel.getTerrain().getId(), "OPTIM")
                .map(c -> c.getReferenceNdvi()).orElse(null);
        Double thresholdLow = calibrationRepository
                .findByTerrainIdAndParcelIdIsNullAndCalibrationType(parcel.getTerrain().getId(), "ALERT")
                .map(c -> c.getReferenceNdvi()).orElse(null);
        body.put("thresholdHigh", thresholdHigh);
        body.put("thresholdLow", thresholdLow);

        // Last 6 months of NDVI history for this parcel.
        LocalDate today = LocalDate.now();
        List<NdviRecord> records = ndviRecordRepository
                .findByParcelIdAndCaptureDateBetweenOrderByCaptureDate(parcelId, today.minusMonths(6), today);

        List<Map<String, Object>> timeline = new ArrayList<>();
        for (NdviRecord r : records) {
            if (r.getMeanNdvi() == null) continue;
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", r.getCaptureDate().toString());
            point.put("meanNdvi", r.getMeanNdvi());
            point.put("source", r.getSource());
            point.put("sceneId", r.getPlanetSceneId());
            timeline.add(point);
        }
        body.put("timeline", timeline);
        body.put("latestNdvi", timeline.isEmpty() ? null : timeline.get(timeline.size() - 1).get("meanNdvi"));
        body.put("latestDate", timeline.isEmpty() ? null : timeline.get(timeline.size() - 1).get("date"));

        return ResponseEntity.ok(body);
    }

    /** Toggle on/off monitoring for the parcel. */
    @PutMapping("/{parcelId}")
    public ResponseEntity<Map<String, Object>> toggle(@PathVariable Long parcelId, @RequestBody Map<String, Object> body) {
        boolean enabled = body.get("enabled") instanceof Boolean b ? b : false;
        Parcel updated = monitoringService.setMonitoring(parcelId, enabled);
        return ResponseEntity.ok(Map.of(
                "parcelId", updated.getId(),
                "monitoringEnabled", updated.getMonitoringEnabled()
        ));
    }

    /** "Pedir imagen ahora" — manual current-week fetch. */
    @PostMapping("/{parcelId}/fetch-now")
    public ResponseEntity<Map<String, Object>> fetchNow(@PathVariable Long parcelId) {
        Map<String, Object> result = monitoringService.fetchCurrentWeek(parcelId);
        if (result != null && result.get("error") != null) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * "Revisar NDVI de esta semana" — on-demand full weekly run for a terrain:
     * terrain-level NDVI (updates the Línea NDVI chart), per-parcel analysis +
     * alerts, and the weekly summary email. Lets the user anticipate the
     * scheduled Monday run.
     */
    @PostMapping("/terrain/{terrainId}/fetch-week")
    public ResponseEntity<Map<String, Object>> fetchTerrainWeek(@PathVariable Long terrainId) {
        Map<String, Object> result = monitoringService.fetchCurrentWeekForTerrain(terrainId);
        if (result != null && result.get("error") != null) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
