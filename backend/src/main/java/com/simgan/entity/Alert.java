package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "alertas")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id", nullable = false)
    private Parcel parcel;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType;

  

    @Column(length = 500)
    private String message;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum AlertType {
        ESTADO_FORRAJE_BAJO_O_EN_UMBRAL,
        POTRERO_ENCHARCADO,
        POTRERO_CON_ESTRES_HIDRICO,
        POTRERO_RECUPERADO
    }

   
}
