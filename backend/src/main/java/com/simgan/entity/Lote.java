package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lotes")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Fecha de ingreso del lote a la finca */
    @Column(nullable = false)
    private LocalDate fechaIngreso;

    /** Fecha de salida (null hasta que el usuario lo determine) */
    private LocalDate fechaSalida;

    /** Terreno donde opera este lote */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terrain_id", nullable = false)
    private Terrain terrain;

    /** Parcela donde está actualmente el lote (null si no está asignado) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_parcel_id")
    private Parcel currentParcel;

    /** Ganado que pertenece a este lote */
    @OneToMany(mappedBy = "lote", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Ganado> ganados = new ArrayList<>();

    /** Historial de parcelas por las que ha pasado */
    @OneToMany(mappedBy = "lote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fechaIngreso DESC")
    @Builder.Default
    private List<LoteParcelHistory> parcelHistory = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** Número total de cabezas */
    public int getCabezas() {
        return ganados != null ? ganados.size() : 0;
    }
}
