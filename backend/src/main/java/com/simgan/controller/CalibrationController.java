package com.simgan.controller;

import com.simgan.dto.CalibrationDto;
import com.simgan.service.NdviCalibrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ndvi/calibration")
@RequiredArgsConstructor
@Slf4j
public class CalibrationController {

    private final NdviCalibrationService calibrationService;

    /**
     * GET /api/ndvi/calibration/status/{terrainId}?type=OPTIM|ALERT
     * Verifica si el terreno ya tiene calibración NDVI del tipo dado.
     */
    @GetMapping("/status/{terrainId}")
    public ResponseEntity<CalibrationDto.CalibrationStatus> getCalibrationStatus(
            @PathVariable Long terrainId,
            @RequestParam(defaultValue = "OPTIM") String type) {
        return ResponseEntity.ok(calibrationService.getCalibrationStatus(terrainId, type));
    }

    /**
     * GET /api/ndvi/calibration/scenes/{terrainId}?calibrationDate=YYYY-MM-DD
     * Busca escenas disponibles para la fecha de calibración sin procesarlas.
     */
    @GetMapping("/scenes/{terrainId}")
    public ResponseEntity<List<Map<String, Object>>> searchAvailableScenes(
            @PathVariable Long terrainId,
            @RequestParam String calibrationDate) {

        LocalDate date = LocalDate.parse(calibrationDate);
        LocalDate today = LocalDate.now();

        if (date.isAfter(today)) {
            return ResponseEntity.badRequest().body(List.of(Map.of(
                    "error", "La fecha de calibración no puede ser futura.")));
        }

        log.info("Buscando escenas disponibles para terreno {} en fecha {}", terrainId, date);
        List<Map<String, Object>> scenes = calibrationService.searchAvailableScenes(terrainId, date);
        return ResponseEntity.ok(scenes);
    }

    /**
     * POST /api/ndvi/calibration/{terrainId}?calibrationDate=YYYY-MM-DD&type=OPTIM|ALERT&sceneId=...
     * Ejecuta la calibración NDVI para un terreno con una escena específica.
     */
    @PostMapping("/{terrainId}")
    public ResponseEntity<Map<String, Object>> runCalibration(
            @PathVariable Long terrainId,
            @RequestParam String calibrationDate,
            @RequestParam(defaultValue = "OPTIM") String type,
            @RequestParam(required = false) String sceneId) {

        LocalDate date = LocalDate.parse(calibrationDate);
        LocalDate today = LocalDate.now();

        if (date.isAfter(today)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "La fecha de calibración no puede ser futura."));
        }

        log.info("Iniciando calibración NDVI ({}) para terreno {} en fecha {} escena={}", type, terrainId, date, sceneId);
        Map<String, Object> result = calibrationService.runCalibration(terrainId, date, type, sceneId);

        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }

        return ResponseEntity.ok(result);
    }
}
