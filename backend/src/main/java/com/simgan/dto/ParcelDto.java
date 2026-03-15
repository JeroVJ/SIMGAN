package com.simgan.dto;

import com.simgan.entity.Parcel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

public class ParcelDto {

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        private String name;

        @NotNull(message = "terrainId es obligatorio")
        private Long terrainId;

        @NotBlank(message = "geoJson es obligatorio")
        private String geoJson;

        private Double areaSqMeters;
        private Double areaHectares;
        private String soilType;
        private String pastureType;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String name;
        private Long terrainId;
        private Long farmId;
        private String farmName;
        private String geoJson;
        private Double areaSqMeters;
        private Double areaHectares;
        private String soilType;
        private String pastureType;
        private Parcel.ParcelStatus status;
        private String createdAt;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    public static class StatusUpdate {
        @NotNull
        private Parcel.ParcelStatus status;
    }
}
