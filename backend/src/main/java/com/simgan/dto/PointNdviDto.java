package com.simgan.dto;

import lombok.*;

import java.util.List;

public class PointNdviDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PointNdviInput {
        private Integer pointIndex;
        private Double latitude;
        private Double longitude;
        private Double areaM2;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PointNdviResult {
        private Integer pointIndex;
        private Double latitude;
        private Double longitude;
        private Double ndvi;
        private Integer pixelCount;
        private String warning;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PointNdviRequest {
        private String sceneId;
        private String downloadUrl;
        private List<PointNdviInput> points;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PointNdviResponse {
        private String sceneId;
        private Integer processingDurationMs;
        private List<PointNdviResult> results;
    }
}
