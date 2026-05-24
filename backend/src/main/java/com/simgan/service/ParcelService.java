package com.simgan.service;

import com.simgan.dto.ParcelDto;
import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import com.simgan.util.GeoJsonUtils;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * Lógica de negocio para Parcelas/Potreros.
 *
 * Funcionalidades principales:
 * - Crear/actualizar parcelas validando nombre (único por terreno) y geometría (dentro del terreno).
 * - Consultar parcelas por terreno o por id.
 * - Cambiar estado de parcela registrando historial de rotación (RotationHistory).
 * - Calcular un plan de rotación (DO/DD/carga) usando biomasa NDVI, área y factores edáficos.
 *
 * Valores relevantes:
 * - geoJson: geometría GeoJSON de la parcela; debe estar cubierta por el geoJson del terreno.
 * - areaSqMeters / areaHectares: áreas (opcionales) en m² y ha.
 * - status: DISPONIBLE / EN_USO / EN_DESCANSO.
 * - diasOcupacion (DO) / diasDescanso (DD): métricas de rotación persistidas en la entidad Parcel.
 */
public class ParcelService {

    private final ParcelRepository parcelRepository;
    private final TerrainRepository terrainRepository;
    private final RotationHistoryRepository rotationHistoryRepository;
    private final NdviRecordRepository ndviRecordRepository;
    private final BiomassCalibrationModelRepository biomassModelRepository;
    private final LoteRepository loteRepository;
    private final SensorRepository sensorRepository;
    private final ClasificacionSensorRepository clasificacionSensorRepository;

