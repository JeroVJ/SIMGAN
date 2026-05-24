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
/**
 * Endpoints REST para Potreros/Parcelas.
 *
 * Funcionalidades:
 * - CRUD de parcelas dentro de un terreno.
 * - Cambio de estado (DISPONIBLE / EN_USO / EN_DESCANSO) con validaciones de negocio.
 * - Cálculo de plan de rotación (DO/DD/carga) para apoyar decisiones en campo.
 * - Trigger manual del scheduler de rotación (útil para pruebas).
 */
public class ParcelController {

    private final ParcelService parcelService;
    private final RotationSchedulerService rotationSchedulerService;

    @PostMapping
    /**
     * Crea una parcela dentro de un terreno.
     *
     * Valores relevantes:
     * - terrainId: id del terreno contenedor.
     * - geoJson: geometría GeoJSON de la parcela (debe estar completamente dentro del terreno).
     * - areaSqMeters/areaHectares: áreas (opcionales).
     */
    public ResponseEntity<ParcelDto.Response> create(@Valid @RequestBody ParcelDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parcelService.create(request));
    }

    @PutMapping("/{id}")
    /**
     * Actualiza datos de una parcela, manteniendo:
     * - unicidad del nombre dentro del terreno
     * - contención espacial (parcela dentro del terreno)
     */
    public ResponseEntity<ParcelDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody ParcelDto.UpdateRequest request) {
        return ResponseEntity.ok(parcelService.update(id, request));
    }

    @GetMapping("/terrain/{terrainId}")
    /**
     * Lista las parcelas de un terreno.
     */
    public ResponseEntity<List<ParcelDto.Response>> findByTerrainId(@PathVariable Long terrainId) {
        return ResponseEntity.ok(parcelService.findByTerrainId(terrainId));
    }

    @GetMapping("/{id}")
    /**
     * Obtiene una parcela por id.
     */
    public ResponseEntity<ParcelDto.Response> findById(@PathVariable Long id) {
        return ResponseEntity.ok(parcelService.findById(id));
    }

    @PatchMapping("/{id}/status")
    /**
     * Cambia el estado de una parcela.
     *
     * Regla: si un lote activo ocupa la parcela, se bloquea el cambio.
     */
    public ResponseEntity<ParcelDto.Response> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ParcelDto.StatusUpdate statusUpdate) {
        return ResponseEntity.ok(parcelService.updateStatus(id, statusUpdate.getStatus()));
    }

    @DeleteMapping("/{id}")
    /**
     * Elimina una parcela por id.
     */
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        parcelService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/parcels/terrain/{terrainId}/rotation-plan?loteId={loteId}
     * Devuelve las métricas de rotación por parcela (DO, DD, carga animal) calculadas en el backend.
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
     * Activa manualmente la comprobación de avance de rotación para todos los lotes activos.
     * Útil para realizar pruebas sin esperar al horario nocturno.
     */
    @PostMapping("/rotation/trigger")
    public ResponseEntity<Map<String, String>> triggerRotation() {
        rotationSchedulerService.triggerNow();
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Rotation check executed"));
    }
}
