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
/**
 * Entidad persistente: Terreno.
 *
 * Un terreno representa el polígono principal de una finca sobre el cual se definen parcelas.
 *
 * Valores:
 * - geoJson: geometría del terreno (guardada como TEXT). Se usa para operaciones espaciales
 *   como validar si una parcela está contenida dentro del terreno.
 * - areaSqMeters / areaHectares: áreas del polígono en m² y hectáreas. Son opcionales porque
 *   pueden calcularse en el frontend o en un proceso externo.
 * - analysisScheduleDays / nextAnalysisDueDate: configuración de programación para análisis NDVI
 *   automático (cada N días) y próxima fecha de ejecución.
 *
 * Restricciones:
 * - (farm_id, name) es único: no puede haber dos terrenos con el mismo nombre en una misma finca.
 */
public class Terrain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String geoJson;

    /**
     * Área en metros cuadrados (m²).
     */
    private Double areaSqMeters;

    /**
     * Área en hectáreas (ha). 1 ha = 10.000 m².
     */
    private Double areaHectares;

    /**
     * Cantidad de días entre análisis NDVI automáticos (null significa "sin programación").
     */
    @Column(name = "analysis_schedule_days")
    private Integer analysisScheduleDays;

    /**
     * Próxima fecha en la que debería ejecutarse el análisis NDVI automático.
     */
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
