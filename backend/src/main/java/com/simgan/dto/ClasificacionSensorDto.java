package com.simgan.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClasificacionSensorDto {
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
    
    private Long sensorId;
}
