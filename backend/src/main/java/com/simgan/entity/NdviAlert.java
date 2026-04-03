package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ndvi_alerts")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NdviAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id", nullable = false)
    private Parcel parcel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType alertType;

    /** Umbral configurado */
    private Double threshold;

    /** Valor actual que disparo la alerta */
    private Double currentValue;

    /** Mensaje descriptivo */
    @Column(length = 500)
    private String message;

    /** Severidad: LOW, MEDIUM, HIGH, CRITICAL */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AlertSeverity severity = AlertSeverity.MEDIUM;

    @Builder.Default
    private Boolean acknowledged = false;

    private LocalDateTime acknowledgedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum AlertType {
        NDVI_BELOW_THRESHOLD,
        BIOMASS_LOW,
        OVERGRAZING_RISK,
        REST_RECOMMENDED,
        READY_FOR_GRAZING,
        GRAZING_DAYS_LOW,
        PASTURE_DEPLETED
    }

    public enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
