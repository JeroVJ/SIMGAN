package com.simgan.controller;

import com.simgan.dto.TerrainDto;
import com.simgan.service.TerrainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/terrains")
@RequiredArgsConstructor
/**
 * Endpoints REST para operar con Terrenos.
 *
 * Nota: este controller delega la lógica de negocio a {@link TerrainService}.
 * Aquí se define principalmente el ruteo HTTP y los códigos de respuesta.
 */
public class TerrainController {

    private final TerrainService terrainService;

    @PostMapping
    /**
     * Crea un terreno dentro de una finca.
     *
     * Valores esperados:
     * - farmId: id de la finca dueña del terreno.
     * - name: nombre del terreno (se normaliza con trim).
     * - geoJson: geometría del terreno en GeoJSON.
     * - areaSqMeters/areaHectares: área (opcional) calculada por el cliente.
     */
    public ResponseEntity<TerrainDto.Response> create(@Valid @RequestBody TerrainDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(terrainService.create(request));
    }

    @GetMapping("/farm/{farmId}")
    /**
     * Lista los terrenos de una finca.
     */
    public ResponseEntity<List<TerrainDto.Response>> findByFarmId(@PathVariable Long farmId) {
        return ResponseEntity.ok(terrainService.findByFarmId(farmId));
    }

    @GetMapping("/{id}")
    /**
     * Obtiene un terreno por id.
     */
    public ResponseEntity<TerrainDto.Response> findById(@PathVariable Long id) {
        return ResponseEntity.ok(terrainService.findById(id));
    }

    @DeleteMapping("/{id}")
    /**
     * Elimina un terreno por id, siempre que no tenga dependencias (lotes o historial NDVI).
     */
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        terrainService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
