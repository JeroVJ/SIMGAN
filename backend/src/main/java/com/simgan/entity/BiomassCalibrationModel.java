package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "biomass_calibration_models")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class BiomassCalibrationModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Parcel parcel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terrain_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Terrain terrain;

    /** Pendiente: biomasa = a * NDVI + b */
    @Column(name = "coefficient_a", nullable = false)
    private Double coefficientA;

    /** Intercepto: biomasa = a * NDVI + b */
    @Column(name = "coefficient_b", nullable = false)
    private Double coefficientB;

    /** Coeficiente de determinación R² */
    @Column(name = "r_squared")
    private Double rSquared;

    /** Número de muestras usadas */
    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount;

    @Column(name = "scene_id")
    private String sceneId;

    @Column(name = "calibration_date")
    private LocalDate calibrationDate;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
