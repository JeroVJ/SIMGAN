package com.simgan.service;

import com.simgan.dto.NdviDto;
import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Motor de recomendaciones basado en NDVI.
 *
 * Reglas de decision para pasturas tropicales:
 *   NDVI < 0.15 → CRITICO, sobrepastoreo probable → descanso urgente
 *   NDVI 0.15-0.30 → REGULAR, pasto degradado → descanso recomendado
 *   NDVI 0.30-0.50 → BUENO, pasto en recuperacion → puede usarse con precaución
 *   NDVI 0.50-0.70 → MUY BUENO, pasto saludable → apto para pastoreo
 *   NDVI > 0.70 → EXCELENTE, vegetacion densa → pastoreo recomendado
 *
 * Biomasa (kg MS/ha) = max(0, (NDVI - 0.1) * 12000)
 * Capacidad de carga estimada (UGG/ha) = biomasa / 2000
 */
@Service   // Servicio de Motor de recomendaciones basado en NDVI
@Slf4j
@RequiredArgsConstructor
public class NdviRecommendationService {     



    private final NdviRecordRepository ndviRecordRepository;
    private final ParcelRepository parcelRepository;
    private final TerrainRepository terrainRepository;
    private final RotationHistoryRepository rotationHistoryRepository;
    private final NdviCalibrationRepository calibrationRepository;
    private final BiomassCalibrationModelRepository biomassModelRepository;

    @Value("${ndvi.alert.threshold:0.1}")
    private double alertThreshold;

    @Value("${ndvi.optimal.threshold:0.6}")
    private double optimalThreshold;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Genera el dashboard completo de NDVI para un terreno
     */
    public NdviDto.TerrainDashboard getDashboard(Long terrainId) {
        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado"));

        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
        List<NdviDto.ParcelSummary> summaries = parcels.stream()
                .map(this::getParcelSummary)
                .sorted(Comparator.comparingDouble((NdviDto.ParcelSummary s) ->
                        s.getLatestNdvi() != null ? s.getLatestNdvi() : 0).reversed())
                .collect(Collectors.toList());

        // Aggregated stats
        double avgNdvi = summaries.stream()
                .filter(s -> s.getLatestNdvi() != null)
                .mapToDouble(NdviDto.ParcelSummary::getLatestNdvi)
                .average().orElse(0);

        double totalBiomass = summaries.stream()
                .filter(s -> s.getLatestBiomass() != null && s.getAreaHectares() != null)
                .mapToDouble(s -> s.getLatestBiomass() * s.getAreaHectares())
                .sum();

        double totalArea = summaries.stream()
                .filter(s -> s.getAreaHectares() != null)
                .mapToDouble(NdviDto.ParcelSummary::getAreaHectares)
                .sum();

        // Timeline data (all parcels combined)
        List<NdviDto.TimelinePoint> timeline = getTerrainTimeline(terrainId);

        String lastDate = summaries.stream()
                .filter(s -> s.getLatestDate() != null)
                .map(NdviDto.ParcelSummary::getLatestDate)
                .max(String::compareTo)
                .orElse(null);

        return NdviDto.TerrainDashboard.builder()
                .terrainId(terrainId)
                .terrainName(terrain.getName())
                .terrainAreaHa(terrain.getAreaHectares())
                .farmName(terrain.getFarm().getName())
                .avgNdvi(Math.round(avgNdvi * 1000.0) / 1000.0)
                .totalBiomassKg((double) Math.round(totalBiomass))
                .avgBiomassPerHa(totalArea > 0 ? Math.round(totalBiomass / totalArea * 100.0) / 100.0 : 0.0)
                .parcels(summaries)
                .timeline(timeline)
                .lastAnalysisDate(lastDate)
                .analysisScheduleDays(terrain.getAnalysisScheduleDays())
                .nextAnalysisDueDate(terrain.getNextAnalysisDueDate() != null ? terrain.getNextAnalysisDueDate().toString() : null)
                .build();
    }

