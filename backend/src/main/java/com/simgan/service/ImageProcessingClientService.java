package com.simgan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simgan.dto.ParcelProcessingRequest;
import com.simgan.dto.PlanetImageProcessingRequest;
import com.simgan.dto.PlanetImageProcessingResponse;
import com.simgan.dto.SentinelImageProcessingRequest;
import com.simgan.dto.SentinelImageProcessingResponse;
import com.simgan.entity.Parcel;
import com.simgan.entity.Terrain;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageProcessingClientService {

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    @Value("${image.processing.base-url}")
    private String imageProcessingBaseUrl;

    public SentinelImageProcessingResponse processSentinelScene(
            Terrain terrain,
            List<Parcel> parcels,
            File redBandFile,
            File nirBandFile,
            int epsg,
            double ulx,
            double uly,
            LocalDate captureDate,
            String sceneId,
            Double cloudCoverPercent) {

        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .setConnectTimeout(Duration.ofSeconds(10))
                    .setReadTimeout(Duration.ofMinutes(10))
                    .build();

            List<ParcelProcessingRequest> parcelRequests = parcels.stream()
                    .map(parcel -> ParcelProcessingRequest.builder()
                            .parcelId(parcel.getId())
                            .parcelName(parcel.getName())
                            .geoJson(parcel.getGeoJson())
                            .build())
                    .toList();

            SentinelImageProcessingRequest payload = SentinelImageProcessingRequest.builder()
                    .terrainId(terrain.getId())
                    .terrainName(terrain.getName())
                    .sceneId(sceneId)
                    .captureDate(captureDate)
                    .epsg(epsg)
                    .ulx(ulx)
                    .uly(uly)
                    .pixelSize(10.0)
                    .cloudCoverPercent(cloudCoverPercent)
                    .parcels(parcelRequests)
                    .build();

            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("request", new HttpEntity<>(objectMapper.writeValueAsString(payload), jsonHeaders));
            body.add("redBand", new FileSystemResource(redBandFile));
            body.add("nirBand", new FileSystemResource(nirBandFile));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ResponseEntity<SentinelImageProcessingResponse> response = restTemplate.postForEntity(
                    imageProcessingBaseUrl + "/ndvi/sentinel/process",
                    new HttpEntity<>(body, headers),
                    SentinelImageProcessingResponse.class
            );

            SentinelImageProcessingResponse responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("procesamientoImagen respondió sin cuerpo.");
            }

            return responseBody;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo procesar Sentinel con procesamientoImagen: " + e.getMessage(), e);
        }
    }

        public PlanetImageProcessingResponse processPlanetScene(
                        Terrain terrain,
                        List<Parcel> parcels,
                        File geoTiffFile,
                        LocalDate captureDate,
                        String sceneId,
                        String assetType,
                        Integer numBands,
                        Double cloudCoverPercent) {

                try {
                        RestTemplate restTemplate = restTemplateBuilder
                                        .setConnectTimeout(Duration.ofSeconds(10))
                                        .setReadTimeout(Duration.ofMinutes(10))
                                        .build();

                        List<ParcelProcessingRequest> parcelRequests = parcels.stream()
                                        .map(parcel -> ParcelProcessingRequest.builder()
                                                        .parcelId(parcel.getId())
                                                        .parcelName(parcel.getName())
                                                        .geoJson(parcel.getGeoJson())
                                                        .build())
                                        .toList();

                        PlanetImageProcessingRequest payload = PlanetImageProcessingRequest.builder()
                                        .terrainId(terrain.getId())
                                        .terrainName(terrain.getName())
                                        .sceneId(sceneId)
                                        .captureDate(captureDate)
                                        .assetType(assetType)
                                        .numBands(numBands)
                                        .cloudCoverPercent(cloudCoverPercent)
                                        .parcels(parcelRequests)
                                        .build();

                        HttpHeaders jsonHeaders = new HttpHeaders();
                        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

                        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                        body.add("request", new HttpEntity<>(objectMapper.writeValueAsString(payload), jsonHeaders));
                        body.add("image", new FileSystemResource(geoTiffFile));

                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                        ResponseEntity<PlanetImageProcessingResponse> response = restTemplate.postForEntity(
                                        imageProcessingBaseUrl + "/ndvi/planet/process",
                                        new HttpEntity<>(body, headers),
                                        PlanetImageProcessingResponse.class
                        );

                        PlanetImageProcessingResponse responseBody = response.getBody();
                        if (responseBody == null) {
                                throw new RuntimeException("procesamientoImagen respondió sin cuerpo para Planet.");
                        }

                        return responseBody;
                } catch (Exception e) {
                        throw new RuntimeException("No se pudo procesar Planet con procesamientoImagen: " + e.getMessage(), e);
                }
        }
}