package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ndvi_calibrations")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NdviCalibration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terrain_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Terrain terrain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Parcel parcel;

    @Column(name = "calibration_date", nullable = false)
    private LocalDate calibrationDate;

    /** OPTIM = referencia óptima, ALERT = umbral de alerta */
    @Column(name = "calibration_type", nullable = false, length = 10)
    @Builder.Default
    private String calibrationType = "OPTIM";

    @Column(name = "reference_ndvi", nullable = false)
    private Double referenceNdvi;

    @Column(name = "reference_biomass")
    private Double referenceBiomass;

    @Column(name = "pasture_type")
    private String pastureType;

    @Column(length = 20)
    @Builder.Default
    private String source = "SENTINEL";

    @Column(name = "scene_id")
    private String sceneId;

    @Column(name = "cloud_cover_percent")
    private Double cloudCoverPercent;

    @Column(name = "pixel_count")
    private Integer pixelCount;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
