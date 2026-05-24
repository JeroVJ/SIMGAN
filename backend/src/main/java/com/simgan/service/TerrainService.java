package com.simgan.service;

import com.simgan.dto.TerrainDto;
import com.simgan.entity.Farm;
import com.simgan.entity.Parcel;
import com.simgan.entity.Terrain;
import com.simgan.repository.FarmRepository;
import com.simgan.repository.LoteRepository;
import com.simgan.repository.NdviRecordRepository;
import com.simgan.repository.ParcelRepository;
import com.simgan.repository.TerrainRepository;
import com.simgan.util.GeoJsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
/**
 * Lógica de negocio para Terrenos (Terrain).
 *
 * Funcionalidades principales:
 * - Crear y actualizar un terreno validando que el nombre no esté vacío y sea único por finca.
 * - Consultar terrenos por finca o por id.
 * - Eliminar un terreno sólo si no tiene dependencias (lotes activos/históricos y registros NDVI).
 * - Cuando se actualiza la geometría (GeoJSON), detectar parcelas que quedarían fuera del nuevo polígono.
 *
 * Valores/datos relevantes:
 * - name: nombre "normalizado" (trim) para evitar duplicados por espacios.
 * - geoJson: geometría del terreno en formato GeoJSON (FeatureCollection/Feature/Geometry).
 * - areaSqMeters: área del polígono en m² (Double, puede ser null si no se calculó).
 * - areaHectares: área del polígono en hectáreas (Double, puede ser null si no se calculó).
 */
public class TerrainService {

    private final TerrainRepository terrainRepository;
    private final FarmRepository farmRepository;
    private final ParcelRepository parcelRepository;
    private final LoteRepository loteRepository;
    private final NdviRecordRepository ndviRecordRepository;

    private static String normalizeName(String name) {
        // "Normaliza" el nombre para evitar entradas como "  Lote 1  " y tratarlo como "Lote 1".
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del terreno es obligatorio.");
        }
        return name.trim();
    }

    public TerrainDto.Response create(TerrainDto.CreateRequest request) {
        // 1) Validación de existencia de la finca a la que pertenecerá el terreno.
        Farm farm = farmRepository.findById(request.getFarmId())
                .orElseThrow(() -> new RuntimeException("Finca no encontrada con id: " + request.getFarmId()));

        // 2) Normalización y validación de unicidad del nombre dentro de la misma finca.
        String normalizedName = normalizeName(request.getName());
        if (terrainRepository.existsByFarmIdAndNameIgnoreCase(farm.getId(), normalizedName)) {
            throw new IllegalArgumentException(
                    "Ya existe un terreno con el nombre '" + normalizedName + "' en esta finca.");
        }

        // 3) Construcción de la entidad.
        // geoJson: geometría del terreno.
        // areaSqMeters/areaHectares: valores auxiliares del área (si el cliente los calcula).
        Terrain terrain = Terrain.builder()
                .name(normalizedName)
                .geoJson(request.getGeoJson())
                .areaSqMeters(request.getAreaSqMeters())
                .areaHectares(request.getAreaHectares())
                .farm(farm)
                .build();

        terrain = terrainRepository.save(terrain);
        return toResponse(terrain);
    }

    public List<TerrainDto.Response> findByFarmId(Long farmId) {
        // Lista todos los terrenos que pertenecen a una finca.
        return terrainRepository.findByFarmId(farmId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public TerrainDto.Response update(Long id, TerrainDto.UpdateRequest request) {
        Terrain terrain = terrainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado con id: " + id));

        // La actualización permite cambiar el nombre, pero manteniendo la unicidad por finca.
        String normalizedName = normalizeName(request.getName());
        if (terrainRepository.existsByFarmIdAndNameIgnoreCaseAndIdNot(terrain.getFarm().getId(), normalizedName, id)) {
            throw new IllegalArgumentException(
                    "Ya existe un terreno con el nombre '" + normalizedName + "' en esta finca.");
        }

        // Si cambia la geometría, se debe validar coherencia con parcelas ya creadas en el terreno.
        boolean geometryChanged = !Objects.equals(terrain.getGeoJson(), request.getGeoJson());

        terrain.setName(normalizedName);
        terrain.setGeoJson(request.getGeoJson());
        terrain.setAreaSqMeters(request.getAreaSqMeters());
        terrain.setAreaHectares(request.getAreaHectares());

        Terrain saved = terrainRepository.save(terrain);
        TerrainDto.Response response = toResponse(saved);

        if (geometryChanged) {
            // Se listan las parcelas "fuera de límites": parcelas cuya geometría ya no es cubierta
            // por el nuevo polígono del terreno (covers = contiene o toca el borde).
            List<Parcel> outOfBoundsParcels = parcelRepository.findByTerrainId(saved.getId()).stream()
                    .filter(parcel -> !GeoJsonUtils.covers(saved.getGeoJson(), parcel.getGeoJson()))
                    .collect(Collectors.toList());

            response.setOutOfBoundsParcelIds(outOfBoundsParcels.stream().map(Parcel::getId).toList());
            response.setOutOfBoundsParcelNames(outOfBoundsParcels.stream().map(Parcel::getName).toList());
        }

        return response;
    }

    public TerrainDto.Response findById(Long id) {
        Terrain terrain = terrainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado con id: " + id));
        return toResponse(terrain);
    }

    public void delete(Long id) {
        Terrain terrain = terrainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado con id: " + id));

        // Regla de negocio: no se permite eliminar si hay lotes asociados.
        if (loteRepository.existsByTerrainId(id)) {
            throw new IllegalArgumentException(
                    "No se puede eliminar el terreno porque tiene lotes asociados. Elimina o cierra esos lotes primero.");
        }

        // Regla de negocio: no se permite eliminar si ya existen registros NDVI para el terreno.
        if (ndviRecordRepository.countByTerrainId(id) > 0) {
            throw new IllegalArgumentException(
                    "No se puede eliminar el terreno porque tiene historial NDVI asociado.");
        }

        try {
            terrainRepository.delete(terrain);
            // flush() fuerza a la base de datos a ejecutar el delete en este punto, para capturar
            // violaciones de integridad referencial (FK) en el mismo request.
            terrainRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException(
                    "No se puede eliminar el terreno porque tiene datos históricos asociados.");
        }
    }

    private TerrainDto.Response toResponse(Terrain terrain) {
        // outOfBoundsParcelIds/outOfBoundsParcelNames se inicializan vacíos y sólo se llenan
        // cuando se detecta que cambió la geometría en update().
        return TerrainDto.Response.builder()
                .id(terrain.getId())
                .name(terrain.getName())
                .farmId(terrain.getFarm().getId())
                .farmName(terrain.getFarm().getName())
                .geoJson(terrain.getGeoJson())
                .areaSqMeters(terrain.getAreaSqMeters())
                .areaHectares(terrain.getAreaHectares())
                .createdAt(terrain.getCreatedAt() != null ? terrain.getCreatedAt().toString() : null)
                .parcelCount(terrain.getParcels() != null ? terrain.getParcels().size() : 0)
                .outOfBoundsParcelIds(Collections.emptyList())
                .outOfBoundsParcelNames(Collections.emptyList())
                .build();
    }
}
