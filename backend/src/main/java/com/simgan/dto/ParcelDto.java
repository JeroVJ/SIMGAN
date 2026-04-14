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
    public static class UpdateRequest {
        private String name;

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
        private Double diasOcupacion;
        private Double diasDescanso;
        private Integer rotationOrder;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    public static class StatusUpdate {
        @NotNull
        private Parcel.ParcelStatus status;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class RotationPlanEntry {
        private Long parcelId;
        private String parcelName;
        private Double areaHectares;
        private String parcelStatus;
        /** Latest biomass from NDVI (kg MS/ha), null if no NDVI data */
        private Double biomassKgPerHa;
        /** 80% of total parcel biomass (kg), null if no NDVI data */
        private Long forrajeDisponible;
        /** Soil-state label from last sensor classification, null if no readings */
        private String estadoEdafico;
        /** True when the parcel has at least one associated sensor */
        private boolean hasSensor;
        /** Total animal-load capacity (headcount fitting available forage) */
        private Long cargaAnimal;
        /** cargaAnimal / areaHectares */
        private Double cargaPerHa;
        /** Occupation days, adjusted for edaphic state */
        private Double diasOcupacion;
        /** Rest days = (totalParcels − 1) × diasOcupacion */
        private Double diasDescanso;
        /** Position in the rotation sequence for this parcel (1 = first) */
        private Integer rotationOrder;
    }
}
