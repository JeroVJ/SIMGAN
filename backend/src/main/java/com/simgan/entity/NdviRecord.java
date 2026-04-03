package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ndvi_records", indexes = {
    @Index(name = "idx_ndvi_parcel_date", columnList = "parcel_id, capture_date"),
    @Index(name = "idx_ndvi_terrain_date", columnList = "terrain_id, capture_date")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NdviRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id")
    private Parcel parcel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terrain_id")
    private Terrain terrain;

    /** Fecha de captura de la imagen satelital */
    @Column(nullable = false)
    private LocalDate captureDate;

    /** NDVI promedio de la parcela (rango -1 a 1) */
    private Double meanNdvi;

    /** NDVI minimo */
    private Double minNdvi;

    /** NDVI maximo */
    private Double maxNdvi;

    /** Desviacion estandar del NDVI */
    private Double stdNdvi;

    /** Mediana del NDVI */
    private Double medianNdvi;

    /** Cantidad de pixeles procesados */
    private Integer pixelCount;

    /**
     * Biomasa estimada (kg de materia seca / hectarea)
     * Calculada con modelo simplificado: biomass = 10000 * (NDVI - 0.1) * 2.5
     * Rango tipico para pasturas: 500 - 8000 kg MS/ha
     */
    private Double biomassKgPerHa;

    /**
     * Porcentaje de cobertura vegetal (pixeles con NDVI > 0.2)
     */
    private Double vegetationCoverPercent;

    /** ID de la escena de Planet Labs (para referencia) */
    private String planetSceneId;

    /** Porcentaje de nubes en la escena */
    private Double cloudCoverPercent;

    /** Fuente: PLANET, SENTINEL, SEED (datos demo) */
    @Column(length = 20)
    @Builder.Default
    private String source = "PLANET";

    @CreationTimestamp
    private LocalDateTime createdAt;
}
