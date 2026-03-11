package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "lote_parcel_history")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LoteParcelHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id", nullable = false)
    private Parcel parcel;

    /** Fecha de ingreso a esta parcela */
    @Column(nullable = false)
    private LocalDate fechaIngreso;

    /** Fecha de salida de esta parcela (null si todavía está ahí) */
    private LocalDate fechaSalida;
}
