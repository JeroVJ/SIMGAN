package com.simgan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedParcelNdviDto {

    private Long parcelId;
    private String parcelName;
    private Double meanNdvi;
    private Double minNdvi;
    private Double maxNdvi;
    private Double stdNdvi;
    private Double medianNdvi;
    private Integer pixelCount;
    private Double biomassKgPerHa;
    private Double vegetationCoverPercent;
    private String warning;
}

