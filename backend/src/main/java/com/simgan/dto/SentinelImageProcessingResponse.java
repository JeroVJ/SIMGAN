package com.simgan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SentinelImageProcessingResponse {

    private Long terrainId;
    private String terrainName;
    private String sceneId;
    private LocalDate captureDate;
    private String source;
    private Integer epsg;
    private Double ulx;
    private Double uly;
    private Double pixelSize;
    private Double cloudCoverPercent;
    private Integer rasterWidth;
    private Integer rasterHeight;
    private String redBandName;
    private String nirBandName;
    private String redGeoTiffName;
    private String nirGeoTiffName;
    private Integer processedParcelCount;
    private Long processingDurationMs;
    private List<String> warnings;
    private List<ProcessedParcelNdviDto> parcelResults;
}