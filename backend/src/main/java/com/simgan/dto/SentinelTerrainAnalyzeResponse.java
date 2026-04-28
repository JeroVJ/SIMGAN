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
public class SentinelTerrainAnalyzeResponse {

    private Long terrainId;
    private String sceneId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate captureDate;

    private Double cloudCoverPercent;
    private Double meanNdvi;
    private Double minNdvi;
    private Double maxNdvi;
    private Double medianNdvi;
    private Double stdNdvi;
    private Integer pixelCount;
    private Double vegetationCoverPercent;
    private Long processingDurationMs;
    private String warning;
}
