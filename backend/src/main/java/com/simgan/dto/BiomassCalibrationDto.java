package com.simgan.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class BiomassCalibrationDto {

    // ========== REQUEST ==========

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SamplePointInput {
        private Integer pointIndex;
        private Double latitude;
        private Double longitude;
        private Double cutAreaM2;
        private Double greenWeightKg;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CalibrateBiomassRequest {
        private Long terrainId;
        private Long parcelId;
        private List<SamplePointInput> points;
        /** Escena de referencia elegida por el usuario en la línea de tiempo (opcional). */
        private String sceneId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReferenceSceneResponse {
        private String sceneId;
        private LocalDate captureDate;
        private String source;
        private Double meanNdvi;
        private Double cloudCoverPercent;
    }

    // ========== RESPONSE ==========

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SamplePointResponse {
        private Long id;
        private Integer pointIndex;
        private Double latitude;
        private Double longitude;
        private Double cutAreaM2;
        private Double greenWeightKg;
        private Double biomassKgPerHa;
        private Double ndviAtPoint;
        private String sceneId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RegressionModelResponse {
        private Long id;
        private Long parcelId;
        private String parcelName;
        private Double coefficientA;
        private Double coefficientB;
        private Double rSquared;
        private Integer sampleCount;
        private String sceneId;
        private LocalDate calibrationDate;
        private String formula;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ParcelBiomassCalibrationResponse {
        private Long parcelId;
        private String parcelName;
        private String geoJson;
        private boolean calibrated;
        private List<SamplePointResponse> points;
        private RegressionModelResponse model;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BiomassCalibrationStatus {
        private Long terrainId;
        private int totalParcels;
        private int calibratedParcels;
        private boolean allCalibrated;
        private List<ParcelBiomassCalibrationResponse> parcels;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CalibrateBiomassResponse {
        private Long parcelId;
        private String parcelName;
        private List<SamplePointResponse> points;
        private RegressionModelResponse model;
        private String message;
    }
}