    /**
     * Resumen NDVI de una parcela individual
     */
    public NdviDto.ParcelSummary getParcelSummary(Parcel parcel) {
        Optional<NdviRecord> latestOpt = ndviRecordRepository.findFirstByParcelIdOrderByCaptureDateDesc(parcel.getId());
        List<NdviRecord> history = ndviRecordRepository.findByParcelIdOrderByCaptureDate(parcel.getId());

        NdviDto.ParcelSummary.ParcelSummaryBuilder builder = NdviDto.ParcelSummary.builder()
                .parcelId(parcel.getId())
                .parcelName(parcel.getName())
                .areaHectares(parcel.getAreaHectares())
                .status(parcel.getStatus())
                .recordCount(history.size());

        double latestBiomass = 0.0;

        if (latestOpt.isPresent()) {
            NdviRecord latest = latestOpt.get();
            double biomass = resolveCurrentBiomassKgPerHa(parcel, latest);
            latestBiomass = biomass;
            builder.latestNdvi(latest.getMeanNdvi())
                    .latestDate(latest.getCaptureDate().format(FMT))
                    .latestBiomass(biomass);
        }

        if (!history.isEmpty()) {
            double avgNdvi = history.stream().mapToDouble(NdviRecord::getMeanNdvi).average().orElse(0);
            double avgMin = history.stream().mapToDouble(NdviRecord::getMinNdvi).average().orElse(0);
            double avgMax = history.stream().mapToDouble(NdviRecord::getMaxNdvi).average().orElse(0);

            builder.avgNdvi(Math.round(avgNdvi * 1000.0) / 1000.0)
                    .avgMinNdvi(Math.round(avgMin * 1000.0) / 1000.0)
                    .avgMaxNdvi(Math.round(avgMax * 1000.0) / 1000.0)
                    .trendSlope(calculateTrend(history));
        }

        // Health classification using calibrated thresholds
        double ndvi = latestOpt.map(NdviRecord::getMeanNdvi).orElse(0.0);
        double[] thresholds = getThresholds(parcel.getId(), parcel.getTerrain().getId());
        String[] health = classifyHealth(ndvi, thresholds[0], thresholds[1], latestBiomass);
        builder.healthStatus(health[0]).healthColor(health[1]);

        // Recommendation
        builder.recommendation(generateRecommendation(parcel, latestOpt.orElse(null)));

        return builder.build();
    }

    /**
     * Genera ranking comparativo de parcelas
     */
    public List<NdviDto.ParcelComparison> getParcelComparison(Long terrainId) {
        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);

        List<NdviDto.ParcelComparison> comparisons = new ArrayList<>();
        for (Parcel p : parcels) {
            NdviDto.ParcelSummary summary = getParcelSummary(p);
            comparisons.add(NdviDto.ParcelComparison.builder()
                    .parcelId(p.getId())
                    .parcelName(p.getName())
                    .areaHectares(p.getAreaHectares())
                    .status(p.getStatus())
                    .latestNdvi(summary.getLatestNdvi())
                    .avgNdvi(summary.getAvgNdvi())
                    .biomassKgPerHa(summary.getLatestBiomass())
                    .vegetationCoverPercent(summary.getLatestNdvi() != null
                            ? Math.min(100, summary.getLatestNdvi() * 130) : null) // approximate
                    .healthStatus(summary.getHealthStatus())
                    .recommendation(summary.getRecommendation())
                    .build());
        }

        // Sort by NDVI descending and assign ranks
        comparisons.sort(Comparator.comparingDouble((NdviDto.ParcelComparison c) ->
                c.getLatestNdvi() != null ? c.getLatestNdvi() : 0).reversed());

        for (int i = 0; i < comparisons.size(); i++) {
            comparisons.get(i).setRank(i + 1);
        }

