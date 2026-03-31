package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "biomass_calibration_points")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class BiomassCalibrationPoint {

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

    @Column(name = "point_index", nullable = false)
    private Integer pointIndex;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /** Área de corte en m² */
    @Column(name = "cut_area_m2", nullable = false)
    private Double cutAreaM2;

    /** Peso del forraje verde en kg */
    @Column(name = "green_weight_kg", nullable = false)
    private Double greenWeightKg;

    /** Biomasa calculada: (greenWeightKg / cutAreaM2) * 10000 → kg/ha */
    @Column(name = "biomass_kg_per_ha")
    private Double biomassKgPerHa;

    /** NDVI calculado del satélite en este punto */
    @Column(name = "ndvi_at_point")
    private Double ndviAtPoint;

    @Column(name = "scene_id")
    private String sceneId;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
