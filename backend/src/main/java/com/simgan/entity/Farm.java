package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "farms")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Farm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ganadero_id", nullable = false)
    private Ganadero ganadero;

    private String department;

    private String municipality;

    private Double centerLat;

    private Double centerLng;

    @Column(name = "is_homogeneous")
    @Builder.Default
    private Boolean isHomogeneous = false;

    @Column(name = "soil_type")
    private String soilType;

    @Column(name = "pasture_type")
    private String pastureType;

    @Column(name = "iot_enabled")
    @Builder.Default
    private Boolean iotEnabled = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "farm", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Terrain> terrains = new ArrayList<>();
}
