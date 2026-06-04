package com.simgan.service;

import com.simgan.dto.TerrainDto;
import com.simgan.entity.Farm;
import com.simgan.entity.Parcel;
import com.simgan.entity.Terrain;
import com.simgan.repository.BiomassCalibrationModelRepository;
import com.simgan.repository.BiomassCalibrationPointRepository;
import com.simgan.repository.FarmRepository;
import com.simgan.repository.LoteRepository;
import com.simgan.repository.NdviCalibrationJobRepository;
import com.simgan.repository.NdviCalibrationRepository;
import com.simgan.repository.NdviRecordRepository;
import com.simgan.repository.NdviTerrainRecordRepository;
import com.simgan.repository.ParcelRepository;
import com.simgan.repository.RotationHistoryRepository;
import com.simgan.repository.TerrainRepository;
import com.simgan.util.GeoJsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TerrainService {

    private final TerrainRepository terrainRepository;
    private final FarmRepository farmRepository;
    private final ParcelRepository parcelRepository;
    private final LoteRepository loteRepository;
    private final NdviRecordRepository ndviRecordRepository;
    private final NdviTerrainRecordRepository ndviTerrainRecordRepository;
    private final NdviCalibrationRepository ndviCalibrationRepository;
    private final NdviCalibrationJobRepository ndviCalibrationJobRepository;
    private final BiomassCalibrationModelRepository biomassCalibrationModelRepository;
    private final BiomassCalibrationPointRepository biomassCalibrationPointRepository;
    private final RotationHistoryRepository rotationHistoryRepository;

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del terreno es obligatorio.");
        }
        return name.trim();
    }

    public TerrainDto.Response create(TerrainDto.CreateRequest request) {
        Farm farm = farmRepository.findById(request.getFarmId())
                .orElseThrow(() -> new RuntimeException("Finca no encontrada con id: " + request.getFarmId()));

        String normalizedName = normalizeName(request.getName());
        if (terrainRepository.existsByFarmIdAndNameIgnoreCase(farm.getId(), normalizedName)) {
            throw new IllegalArgumentException(
                    "Ya existe un terreno con el nombre '" + normalizedName + "' en esta finca.");
        }

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
        return terrainRepository.findByFarmId(farmId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public TerrainDto.Response update(Long id, TerrainDto.UpdateRequest request) {
        Terrain terrain = terrainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado con id: " + id));

        String normalizedName = normalizeName(request.getName());
        if (terrainRepository.existsByFarmIdAndNameIgnoreCaseAndIdNot(terrain.getFarm().getId(), normalizedName, id)) {
            throw new IllegalArgumentException(
                    "Ya existe un terreno con el nombre '" + normalizedName + "' en esta finca.");
        }

        boolean geometryChanged = !Objects.equals(terrain.getGeoJson(), request.getGeoJson());

        terrain.setName(normalizedName);
        terrain.setGeoJson(request.getGeoJson());
        terrain.setAreaSqMeters(request.getAreaSqMeters());
        terrain.setAreaHectares(request.getAreaHectares());

        Terrain saved = terrainRepository.save(terrain);
        TerrainDto.Response response = toResponse(saved);

        if (geometryChanged) {
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

    @Transactional
    public void delete(Long id) {
        Terrain terrain = terrainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado con id: " + id));

        // Borrado en cascada del terreno. Eliminamos primero todo lo que referencia
        // al terreno o a sus potreros y que NO está cubierto por el cascade de JPA,
        // respetando el orden de las claves foráneas.

        // 1. Lotes (incluye su ganado e historial de parcelas por cascade de la entidad).
        //    Deben borrarse antes que los potreros por la FK current_parcel_id.
        loteRepository.deleteByTerrainId(id);

        // 2. NDVI y calibraciones ligadas al terreno o a sus potreros.
        ndviRecordRepository.deleteByTerrainOrParcelTerrainId(id);
        ndviTerrainRecordRepository.deleteByTerrainId(id);   // antes que los jobs (FK job_id)
        ndviCalibrationJobRepository.deleteByTerrainId(id);
        ndviCalibrationRepository.deleteByTerrainId(id);
        biomassCalibrationPointRepository.deleteByTerrainId(id);
        biomassCalibrationModelRepository.deleteByTerrainId(id);

        // 3. Historial de rotación (ligado solo por potrero).
        rotationHistoryRepository.deleteByParcelTerrainId(id);

        try {
            // 4. El terreno: cascade a potreros -> sensores (+clasificaciones) y alertas.
            terrainRepository.delete(terrain);
            terrainRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException(
                    "No se puede eliminar el terreno porque tiene datos históricos asociados.");
        }
    }

    private TerrainDto.Response toResponse(Terrain terrain) {
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
