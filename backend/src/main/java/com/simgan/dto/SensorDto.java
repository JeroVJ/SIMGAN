package com.simgan.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SensorDto {
    private Long id;
    private String name;
    private String ubicacionGeoJson;
    private String mqttTopic;
    private String mqttBrokerUrl;
    private String clientId;
    private Boolean connected;
    private Integer pollingIntervalMs;
    private Long parcelId;
}
