package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "rotation_history", indexes = {
    @Index(name = "idx_rotation_parcel", columnList = "parcel_id, changed_at")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RotationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id", nullable = false)
    private Parcel parcel;

    @Enumerated(EnumType.STRING)
    private Parcel.ParcelStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Parcel.ParcelStatus newStatus;

    /** NDVI promedio al momento del cambio (null si no hay datos) */
    private Double ndviAtChange;

    /** Biomasa al momento del cambio */
    private Double biomassAtChange;

    /** Nota o razon del cambio */
    private String note;

    @CreationTimestamp
    private LocalDateTime changedAt;
}