    private static String normalizeName(String name, String fieldLabel) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldLabel + " es obligatorio.");
        }
        return name.trim();
    }

    public ParcelDto.Response create(ParcelDto.CreateRequest request) {
        // 1) Validación de existencia del terreno contenedor.
        Terrain terrain = terrainRepository.findById(request.getTerrainId())
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado con id: " + request.getTerrainId()));

        // 2) Nombre único por terreno.
        String normalizedName = normalizeName(request.getName(), "El nombre del potrero");
        if (parcelRepository.existsByTerrainIdAndNameIgnoreCase(terrain.getId(), normalizedName)) {
            throw new IllegalArgumentException(
                    "Ya existe un potrero con el nombre '" + normalizedName + "' en este terreno.");
        }

        // 3) Validación espacial: la parcela debe estar totalmente dentro del terreno.
        if (!GeoJsonUtils.covers(terrain.getGeoJson(), request.getGeoJson())) {
            throw new IllegalArgumentException("El potrero debe quedar completamente dentro del terreno.");
        }

        Parcel parcel = Parcel.builder()
                .name(normalizedName)
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

    public ParcelDto.Response update(Long id, ParcelDto.UpdateRequest request) {
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Parcela no encontrada con id: " + id));

        Terrain terrain = parcel.getTerrain();
        String normalizedName = normalizeName(request.getName(), "El nombre del potrero");
        if (parcelRepository.existsByTerrainIdAndNameIgnoreCaseAndIdNot(terrain.getId(), normalizedName, id)) {
            throw new IllegalArgumentException(
                    "Ya existe un potrero con el nombre '" + normalizedName + "' en este terreno.");
        }

        // Se mantiene la regla de contención dentro del terreno al actualizar geometría.
        if (!GeoJsonUtils.covers(terrain.getGeoJson(), request.getGeoJson())) {
            throw new IllegalArgumentException("El potrero debe quedar completamente dentro del terreno.");
        }

        parcel.setName(normalizedName);
        parcel.setGeoJson(request.getGeoJson());
        parcel.setAreaSqMeters(request.getAreaSqMeters());
        parcel.setAreaHectares(request.getAreaHectares());
        parcel.setSoilType(request.getSoilType());
        parcel.setPastureType(request.getPastureType());

        return toResponse(parcelRepository.save(parcel));
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

        // Regla: bloquear el cambio de estado si la parcela está ocupada por un lote activo.
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

        // Registrar historial sólo si el estado cambia realmente.
        if (previousStatus != newStatus) {
            // Captura contexto del último NDVI.
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
    *   DO (raw)           = forrajeDisponible / (numeroAnimales × ofertaFV)
     *   DO (adjusted)      = DO_raw × edaphicFactor
    *   cargaAnimal        = forrajeDisponible / (consumoIndividual × DO)
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

        log.info("[RotationPlan] terrainId={} loteId={} tipoAnimal={} numeroAnimales={} totalParcels={} uggWeight={} forageRate={} consumoIndividual={} ofertaFV={}",
            terrainId, loteId, tipoAnimal, numeroAnimales, totalParcels,
            uggWeight, forageRate, consumoIndividual, ofertaFV);

        List<ParcelDto.RotationPlanEntry> result = new ArrayList<>();

        for (Parcel parcel : parcels) {
            // NDVI/biomasa.
            Optional<NdviRecord> latestNdvi =
                    ndviRecordRepository.findFirstByParcelIdOrderByCaptureDateDesc(parcel.getId());
            Double biomassKgPerHa = resolveCurrentBiomassKgPerHa(parcel, latestNdvi);

            // Sensores y última clasificación.
            List<Sensor> sensors = sensorRepository.findByParcelId(parcel.getId());
            boolean hasSensor = !sensors.isEmpty();
            String estadoEdafico = null;
            if (hasSensor) {
                Optional<ClasificacionSensor> lastClasif =
                        clasificacionSensorRepository.findFirstBySensorParcelIdOrderByTimestampDesc(parcel.getId());
                estadoEdafico = lastClasif.map(ClasificacionSensor::getEstado).orElse(null);
            }

            // Sólo calcula métricas si hay biomasa y área válidas.
            Long forrajeDisponible = null;
            Long cargaAnimal = null;
            Double cargaPerHa = null;
            Double diasOcupacion = null;
            Double diasDescanso = null;

            if (biomassKgPerHa != null && parcel.getAreaHectares() != null
                    && parcel.getAreaHectares() > 0 && numeroAnimales > 0) {
                double areaHa = parcel.getAreaHectares();
                double biomasaTotal = biomassKgPerHa * areaHa;
                double biomasaDisponible = biomasaTotal * 0.80;

                forrajeDisponible = Math.round(biomasaDisponible);

                // 1) DO base (sin ajuste edafico)
                double doRaw = biomasaDisponible / ((double) numeroAnimales * ofertaFV);

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
                double doAdjusted = Math.max(1.0, doRaw * factor);
                double doEffective = Math.max(1.0, Math.round(doAdjusted * 10.0) / 10.0);
                double ddAdjusted = Math.max(1.0, (totalParcels - 1) * doEffective);

                // 3) Carga usando el mismo DO final que se expone, con minimo 1 dia.
                if (doEffective > 0) {
                    double carga = biomasaDisponible / (consumoIndividual * doEffective);
                    cargaAnimal = Math.round(carga);
                    cargaPerHa = Math.round((carga / areaHa) * 10.0) / 10.0;
                } else {
                    cargaAnimal = 0L;
                    cargaPerHa = 0.0;
                }

                diasOcupacion = doEffective;
                diasDescanso = Math.round(ddAdjusted * 10.0) / 10.0;

                log.info("[RotationPlan] parcelId={} parcelName={} areaHa={} biomassKgPerHa={} biomasaTotal={} biomasaDisponible={} soilType={} pastureType={} estadoEdafico={} factor={} doRaw={} doAdjusted={} doEffective={} ddAdjusted={} cargaAnimal={} cargaPerHa={}",
                    parcel.getId(), parcel.getName(), areaHa, biomassKgPerHa,
                    biomasaTotal, biomasaDisponible, soilType, pastureType, estadoEdafico,
                    factor, doRaw, doAdjusted, doEffective, ddAdjusted, cargaAnimal, cargaPerHa);
                } else {
                log.info("[RotationPlan] parcelId={} parcelName={} skipped: biomassKgPerHa={} areaHa={} numeroAnimales={}",
                    parcel.getId(), parcel.getName(), biomassKgPerHa, parcel.getAreaHectares(), numeroAnimales);
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
        // Factor edáfico aplicado sólo a combinaciones específicas (ejemplo de regla de negocio).
        if (!isFrancoArcilloso(soilType) || !isBrachiariaHumidicola(pastureType)) return 1.0;
        if (estado == null) return 1.0;
        return switch (estado.toUpperCase()) {
            case "SECO"        -> 0.70;
            case "ENCHARCADO"  -> 0.55;
            default            -> 1.0;
        };
    }

    private Double resolveCurrentBiomassKgPerHa(Parcel parcel, Optional<NdviRecord> latestNdviOpt) {
        if (latestNdviOpt.isEmpty()) return null;

        NdviRecord latestNdvi = latestNdviOpt.get();
        Optional<BiomassCalibrationModel> calibModel = biomassModelRepository.findByParcelId(parcel.getId());

        if (calibModel.isPresent() && calibModel.get().getCoefficientA() != null && latestNdvi.getMeanNdvi() != null) {
            double biomass = Math.max(0.0, calibModel.get().getCoefficientA() * latestNdvi.getMeanNdvi());
            return Math.round(biomass * 100.0) / 100.0;
        }

        return latestNdvi.getBiomassKgPerHa();
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
