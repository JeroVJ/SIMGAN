package com.simgan.controller;

import com.simgan.dto.LoteDto;
import com.simgan.service.LoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/lotes")
@RequiredArgsConstructor
@Slf4j
public class LoteController {

    private final LoteService loteService;

    // ===== LOTES =====

    @GetMapping("/terrain/{terrainId}")
    public ResponseEntity<List<LoteDto.LoteResponse>> getLotesByTerrain(@PathVariable Long terrainId) {
        return ResponseEntity.ok(loteService.getLotesByTerrain(terrainId));
    }

    @GetMapping("/terrain/{terrainId}/active")
    public ResponseEntity<List<LoteDto.LoteResponse>> getActiveLotes(@PathVariable Long terrainId) {
        return ResponseEntity.ok(loteService.getActiveLotesByTerrain(terrainId));
    }

    @GetMapping("/{loteId}")
    public ResponseEntity<LoteDto.LoteResponse> getLote(@PathVariable Long loteId) {
        return ResponseEntity.ok(loteService.getLote(loteId));
    }

    @PostMapping
    public ResponseEntity<LoteDto.LoteResponse> createLote(@RequestBody LoteDto.CreateLoteRequest req) {
        return ResponseEntity.ok(loteService.createLote(req));
    }

    @PatchMapping("/{loteId}/close")
    public ResponseEntity<LoteDto.LoteResponse> closeLote(
            @PathVariable Long loteId,
            @RequestBody LoteDto.CloseLoteRequest req) {
        return ResponseEntity.ok(loteService.closeLote(loteId, req));
    }

    @DeleteMapping("/{loteId}")
    public ResponseEntity<Void> deleteLote(@PathVariable Long loteId) {
        loteService.deleteLote(loteId);
        return ResponseEntity.noContent().build();
    }

    // ===== ASIGNACIÓN PARCELA =====

    @PostMapping("/{loteId}/assign-parcel")
    public ResponseEntity<LoteDto.LoteResponse> assignParcel(
            @PathVariable Long loteId,
            @RequestBody LoteDto.AssignParcelRequest req) {
        return ResponseEntity.ok(loteService.assignParcel(loteId, req));
    }

    @PostMapping("/{loteId}/unassign-parcel")
    public ResponseEntity<LoteDto.LoteResponse> unassignParcel(@PathVariable Long loteId) {
        return ResponseEntity.ok(loteService.unassignParcel(loteId));
    }

    @PostMapping("/{loteId}/rotation-assignment")
    public ResponseEntity<LoteDto.LoteResponse> saveRotationAssignment(
            @PathVariable Long loteId,
            @RequestBody LoteDto.RotationAssignmentRequest req) {
        return ResponseEntity.ok(loteService.saveRotationAssignment(loteId, req));
    }

    // ===== GANADO =====

    @GetMapping("/{loteId}/ganado")
    public ResponseEntity<List<LoteDto.GanadoResponse>> getGanado(@PathVariable Long loteId) {
        return ResponseEntity.ok(loteService.getGanadoByLote(loteId));
    }

    @PostMapping("/{loteId}/ganado")
    public ResponseEntity<LoteDto.GanadoResponse> addGanado(
            @PathVariable Long loteId,
            @RequestBody LoteDto.CreateGanadoRequest req) {
        req.setLoteId(loteId);
        return ResponseEntity.ok(loteService.addGanado(req));
    }

    @PostMapping("/{loteId}/ganado/batch")
    public ResponseEntity<List<LoteDto.GanadoResponse>> addGanadoBatch(
            @PathVariable Long loteId,
            @RequestBody List<LoteDto.CreateGanadoRequest> requests) {
        return ResponseEntity.ok(loteService.addGanadoBatch(loteId, requests));
    }

    @PutMapping("/ganado/{ganadoId}")
    public ResponseEntity<LoteDto.GanadoResponse> updateGanado(
            @PathVariable Long ganadoId,
            @RequestBody LoteDto.UpdateGanadoRequest req) {
        return ResponseEntity.ok(loteService.updateGanado(ganadoId, req));
    }

    @DeleteMapping("/ganado/{ganadoId}")
    public ResponseEntity<Void> deleteGanado(@PathVariable Long ganadoId) {
        loteService.deleteGanado(ganadoId);
        return ResponseEntity.noContent().build();
    }
}
