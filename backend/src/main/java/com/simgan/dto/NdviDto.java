package com.simgan.dto;

import com.simgan.entity.NdviAlert;
import com.simgan.entity.Parcel;
import lombok.*;

import java.util.List;

public class NdviDto {

    /** Single NDVI record */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Record {
        private Long id;
        private Long parcelId;
        private String parcelName;
        private Long terrainId;
        private String captureDate;
        private Double meanNdvi;
        private Double minNdvi;
        private Double maxNdvi;
        private Double stdNdvi;
        private Double medianNdvi;
        private Integer pixelCount;
        private Double biomassKgPerHa;
        private Double vegetationCoverPercent;
        private String planetSceneId;
        private Double cloudCoverPercent;
        private String source;
    }

    /** Parcel summary with latest NDVI + stats */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ParcelSummary {
        private Long parcelId;
        private String parcelName;
        private Double areaHectares;
        private Parcel.ParcelStatus status;

        // Latest NDVI
        private Double latestNdvi;
        private String latestDate;
        private Double latestBiomass;

        // Historical stats
        private Double avgNdvi;
        private Double avgMinNdvi;
        private Double avgMaxNdvi;
        private Double trendSlope; // positive = improving, negative = degrading
        private int recordCount;

        // Health classification
        private String healthStatus; // EXCELENTE, BUENO, REGULAR, CRITICO
        private String healthColor;

        // Recommendations
        private String recommendation;
    }

    /** Terrain-level dashboard */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TerrainDashboard {
        private Long terrainId;
        private String terrainName;
        private Double terrainAreaHa;
        private String farmName;

        // Aggregated stats
        private Double avgNdvi;
        private Double totalBiomassKg;
        private Double avgBiomassPerHa;
        private Double vegetationCoverPercent;

        // Parcel summaries
        private List<ParcelSummary> parcels;

        // Alerts
        private int activeAlerts;
        private List<AlertResponse> alerts;

        // Timeline data
        private List<TimelinePoint> timeline;

        // Last analysis date
        private String lastAnalysisDate;
    }

    /** Point on timeline chart */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TimelinePoint {
        private String date;
        private Double meanNdvi;
        private Double biomassKgPerHa;
        private Double vegetationCoverPercent;
        private String parcelName; // for multi-parcel charts
        private Long parcelId;
    }

    /** Parcel comparison row */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ParcelComparison {
        private Long parcelId;
        private String parcelName;
        private Double areaHectares;
        private Parcel.ParcelStatus status;
        private Double latestNdvi;
        private Double avgNdvi;
        private Double biomassKgPerHa;
        private Double vegetationCoverPercent;
        private String healthStatus;
        private String recommendation;
        private int rank;
    }

    /** Rotation recommendation */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RotationRecommendation {
        private Long parcelId;
        private String parcelName;
        private Parcel.ParcelStatus currentStatus;
        private Parcel.ParcelStatus recommendedStatus;
        private String reason;
        private Double currentNdvi;
        private Double biomass;
        private String urgency; // BAJA, MEDIA, ALTA, URGENTE
    }

    /** Alert response */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AlertResponse {
        private Long id;
        private Long parcelId;
        private String parcelName;
        private NdviAlert.AlertType alertType;
        private NdviAlert.AlertSeverity severity;
        private Double threshold;
        private Double currentValue;
        private String message;
        private Boolean acknowledged;
        private String createdAt;
    }

    /** Rotation history entry */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RotationHistoryEntry {
        private Long id;
        private Long parcelId;
        private String parcelName;
        private String previousStatus;
        private String newStatus;
        private Double ndviAtChange;
        private Double biomassAtChange;
        private String note;
        private String changedAt;
    }

    /** Trigger analysis request */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class AnalysisRequest {
        private Long terrainId;
        private String startDate; // yyyy-MM-dd
        private String endDate;
    }
}
