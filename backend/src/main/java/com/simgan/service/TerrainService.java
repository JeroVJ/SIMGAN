package com.simgan.service;

import com.simgan.dto.TerrainDto;
import com.simgan.entity.Farm;
import com.simgan.entity.Terrain;
import com.simgan.repository.FarmRepository;
import com.simgan.repository.TerrainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TerrainService {

    private final TerrainRepository terrainRepository;
    private final FarmRepository farmRepository;

    public TerrainDto.Response create(TerrainDto.CreateRequest request) {
        Farm farm = farmRepository.findById(request.getFarmId())
                .orElseThrow(() -> new RuntimeException("Finca no encontrada con id: " + request.getFarmId()));

        Terrain terrain = Terrain.builder()
                .name(request.getName())
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

    public TerrainDto.Response findById(Long id) {
        Terrain terrain = terrainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado con id: " + id));
        return toResponse(terrain);
    }

    public void delete(Long id) {
        terrainRepository.deleteById(id);
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
                .build();
    }
}
