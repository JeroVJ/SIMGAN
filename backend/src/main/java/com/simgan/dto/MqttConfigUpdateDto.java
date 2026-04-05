package com.simgan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO para actualizar configuración MQTT de un sensor
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MqttConfigUpdateDto {
    private String brokerUrl;      // ssl://host:8883
    private String username;        // SIMGAN
    private String password;        // simgan12
    private String topic;           // sensor/5/data
    private String clientId;        // sensor-client-xxx (opcional)
    private Double pollingIntervalHours;
    private Integer pollingIntervalMs;
    private String mqttTopic;
    private String mqttBrokerUrl;
    private Boolean connected;

    /**
     * Clase interna para actualizar solo campos específicos
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private String mqttTopic;
        private String mqttBrokerUrl;
        private String clientId;
        private Double pollingIntervalHours;
        private Integer pollingIntervalMs;
        private Boolean connected;
    }
}
