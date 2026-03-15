package com.simgan.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SensorConfigDto {
    private Long sensorId;
    private String mqttTopic;
    private String mqttBrokerUrl;
    private String clientId;
    private Boolean connected;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private String mqttTopic;
        private String mqttBrokerUrl;
        private String clientId;
        private Boolean connected;
    }
}
