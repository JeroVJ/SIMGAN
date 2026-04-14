package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "terrains",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_terrain_farm_name", columnNames = {"farm_id", "name"})
    }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Terrain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String geoJson;

    private Double areaSqMeters;

    private Double areaHectares;

    @Column(name = "analysis_schedule_days")
    private Integer analysisScheduleDays;

    @Column(name = "next_analysis_due_date")
    private LocalDate nextAnalysisDueDate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    @OneToMany(mappedBy = "terrain", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Parcel> parcels = new ArrayList<>();
}
