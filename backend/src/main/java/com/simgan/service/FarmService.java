package com.simgan.service;

import com.simgan.dto.FarmDto;
import com.simgan.entity.Farm;
import com.simgan.repository.FarmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FarmService {

    private final FarmRepository farmRepository;

    public FarmDto.Response create(FarmDto.CreateRequest request) {
        Farm farm = Farm.builder()
                .name(request.getName())
                .owner(request.getOwner())
                .department(request.getDepartment())
                .municipality(request.getMunicipality())
                .centerLat(request.getCenterLat())
                .centerLng(request.getCenterLng())
                .build();

        farm = farmRepository.save(farm);
        return toResponse(farm);
    }

    public List<FarmDto.Response> findAll() {
        return farmRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public FarmDto.Response findById(Long id) {
        Farm farm = farmRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Finca no encontrada con id: " + id));
        return toResponse(farm);
    }

    public void delete(Long id) {
        farmRepository.deleteById(id);
    }

    private FarmDto.Response toResponse(Farm farm) {
        return FarmDto.Response.builder()
                .id(farm.getId())
                .name(farm.getName())
                .owner(farm.getOwner())
                .department(farm.getDepartment())
                .municipality(farm.getMunicipality())
                .centerLat(farm.getCenterLat())
                .centerLng(farm.getCenterLng())
                .createdAt(farm.getCreatedAt() != null ? farm.getCreatedAt().toString() : null)
                .terrainCount(farm.getTerrains() != null ? farm.getTerrains().size() : 0)
                .build();
    }
}
