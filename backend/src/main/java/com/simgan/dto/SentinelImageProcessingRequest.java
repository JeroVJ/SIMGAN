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
public class SentinelImageProcessingRequest {

    private Long terrainId;
    private String terrainName;
    private String sceneId;
    private LocalDate captureDate;
    private Integer epsg;
    private Double ulx;
    private Double uly;
    private Double pixelSize;
    private Double cloudCoverPercent;
    private List<ParcelProcessingRequest> parcels;
}