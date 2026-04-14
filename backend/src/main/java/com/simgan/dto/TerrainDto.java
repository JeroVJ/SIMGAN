package com.simgan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

public class TerrainDto {

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        private String name;

        @NotNull(message = "farmId es obligatorio")
        private Long farmId;

        @NotBlank(message = "geoJson es obligatorio")
        private String geoJson;

        private Double areaSqMeters;
        private Double areaHectares;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private String name;

        @NotBlank(message = "geoJson es obligatorio")
        private String geoJson;

        private Double areaSqMeters;
        private Double areaHectares;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String name;
        private Long farmId;
        private String farmName;
        private String geoJson;
        private Double areaSqMeters;
        private Double areaHectares;
        private String createdAt;
        private int parcelCount;
        private List<Long> outOfBoundsParcelIds;
        private List<String> outOfBoundsParcelNames;
    }
}
