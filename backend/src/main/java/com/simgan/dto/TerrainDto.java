package com.simgan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * DTOs (Data Transfer Objects) para las operaciones de Terreno.
 *
 * Objetivo:
 * - Separar el modelo de persistencia (entity Terrain) del contrato HTTP (request/response).
 *
 * Valores:
 * - geoJson: geometría del terreno en formato GeoJSON (texto). Puede venir como FeatureCollection,
 *   Feature o Geometry. El backend la guarda tal cual y la usa para validaciones espaciales.
 * - areaSqMeters: área del polígono en metros cuadrados (m²).
 * - areaHectares: área del polígono en hectáreas (ha). 1 ha = 10.000 m².
 *
 * Nota:
 * - La validación de "name obligatorio" se realiza en el service (normalización/trim).
 */
public class TerrainDto {

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        /**
         * Nombre del terreno (se normaliza con trim en el service).
         */
        private String name;

        @NotNull(message = "farmId es obligatorio")
        /**
         * Identificador de la finca a la que pertenece el terreno.
         */
        private Long farmId;

        @NotBlank(message = "geoJson es obligatorio")
        /**
         * GeoJSON del terreno. Debe contener una geometría válida.
         */
        private String geoJson;

        /**
         * Área del terreno en m² (opcional).
         */
        private Double areaSqMeters;
        /**
         * Área del terreno en hectáreas (opcional).
         */
        private Double areaHectares;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        /**
         * Nombre del terreno (se normaliza con trim en el service).
         */
        private String name;

        @NotBlank(message = "geoJson es obligatorio")
        /**
         * GeoJSON del terreno actualizado.
         */
        private String geoJson;

        /**
         * Área del terreno en m² (opcional).
         */
        private Double areaSqMeters;
        /**
         * Área del terreno en hectáreas (opcional).
         */
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
        /**
         * Cantidad de parcelas registradas dentro del terreno.
         */
        private int parcelCount;
        /**
         * Sólo se llena cuando se actualiza la geometría del terreno y existen parcelas fuera del polígono.
         */
        private List<Long> outOfBoundsParcelIds;
        /**
         * Nombres de las parcelas fuera del polígono (mismo orden que outOfBoundsParcelIds).
         */
        private List<String> outOfBoundsParcelNames;
    }
}
