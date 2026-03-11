package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ganados")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Ganado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numeración/identificación del animal (ej: "001", "A-15", ear tag) */
    @Column(nullable = false)
    private String numeracion;

    /** Tipo de animal */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoGanado tipo;

    /** Peso inicial al ingresar al lote (kg) */
    @Column(nullable = false)
    private Double pesoInicial;

    /** Peso actual (kg) - se actualiza manualmente */
    @Column(nullable = false)
    private Double pesoActual;

    /** Lote al que pertenece */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** Ganancia de peso (kg) */
    public Double getGananciaPeso() {
        if (pesoInicial == null || pesoActual == null) return 0.0;
        return pesoActual - pesoInicial;
    }

    public enum TipoGanado {
        NOVILLO,
        NOVILLA,
        TORO,
        VACA
    }
}
