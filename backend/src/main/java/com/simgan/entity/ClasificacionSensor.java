package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "clasificaciones_sensor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClasificacionSensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double valorHumedad;

    private LocalDateTime timestamp;

    private String estado;

    private String consecuencia;

    // MQTT Configuration
    private String mqttTopic;
    private String mqttBrokerUrl;
    private String clientId;
    private Boolean connected;

    @ManyToOne
    @JoinColumn(name = "sensor_id")
    private Sensor sensor;


    
}