        return comparisons;
    }

    /**
     * Genera recomendaciones de rotación para todas las parcelas
     */
    public List<NdviDto.RotationRecommendation> getRotationRecommendations(Long terrainId) {
        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
        List<NdviDto.RotationRecommendation> recommendations = new ArrayList<>();

        for (Parcel parcel : parcels) {
            Optional<NdviRecord> latest = ndviRecordRepository.findFirstByParcelIdOrderByCaptureDateDesc(parcel.getId());

            if (latest.isEmpty()) continue;

            double ndvi = latest.get().getMeanNdvi();
            double biomass = resolveCurrentBiomassKgPerHa(parcel, latest.get());

            double[] thresholds = getThresholds(parcel.getId(), terrainId);
            double parcelAlertThreshold = thresholds[1];
            Parcel.ParcelStatus recommended = recommendStatus(ndvi, parcelAlertThreshold, parcel.getStatus());

            if (recommended != parcel.getStatus()) {
                String urgency;
                String reason;

                if (ndvi < 0.15) {
                    urgency = "URGENTE";
                    reason = String.format("NDVI crítico (%.2f). Sobrepastoreo probable. " +
                            "Biomasa: %.0f kg/ha. Descanso inmediato necesario.", ndvi, biomass);
                } else if (ndvi < parcelAlertThreshold) {
                    urgency = "ALTA";
                    reason = String.format("NDVI bajo (%.2f). Pasto degradado. " +
                            "Biomasa insuficiente: %.0f kg/ha.", ndvi, biomass);
                } else if (ndvi >= optimalThreshold && parcel.getStatus() == Parcel.ParcelStatus.EN_DESCANSO) {
                    urgency = "MEDIA";
                    reason = String.format("Pasto recuperado. NDVI=%.2f, Biomasa=%.0f kg/ha. " +
                            "Listo para pastoreo rotacional.", ndvi, biomass);
                } else {
                    urgency = "BAJA";
                    reason = generateRecommendation(parcel, latest.get());
                }

                recommendations.add(NdviDto.RotationRecommendation.builder()
                        .parcelId(parcel.getId())
                        .parcelName(parcel.getName())
                        .currentStatus(parcel.getStatus())
                        .recommendedStatus(recommended)
                        .currentNdvi(ndvi)
                        .biomass(biomass)
                        .reason(reason)
                        .urgency(urgency)
                        .build());
            }
        }

        // Sort by urgency
        Map<String, Integer> urgencyOrder = Map.of("URGENTE", 0, "ALTA", 1, "MEDIA", 2, "BAJA", 3);
        recommendations.sort(Comparator.comparingInt(r -> urgencyOrder.getOrDefault(r.getUrgency(), 4)));

        return recommendations;
    }

    /**
     * Historial de rotación por terreno
     */
    public List<NdviDto.RotationHistoryEntry> getRotationHistory(Long terrainId) {
        return rotationHistoryRepository.findByParcelTerrainIdOrderByChangedAtDesc(terrainId)
                .stream()
                .map(h -> NdviDto.RotationHistoryEntry.builder()
                        .id(h.getId())
                        .parcelId(h.getParcel().getId())
                        .parcelName(h.getParcel().getName())
                        .previousStatus(h.getPreviousStatus() != null ? h.getPreviousStatus().name() : null)
                        .newStatus(h.getNewStatus().name())
                        .ndviAtChange(h.getNdviAtChange())
                        .biomassAtChange(h.getBiomassAtChange())
                        .note(h.getNote())
                        .changedAt(h.getChangedAt() != null ? h.getChangedAt().toString() : null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Timeline de NDVI para un terreno (todas las parcelas)
     */
    public List<NdviDto.TimelinePoint> getTerrainTimeline(Long terrainId) {
        List<NdviRecord> records = ndviRecordRepository.findByTerrainIdOrderByCaptureDate(terrainId);

        return records.stream().map(r -> NdviDto.TimelinePoint.builder()
                .date(r.getCaptureDate().format(FMT))
                .meanNdvi(r.getMeanNdvi())
            .biomassKgPerHa(resolveCurrentBiomassKgPerHa(r.getParcel(), r))
                .vegetationCoverPercent(r.getVegetationCoverPercent())
                .parcelName(r.getParcel() != null ? r.getParcel().getName() : null)
                .parcelId(r.getParcel() != null ? r.getParcel().getId() : null)
                .build()
        ).collect(Collectors.toList());
    }

    // ===== HELPERS =====

    private Parcel.ParcelStatus recommendStatus(double ndvi, double parcelAlertThreshold, Parcel.ParcelStatus current) {
        if (ndvi < 0.20) return Parcel.ParcelStatus.EN_DESCANSO;
        if (ndvi < parcelAlertThreshold && current == Parcel.ParcelStatus.EN_USO) return Parcel.ParcelStatus.EN_DESCANSO;
        if (ndvi >= optimalThreshold && current == Parcel.ParcelStatus.EN_DESCANSO) return Parcel.ParcelStatus.DISPONIBLE;
        return current;
    }

    private String generateRecommendation(Parcel parcel, NdviRecord latest) {
        if (latest == null) return "Sin datos NDVI. Ejecutar análisis satelital.";

        double ndvi = latest.getMeanNdvi();
        double biomass = resolveCurrentBiomassKgPerHa(parcel, latest);

        double[] thresholds = getThresholds(parcel.getId(), parcel.getTerrain().getId());
        double optim = thresholds[0];
        double alert = thresholds[1];

        // Keep interval robust even if thresholds are misconfigured/inverted.
        double upper = Math.max(optim, alert);
        double lower = Math.min(optim, alert);

        if (ndvi > upper) {
            return String.format(
                    " Pasto por encima del óptimo (%.2f > %.2f). El pasto puede estar subpastoreado. Biomasa: %.0f kg/ha.",
                    ndvi, upper, biomass);
        }
        if (ndvi > lower && ndvi <= upper) {
            if (biomass == 0) {
                return String.format(
                        " El pasto está sobrepastoreado y degradado. Biomasa: %.0f kg/ha.",
                        ndvi, lower, biomass);
            }
            return String.format(
                    "Pasto dentro del rango óptimo-alerta (%.2f entre %.2f y %.2f). El pasto está en buen estado. Biomasa: %.0f kg/ha.",
                    ndvi, lower, upper, biomass);
        }
        return String.format(
                " Pasto por debajo del umbral de alerta (%.2f < %.2f). El pasto está sobrepastoreado y degradado. Biomasa: %.0f kg/ha.",
                ndvi, lower, biomass);
    }

    private double resolveCurrentBiomassKgPerHa(Parcel parcel, NdviRecord record) {
        if (record == null) return 0.0;

        Optional<BiomassCalibrationModel> calibModel = biomassModelRepository.findByParcelId(parcel.getId());
        if (calibModel.isPresent() && calibModel.get().getCoefficientA() != null && record.getMeanNdvi() != null) {
            double biomass = Math.max(0.0, calibModel.get().getCoefficientA() * record.getMeanNdvi());
            return Math.round(biomass * 100.0) / 100.0;
        }

        if (record.getBiomassKgPerHa() != null) {
            return record.getBiomassKgPerHa();
        }

        return record.getMeanNdvi() != null ? Math.max(0, (record.getMeanNdvi() - 0.1) * 12000) : 0.0;
    }

    /**
     * Obtiene los umbrales [optim, alert] para una parcela, con fallback a terreno y luego a defaults.
     * Expuesto como public para uso en reportes PDF.
     */
    public double[] getThresholds(Long parcelId, Long terrainId) {
        // 1. Try parcel-level calibrations
        var optimParcel = calibrationRepository.findByParcelIdAndCalibrationType(parcelId, "OPTIM");
        double optim = optimParcel.map(NdviCalibration::getReferenceNdvi).orElse(-1.0);
        double alert = calibrationRepository.findByTerrainIdAndParcelIdIsNullAndCalibrationType(terrainId, "ALERT")
            .map(NdviCalibration::getReferenceNdvi).orElse(alertThreshold);

        // 2. Fallback to terrain-level calibrations
        if (optim < 0) {
            optim = calibrationRepository.findByTerrainIdAndParcelIdIsNullAndCalibrationType(terrainId, "OPTIM")
                    .map(NdviCalibration::getReferenceNdvi).orElse(optimalThreshold);
        }
        alert = Math.max(0.0, alert);
        return new double[]{optim, alert};
    }

    private String[] classifyHealth(double ndvi, double optim, double alert, double biomass) {

    if (ndvi <= alert) {
        return new String[]{"CRÍTICO", "#ef4444"};
    }

    if (ndvi <= optim) {
        return new String[]{"OPTIMO", "#84cc16"};
    }

    return new String[]{"EXCELENTE", "#4ade80"};
   }

    private double calculateTrend(List<NdviRecord> history) {
        if (history.size() < 2) return 0;

        // Simple linear regression on NDVI over time
        int n = history.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += history.get(i).getMeanNdvi();
            sumXY += i * history.get(i).getMeanNdvi();
            sumX2 += i * i;
        }
        double denom = n * sumX2 - sumX * sumX;
        if (denom == 0) return 0;
        return Math.round((n * sumXY - sumX * sumY) / denom * 10000.0) / 10000.0;
    }

}
