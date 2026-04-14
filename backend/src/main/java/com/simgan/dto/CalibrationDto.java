package com.simgan.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class CalibrationDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CalibrationRequest {
        private Long terrainId;
        private LocalDate calibrationDate;
        private List<ParcelCalibrationEntry> parcelEntries;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ParcelCalibrationEntry {
        private Long parcelId;
        private LocalDate calibrationDate;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CalibrationResponse {
        private Long id;
        private Long terrainId;
        private String terrainName;
        private Long parcelId;
        private String parcelName;
        private LocalDate calibrationDate;
        private String calibrationType;
        private Double referenceNdvi;
        private String pastureType;
        private String source;
        private String sceneId;
        private Double cloudCoverPercent;
        private Integer pixelCount;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CalibrationStatus {
        private Long terrainId;
        private String calibrationType;
        private boolean calibrated;
        private boolean homogeneous;
        private int totalParcels;
        private int calibratedParcels;
        private List<CalibrationResponse> calibrations;
    }
}
