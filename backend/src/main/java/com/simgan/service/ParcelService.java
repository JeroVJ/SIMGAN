package com.simgan.service;

import com.simgan.dto.ParcelDto;
import com.simgan.entity.*;
import com.simgan.repository.*;
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
    private final LoteRepository loteRepository;

    public ParcelDto.Response create(ParcelDto.CreateRequest request) {
        Terrain terrain = terrainRepository.findById(request.getTerrainId())
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado con id: " + request.getTerrainId()));

        Parcel parcel = Parcel.builder()
                .name(request.getName())
                .geoJson(request.getGeoJson())
                .areaSqMeters(request.getAreaSqMeters())
                .areaHectares(request.getAreaHectares())
                .soilType(request.getSoilType())
                .pastureType(request.getPastureType())
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

    public ParcelDto.Response findById(Long id) {
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Parcela no encontrada con id: " + id));
        return toResponse(parcel);
    }

    public ParcelDto.Response updateStatus(Long id, Parcel.ParcelStatus newStatus) {
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Parcela no encontrada con id: " + id));

        // Block status change if parcel is in use by an active lote
        List<Lote> occupyingLotes = loteRepository.findByCurrentParcelId(id);
        List<Lote> activeLotes = occupyingLotes.stream()
                .filter(l -> l.getFechaSalida() == null)
                .collect(Collectors.toList());
        if (!activeLotes.isEmpty()) {
            throw new RuntimeException("No se puede cambiar el estado de la parcela '"
                    + parcel.getName() + "' porque está en uso por el lote '"
                    + activeLotes.get(0).getName() + "'. Primero retire el lote de la parcela.");
        }

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
                .farmId(parcel.getTerrain().getFarm() != null ? parcel.getTerrain().getFarm().getId() : null)
                .farmName(parcel.getTerrain().getFarm() != null ? parcel.getTerrain().getFarm().getName() : null)
                .geoJson(parcel.getGeoJson())
                .areaSqMeters(parcel.getAreaSqMeters())
                .areaHectares(parcel.getAreaHectares())
                .soilType(parcel.getSoilType())
                .pastureType(parcel.getPastureType())
                .status(parcel.getStatus())
                .createdAt(parcel.getCreatedAt() != null ? parcel.getCreatedAt().toString() : null)
                .build();
    }
}
