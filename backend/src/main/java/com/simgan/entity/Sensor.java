package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sensores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String ubicacionGeoJson;

    // MQTT Configuracion
    private String mqttTopic;
    private String mqttBrokerUrl;
    private String clientId;
    private Boolean connected;
    
    @Column(name = "polling_interval_ms")
    private Integer pollingIntervalMs;

    @OneToMany(mappedBy = "sensor", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ClasificacionSensor> clasificaciones = new ArrayList<>();  

    @ManyToOne
    @JoinColumn(name = "parcel_id")
    private Parcel parcel;

}