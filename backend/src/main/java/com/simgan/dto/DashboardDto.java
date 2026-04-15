package com.simgan.dto;

import lombok.*;

import java.util.List;

public class DashboardDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SummaryResponse {
        private Double gananciaTotalEstimada;
        private int totalPotreros;
        private int potrerosDisponibles;
        private int potrerosOcupados;
        private int potrerosEnDescanso;
        private int totalRotacionesUltima24h;
        private int totalAnimales;
        private Double densidadAnimal;
        private int animalesAfectados;
        private int potrerosEnAlerta;
        private List<FarmInfo> farms;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FarmInfo {
        private Long id;
        private String name;
    }
}
