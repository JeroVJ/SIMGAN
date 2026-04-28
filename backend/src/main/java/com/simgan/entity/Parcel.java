package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "parcels",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_parcel_terrain_name", columnNames = {"terrain_id", "name"})
    }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Parcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String geoJson;

    private Double areaSqMeters;

    private Double areaHectares;

    @Column(name = "soil_type")
    private String soilType;

    @Column(name = "pasture_type")
    private String pastureType;

    @Column(name = "dias_ocupacion")
    private Double diasOcupacion;

    @Column(name = "dias_descanso")
    private Double diasDescanso;

    @Column(name = "rotation_order")
    private Integer rotationOrder;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ParcelStatus status = ParcelStatus.DISPONIBLE;

    @Column(name = "monitoring_enabled", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    @Builder.Default
    private Boolean monitoringEnabled = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terrain_id", nullable = false)
    private Terrain terrain;

    /**
     * RELACION 1:N
     * Una parcela puede tener muchos sensores
     */
    @OneToMany(mappedBy = "parcel", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Sensor> sensores = new ArrayList<>();

    /**
     * RELACION 1:N
     * Una parcela puede tener muchas alertas
     */
    @OneToMany(mappedBy = "parcel", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Alert> alertas = new ArrayList<>();

    public enum ParcelStatus {
        DISPONIBLE,
        EN_USO,
        EN_DESCANSO
    }
}

