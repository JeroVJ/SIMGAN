package com.simgan.controller;

import com.simgan.dto.BiomassCalibrationDto;
import com.simgan.service.BiomassCalibrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ndvi/biomass-calibration")
@RequiredArgsConstructor
@Slf4j
public class BiomassCalibrationController {

    private final BiomassCalibrationService biomassCalibrationService;

    /**
     * GET /api/ndvi/biomass-calibration/status/{terrainId}
     * Returns the biomass calibration status for all parcels.
     */
    @GetMapping("/status/{terrainId}")
    public ResponseEntity<BiomassCalibrationDto.BiomassCalibrationStatus> getStatus(
            @PathVariable Long terrainId) {
        return ResponseEntity.ok(biomassCalibrationService.getStatus(terrainId));
    }

    /**
     * POST /api/ndvi/biomass-calibration/{terrainId}/{parcelId}
     * Runs biomass calibration for a specific parcel using field sample points.
     */
    @PostMapping("/{terrainId}/{parcelId}")
    public ResponseEntity<?> calibrateParcel(
            @PathVariable Long terrainId,
            @PathVariable Long parcelId,
            @RequestBody BiomassCalibrationDto.CalibrateBiomassRequest request) {

        try {
            log.info("Calibración biomasa terrainId={} parcelId={} puntos={}",
                    terrainId, parcelId,
                    request.getPoints() != null ? request.getPoints().size() : 0);

            BiomassCalibrationDto.CalibrateBiomassResponse result =
                    biomassCalibrationService.calibrateParcel(terrainId, parcelId, request.getPoints());

            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.warn("Error calibración biomasa: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
