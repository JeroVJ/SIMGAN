package com.simgan.controller;

import com.simgan.dto.ParcelDto;
import com.simgan.service.ParcelService;
import com.simgan.service.RotationSchedulerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/parcels")
@RequiredArgsConstructor
public class ParcelController {

    private final ParcelService parcelService;
    private final RotationSchedulerService rotationSchedulerService;

    @PostMapping
    public ResponseEntity<ParcelDto.Response> create(@Valid @RequestBody ParcelDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parcelService.create(request));
    }

    @GetMapping("/terrain/{terrainId}")
    public ResponseEntity<List<ParcelDto.Response>> findByTerrainId(@PathVariable Long terrainId) {
        return ResponseEntity.ok(parcelService.findByTerrainId(terrainId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ParcelDto.Response> findById(@PathVariable Long id) {
        return ResponseEntity.ok(parcelService.findById(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ParcelDto.Response> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ParcelDto.StatusUpdate statusUpdate) {
        return ResponseEntity.ok(parcelService.updateStatus(id, statusUpdate.getStatus()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        parcelService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/parcels/terrain/{terrainId}/rotation-plan?loteId={loteId}
     * Returns per-parcel rotation metrics (DO, DD, carga animal) calculated in the backend.
     */
    @GetMapping("/terrain/{terrainId}/rotation-plan")
    public ResponseEntity<List<ParcelDto.RotationPlanEntry>> getRotationPlan(
            @PathVariable Long terrainId,
            @RequestParam Long loteId,
            @RequestParam String tipoAnimal,
            @RequestParam int numeroAnimales) {
        return ResponseEntity.ok(parcelService.getRotationPlan(terrainId, loteId, tipoAnimal, numeroAnimales));
    }

    /**
     * POST /api/parcels/rotation/trigger
     * Manually triggers the rotation advancement check for all active lotes.
     * Useful for testing without waiting for the nightly schedule.
     */
    @PostMapping("/rotation/trigger")
    public ResponseEntity<Map<String, String>> triggerRotation() {
        rotationSchedulerService.triggerNow();
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Rotation check executed"));
    }
}
