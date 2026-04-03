package com.simgan.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SensorCreateDto {
    private String name;
    private String ubicacionGeoJson;
    
    @JsonProperty("parcelId")
    private Long parcelId;
    
    private String mqttTopic;
    private String mqttBrokerUrl;
    private String clientId;
}
