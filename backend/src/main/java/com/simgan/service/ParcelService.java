package com.simgan.service;

import com.simgan.dto.ParcelDto;
import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
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
    private final SensorRepository sensorRepository;
    private final ClasificacionSensorRepository clasificacionSensorRepository;

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

    // ── Rotation planning ───────────────────────────────────────────────────

    /**
     * Compute rotation-plan metrics for every parcel in a terrain, relative to
     * the given lote.
     *
     * Formulas:
     *   forrajeDisponible = biomassKgPerHa × areaHa × 0.80
     *   consumoIndividual  = sum(peso_i × rate_i) / cabezas         (per animal/day)
     *   cargaAnimal        = forrajeDisponible / consumoIndividual
     *   UGG base weight: Novillo=450 kg (1 UGG), Novilla=360 kg (0.8), Vaca=540 kg (1.2), Toro=810 kg (1.8)
     *   consumoIndividual  = uggWeight × forageRate%
     *   ofertaFV           = consumoIndividual × 1.5
     *   DO (raw)           = biomasaTotal / (numeroAnimales × ofertaFV)
     *   DO (adjusted)      = DO_raw × edaphicFactor
     *   cargaAnimal        = biomasaTotal / (consumoIndividual × DO)
     *   DD                 = (totalParcels − 1) × DO
     */
    public List<ParcelDto.RotationPlanEntry> getRotationPlan(
            Long terrainId, Long loteId, String tipoAnimal, int numeroAnimales) {

        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
        int totalParcels = parcels.size();

        // UGG-based animal constants
        double uggWeight = uggWeightByType(tipoAnimal);           // kg reference weight
        double forageRate = forageRateByTypeName(tipoAnimal);     // % of body weight
        double consumoIndividual = uggWeight * forageRate;        // kg/day per animal
        double ofertaFV = consumoIndividual * 1.5;                // oferta forrajera deseada

        List<ParcelDto.RotationPlanEntry> result = new ArrayList<>();

        for (Parcel parcel : parcels) {
            // NDVI / biomass
            Optional<NdviRecord> latestNdvi =
                    ndviRecordRepository.findFirstByParcelIdOrderByCaptureDateDesc(parcel.getId());
            Double biomassKgPerHa = latestNdvi.map(NdviRecord::getBiomassKgPerHa).orElse(null);

            // Sensor + last classification
            List<Sensor> sensors = sensorRepository.findByParcelId(parcel.getId());
            boolean hasSensor = !sensors.isEmpty();
            String estadoEdafico = null;
            if (hasSensor) {
                Optional<ClasificacionSensor> lastClasif =
                        clasificacionSensorRepository.findFirstBySensorParcelIdOrderByTimestampDesc(parcel.getId());
                estadoEdafico = lastClasif.map(ClasificacionSensor::getEstado).orElse(null);
            }

            // Compute metrics only when we have biomass + area
            Long forrajeDisponible = null;
            Long cargaAnimal = null;
            Double cargaPerHa = null;
            Double diasOcupacion = null;
            Double diasDescanso = null;

            if (biomassKgPerHa != null && parcel.getAreaHectares() != null
                    && parcel.getAreaHectares() > 0 && numeroAnimales > 0) {
                double areaHa = parcel.getAreaHectares();
                double biomasaTotal = biomassKgPerHa * areaHa;

                forrajeDisponible = Math.round(biomasaTotal);

                // 1) DO base (sin ajuste edafico)
                double doRaw = biomasaTotal / ((double) numeroAnimales * ofertaFV);

                // 2) Ajuste edafico sobre DO y recalculo de DD
                String soilType = firstNonBlank(
                    parcel.getSoilType(),
                    parcel.getTerrain() != null && parcel.getTerrain().getFarm() != null
                        ? parcel.getTerrain().getFarm().getSoilType()
                        : null
                );
                String pastureType = firstNonBlank(
                    parcel.getPastureType(),
                    parcel.getTerrain() != null && parcel.getTerrain().getFarm() != null
                        ? parcel.getTerrain().getFarm().getPastureType()
                        : null
                );

                double factor = edaphicFactor(soilType, pastureType, estadoEdafico);
                double doAdjusted = Math.max(0.0, doRaw * factor);
                double ddAdjusted = Math.max(0.0, (totalParcels - 1) * doAdjusted);

                // 3) Carga usando DO ajustado (con factor edafico)
                if (doAdjusted > 0) {
                    double carga = biomasaTotal / (consumoIndividual * doAdjusted);
                    cargaAnimal = Math.round(carga);
                    cargaPerHa = Math.round((carga / areaHa) * 10.0) / 10.0;
                } else {
                    cargaAnimal = 0L;
                    cargaPerHa = 0.0;
                }

                diasOcupacion = Math.round(doAdjusted * 10.0) / 10.0;
                diasDescanso = Math.round(ddAdjusted * 10.0) / 10.0;
            }

            // Persist DO/DD in parcels table so latest rotation values are stored in DB.
            parcel.setDiasOcupacion(diasOcupacion);
            parcel.setDiasDescanso(diasDescanso);

            result.add(ParcelDto.RotationPlanEntry.builder()
                    .parcelId(parcel.getId())
                    .parcelName(parcel.getName())
                    .areaHectares(parcel.getAreaHectares())
                    .parcelStatus(parcel.getStatus() != null ? parcel.getStatus().name() : null)
                    .biomassKgPerHa(biomassKgPerHa)
                    .forrajeDisponible(forrajeDisponible)
                    .estadoEdafico(estadoEdafico)
                    .hasSensor(hasSensor)
                    .cargaAnimal(cargaAnimal)
                    .cargaPerHa(cargaPerHa)
                    .diasOcupacion(diasOcupacion)
                    .diasDescanso(diasDescanso)
                    .rotationOrder(parcel.getRotationOrder())
                    .build());
        }

        parcelRepository.saveAll(parcels);

        return result;
    }

    /** UGG reference weight (450 kg base = 1 UGG). */
    private double uggWeightByType(String tipo) {
        if (tipo == null) return 450.0;
        return switch (tipo.toUpperCase()) {
            case "NOVILLA" -> 360.0;  // 0.8 UGG
            case "VACA"    -> 540.0;  // 1.2 UGG
            case "TORO"    -> 810.0;  // 1.8 UGG
            default        -> 450.0;  // NOVILLO = 1 UGG
        };
    }

    /** Forage consumption rate as % of UGG body weight (green forage). */
    private double forageRateByTypeName(String tipo) {
        if (tipo == null) return 0.13;
        return switch (tipo.toUpperCase()) {
            case "VACA"    -> 0.10;
            case "NOVILLA" -> 0.12;
            case "TORO"    -> 0.11;
            default        -> 0.13;  // NOVILLO
        };
    }

    private boolean isFrancoArcilloso(String soilType) {
        String s = normalizeText(soilType);
        if (s == null) return false;
        return s.contains("franco") && s.contains("arcill");
    }

    private boolean isBrachiariaHumidicola(String pastureType) {
        String p = normalizeText(pastureType);
        if (p == null) return false;
        return p.contains("brachiaria") && p.contains("humid");
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        String withoutAccents = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return withoutAccents.toLowerCase();
    }

    private String firstNonBlank(String primary, String fallback) {
        String p = normalizeText(primary);
        if (p != null) return primary;
        String f = normalizeText(fallback);
        return f != null ? fallback : null;
    }

    private double edaphicFactor(String soilType, String pastureType, String estado) {
        if (!isFrancoArcilloso(soilType) || !isBrachiariaHumidicola(pastureType)) return 1.0;
        if (estado == null) return 1.0;
        return switch (estado.toUpperCase()) {
            case "SECO"        -> 0.70;
            case "ENCHARCADO"  -> 0.55;
            default            -> 1.0;
        };
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
                .diasOcupacion(parcel.getDiasOcupacion())
                .diasDescanso(parcel.getDiasDescanso())
                .rotationOrder(parcel.getRotationOrder())
                .build();
    }
}
