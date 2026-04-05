package com.simgan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO para enviar configuración MQTT al frontend
 * Respuesta con la configuración guardada del sensor
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MqttConfigResponseDto {
    private Long sensorId;
    private String brokerUrl;
    private String username;
    private String topic;
    private String clientId;
    private Double pollingIntervalHours;
    private Integer pollingIntervalMs;
    private String state;
}
