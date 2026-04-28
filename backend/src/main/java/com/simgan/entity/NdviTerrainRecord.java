package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * NDVI aggregated over an entire terrain polygon (one row per scene).
 * Produced exclusively by 12-month auto-calibration so percentile-derived
 * thresholds don't depend on the terrain having parcels yet.
 */
@Entity
@Table(name = "ndvi_terrain_records", indexes = {
    @Index(name = "idx_ndvi_terrain_record_terrain_date", columnList = "terrain_id, capture_date"),
    @Index(name = "idx_ndvi_terrain_record_job", columnList = "job_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NdviTerrainRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terrain_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Terrain terrain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private NdviCalibrationJob job;

    @Column(nullable = false)
    private LocalDate captureDate;

    private Double meanNdvi;
    private Double minNdvi;
    private Double maxNdvi;
    private Double stdNdvi;
    private Double medianNdvi;
    private Integer pixelCount;
    private Double vegetationCoverPercent;

    @Column(length = 80)
    private String sceneId;

    private Double cloudCoverPercent;

    @Column(length = 20)
    @Builder.Default
    private String source = "SENTINEL";

    @CreationTimestamp
    private LocalDateTime createdAt;
}
