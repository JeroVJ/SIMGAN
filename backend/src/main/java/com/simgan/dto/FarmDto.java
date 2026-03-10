package com.simgan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

public class FarmDto {

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotBlank(message = "El nombre es obligatorio")
        private String name;

        private Long ganaderoId;

        private String department;
        private String municipality;
        private Double centerLat;
        private Double centerLng;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String name;
        private Long ganaderoId;
        private String department;
        private String municipality;
        private Double centerLat;
        private Double centerLng;
        private String createdAt;
        private int terrainCount;
    }
}
