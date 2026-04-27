package com.simgan.service;

import com.simgan.dto.FarmDto;
import com.simgan.entity.Farm;
import com.simgan.entity.Ganadero;
import com.simgan.repository.FarmRepository;
import com.simgan.repository.GanaderoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FarmService {

    private final FarmRepository farmRepository;
    private final GanaderoRepository ganaderoRepository;

    public FarmDto.Response create(FarmDto.CreateRequest request, String email) {
        String normalizedName = request.getName() != null ? request.getName().trim() : null;

        if (normalizedName == null || normalizedName.isBlank()) {
            throw new IllegalArgumentException("El nombre de la finca es obligatorio");
        }

        Ganadero ganadero = ganaderoRepository.findById(request.getGanaderoId())
                .orElseThrow(() -> new RuntimeException("Ganadero no encontrado con id: " + request.getGanaderoId()));

        if (farmRepository.existsByNormalizedNameAndGanaderoId(normalizedName, ganadero.getId())) {
            throw new IllegalArgumentException("La finca ya existe");
        }

        Farm farm = Farm.builder()
                .name(normalizedName)
                .ganadero(ganadero)
                .department(request.getDepartment())
                .municipality(request.getMunicipality())
                .centerLat(request.getCenterLat())
                .centerLng(request.getCenterLng())
                .isHomogeneous(request.getIsHomogeneous() != null ? request.getIsHomogeneous() : false)
                .soilType(request.getSoilType())
                .pastureType(request.getPastureType())
            .iotEnabled(request.getIotEnabled() != null ? request.getIotEnabled() : true)
                .build();

        farm = farmRepository.save(farm);
        return toResponse(farm);
    }

    public List<FarmDto.Response> findAll(String email) {
        Ganadero ganadero = requireGanadero(email);
        return farmRepository.findByGanaderoId(ganadero.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public FarmDto.Response findById(Long id, String email) {
        Farm farm = requireOwnedFarm(id, email);
        return toResponse(farm);
    }

    public void delete(Long id, String email) {
        requireOwnedFarm(id, email);
        farmRepository.deleteById(id);
    }

    private Ganadero requireGanadero(String email) {
        return ganaderoRepository.findByCorreo(email)
                .orElseThrow(() -> new AccessDeniedException("Usuario no autenticado"));
    }

    private Farm requireOwnedFarm(Long farmId, String email) {
        Ganadero ganadero = requireGanadero(email);
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new RuntimeException("Finca no encontrada con id: " + farmId));
        if (farm.getGanadero() == null || !ganadero.getId().equals(farm.getGanadero().getId())) {
            throw new AccessDeniedException("No tienes acceso a esta finca");
        }
        return farm;
    }

    private FarmDto.Response toResponse(Farm farm) {
        return FarmDto.Response.builder()
                .id(farm.getId())
                .name(farm.getName())
                .ganaderoId(farm.getGanadero() != null ? farm.getGanadero().getId() : null)
                .department(farm.getDepartment())
                .municipality(farm.getMunicipality())
                .centerLat(farm.getCenterLat())
                .centerLng(farm.getCenterLng())
                .isHomogeneous(farm.getIsHomogeneous())
                .soilType(farm.getSoilType())
                .pastureType(farm.getPastureType())
                .iotEnabled(farm.getIotEnabled())
                .createdAt(farm.getCreatedAt() != null ? farm.getCreatedAt().toString() : null)
                .terrainCount(farm.getTerrains() != null ? farm.getTerrains().size() : 0)
                .build();
    }
}
