package com.simgan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SentinelTerrainAnalyzeRequest {

    private Long terrainId;
    private String terrainName;
    private String terrainGeoJson;
    private String sceneId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate captureDate;

    private String downloadUrl;
    private Double cloudCoverPercent;
}
