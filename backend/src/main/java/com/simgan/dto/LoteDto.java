package com.simgan.dto;

import com.simgan.entity.Ganado;
import com.simgan.entity.Parcel;
import lombok.*;

import java.util.List;

public class LoteDto {

    // ===== LOTE =====

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CreateLoteRequest {
        private String name;
        private String fechaIngreso; // yyyy-MM-dd
        private Long terrainId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LoteResponse {
        private Long id;
        private String name;
        private String fechaIngreso;
        private String fechaSalida;
        private Long terrainId;
        private String terrainName;
        private Long currentParcelId;
        private String currentParcelName;
        private int cabezas;
        private Double pesoPromedioActual;
        private Double gananciaPromedioKg;
        private List<GanadoResponse> ganados;
        private List<ParcelHistoryResponse> parcelHistory;
        private String createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class AssignParcelRequest {
        private Long parcelId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CloseLoteRequest {
        private String fechaSalida; // yyyy-MM-dd
        private List<CloseLoteGanadoRequest> ganados;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CloseLoteGanadoRequest {
        private Long ganadoId;
        private Double pesoActual;
    }

    // ===== ROTATION ASSIGNMENT =====

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class RotationAssignmentRequest {
        /** Entries with DO, DD and order for each parcel in the rotation. */
        private List<RotationAssignmentEntry> entries;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class RotationAssignmentEntry {
        private Long parcelId;
        private Double diasOcupacion;
        private Double diasDescanso;
        /** 1-based position in the rotation sequence. */
        private Integer rotationOrder;
    }

    // ===== GANADO =====

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CreateGanadoRequest {
        private String numeracion;
        private Ganado.TipoGanado tipo;
        private Double pesoInicial;
        private Double pesoActual;
        private Long loteId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class UpdateGanadoRequest {
        private String numeracion;
        private Ganado.TipoGanado tipo;
        private Double pesoActual;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GanadoResponse {
        private Long id;
        private String numeracion;
        private Ganado.TipoGanado tipo;
        private Double pesoInicial;
        private Double pesoActual;
        private Double gananciaPeso;
        private Long loteId;
        private String createdAt;
    }

    // ===== PARCEL HISTORY =====

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ParcelHistoryResponse {
        private Long id;
        private Long parcelId;
        private String parcelName;
        private String fechaIngreso;
        private String fechaSalida;
    }
}
