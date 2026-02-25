package com.simgan.service;

import com.simgan.dto.ParcelDto;
import com.simgan.entity.NdviRecord;
import com.simgan.entity.Parcel;
import com.simgan.entity.RotationHistory;
import com.simgan.entity.Terrain;
import com.simgan.repository.NdviRecordRepository;
import com.simgan.repository.ParcelRepository;
import com.simgan.repository.RotationHistoryRepository;
import com.simgan.repository.TerrainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParcelService {

    private final ParcelRepository parcelRepository;
    private final TerrainRepository terrainRepository;
    private final RotationHistoryRepository rotationHistoryRepository;
    private final NdviRecordRepository ndviRecordRepository;

    public ParcelDto.Response create(ParcelDto.CreateRequest request) {
        Terrain terrain = terrainRepository.findById(request.getTerrainId())
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado con id: " + request.getTerrainId()));

        Parcel parcel = Parcel.builder()
                .name(request.getName())
                .geoJson(request.getGeoJson())
                .areaSqMeters(request.getAreaSqMeters())
                .areaHectares(request.getAreaHectares())
                .terrain(terrain)
                .build();

        parcel = parcelRepository.save(parcel);
        return toResponse(parcel);
    }

    public List<ParcelDto.Response> findByTerrainId(Long terrainId) {
        return parcelRepository.findByTerrainId(terrainId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ParcelDto.Response updateStatus(Long id, Parcel.ParcelStatus newStatus) {
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Parcela no encontrada con id: " + id));

        Parcel.ParcelStatus previousStatus = parcel.getStatus();

        // Only log if status actually changed
        if (previousStatus != newStatus) {
            // Get latest NDVI for context
            Optional<NdviRecord> latestNdvi = ndviRecordRepository
                    .findFirstByParcelIdOrderByCaptureDateDesc(id);

            RotationHistory history = RotationHistory.builder()
                    .parcel(parcel)
                    .previousStatus(previousStatus)
                    .newStatus(newStatus)
                    .ndviAtChange(latestNdvi.map(NdviRecord::getMeanNdvi).orElse(null))
                    .biomassAtChange(latestNdvi.map(NdviRecord::getBiomassKgPerHa).orElse(null))
                    .build();

            rotationHistoryRepository.save(history);
        }

        parcel.setStatus(newStatus);
        parcel = parcelRepository.save(parcel);
        return toResponse(parcel);
    }

    public void delete(Long id) {
        parcelRepository.deleteById(id);
    }

    private ParcelDto.Response toResponse(Parcel parcel) {
        return ParcelDto.Response.builder()
                .id(parcel.getId())
                .name(parcel.getName())
                .terrainId(parcel.getTerrain().getId())
                .geoJson(parcel.getGeoJson())
                .areaSqMeters(parcel.getAreaSqMeters())
                .areaHectares(parcel.getAreaHectares())
                .status(parcel.getStatus())
                .createdAt(parcel.getCreatedAt() != null ? parcel.getCreatedAt().toString() : null)
                .build();
    }
}
