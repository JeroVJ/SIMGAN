package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "parcels")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Parcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String geoJson;

    private Double areaSqMeters;

    private Double areaHectares;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ParcelStatus status = ParcelStatus.DISPONIBLE;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terrain_id", nullable = false)
    private Terrain terrain;

    public enum ParcelStatus {
        DISPONIBLE,
        EN_USO,
        EN_DESCANSO
    }
}
