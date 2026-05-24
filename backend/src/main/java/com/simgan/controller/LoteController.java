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
/**
 * Endpoints REST para Lotes (grupos de animales) y su operación en potreros.
 *
 * Funcionalidades:
 * - CRUD de lotes por terreno.
 * - Asignación/desasignación de parcela actual (potrero en uso).
 * - Guardado de asignación de rotación (orden + DO/DD por parcela).
 * - CRUD de ganado asociado a un lote.
 */
public class LoteController {

    private final LoteService loteService;

    // ===== LOTES =====

    @GetMapping("/terrain/{terrainId}")
    /**
     * Lista lotes (histórico) de un terreno, ordenados por fecha de creación desc.
     */
    public ResponseEntity<List<LoteDto.LoteResponse>> getLotesByTerrain(@PathVariable Long terrainId) {
        return ResponseEntity.ok(loteService.getLotesByTerrain(terrainId));
    }

    @GetMapping("/terrain/{terrainId}/active")
    /**
     * Lista lotes activos de un terreno (sin fecha de salida).
     */
    public ResponseEntity<List<LoteDto.LoteResponse>> getActiveLotes(@PathVariable Long terrainId) {
        return ResponseEntity.ok(loteService.getActiveLotesByTerrain(terrainId));
    }

    @GetMapping("/{loteId}")
    /**
     * Obtiene el detalle de un lote por id (incluye ganado y el historial de parcelas).
     */
    public ResponseEntity<LoteDto.LoteResponse> getLote(@PathVariable Long loteId) {
        return ResponseEntity.ok(loteService.getLote(loteId));
    }

    @PostMapping
    /**
     * Crea un lote dentro de un terreno.
     */
    public ResponseEntity<LoteDto.LoteResponse> createLote(@RequestBody LoteDto.CreateLoteRequest req) {
        return ResponseEntity.ok(loteService.createLote(req));
    }

    @PatchMapping("/{loteId}/close")
    /**
     * Cierra un lote, registrando fecha de salida y actualizando pesos finales si se envían.
     */
    public ResponseEntity<LoteDto.LoteResponse> closeLote(
            @PathVariable Long loteId,
            @RequestBody LoteDto.CloseLoteRequest req) {
        return ResponseEntity.ok(loteService.closeLote(loteId, req));
    }

    @DeleteMapping("/{loteId}")
    /**
     * Elimina un lote.
     */
    public ResponseEntity<Void> deleteLote(@PathVariable Long loteId) {
        loteService.deleteLote(loteId);
        return ResponseEntity.noContent().build();
    }

    // ===== ASIGNACIÓN PARCELA =====

    @PostMapping("/{loteId}/assign-parcel")
    /**
     * Asigna el lote a una parcela (potrero) y marca la parcela en EN_USO.
     */
    public ResponseEntity<LoteDto.LoteResponse> assignParcel(
            @PathVariable Long loteId,
            @RequestBody LoteDto.AssignParcelRequest req) {
        return ResponseEntity.ok(loteService.assignParcel(loteId, req));
    }

    @PostMapping("/{loteId}/unassign-parcel")
    /**
     * Desasigna el lote de su parcela actual y deja la parcela en EN_DESCANSO.
     */
    public ResponseEntity<LoteDto.LoteResponse> unassignParcel(@PathVariable Long loteId) {
        return ResponseEntity.ok(loteService.unassignParcel(loteId));
    }

    @PostMapping("/{loteId}/rotation-assignment")
    /**
     * Guarda la asignación de rotación (DO/DD + orden de rotación por parcela).
     */
    public ResponseEntity<LoteDto.LoteResponse> saveRotationAssignment(
            @PathVariable Long loteId,
            @RequestBody LoteDto.RotationAssignmentRequest req) {
        return ResponseEntity.ok(loteService.saveRotationAssignment(loteId, req));
    }

    // ===== GANADO =====

    @GetMapping("/{loteId}/ganado")
    /**
     * Lista ganado del lote.
     */
    public ResponseEntity<List<LoteDto.GanadoResponse>> getGanado(@PathVariable Long loteId) {
        return ResponseEntity.ok(loteService.getGanadoByLote(loteId));
    }

    @PostMapping("/{loteId}/ganado")
    /**
     * Agrega un animal al lote.
     */
    public ResponseEntity<LoteDto.GanadoResponse> addGanado(
            @PathVariable Long loteId,
            @RequestBody LoteDto.CreateGanadoRequest req) {
        req.setLoteId(loteId);
        return ResponseEntity.ok(loteService.addGanado(req));
    }

    @PostMapping("/{loteId}/ganado/batch")
    /**
     * Agrega varios animales al lote en una sola petición.
     */
    public ResponseEntity<List<LoteDto.GanadoResponse>> addGanadoBatch(
            @PathVariable Long loteId,
            @RequestBody List<LoteDto.CreateGanadoRequest> requests) {
        return ResponseEntity.ok(loteService.addGanadoBatch(loteId, requests));
    }

    @PutMapping("/ganado/{ganadoId}")
    /**
     * Actualiza datos de un animal del lote (numeración, tipo, peso actual).
     */
    public ResponseEntity<LoteDto.GanadoResponse> updateGanado(
            @PathVariable Long ganadoId,
            @RequestBody LoteDto.UpdateGanadoRequest req) {
        return ResponseEntity.ok(loteService.updateGanado(ganadoId, req));
    }

    @DeleteMapping("/ganado/{ganadoId}")
    /**
     * Elimina un animal.
     */
    public ResponseEntity<Void> deleteGanado(@PathVariable Long ganadoId) {
        loteService.deleteGanado(ganadoId);
        return ResponseEntity.noContent().build();
    }
}
