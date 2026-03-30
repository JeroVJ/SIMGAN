package com.simgan.service;

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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;



// Se comubnica con el microservicio de procesamiento de imágenes (procesamientoImagen) para enviarle las escenas satelitales y las geometrías de las parcelas para que él se encargue de descargar las imágenes, recortarlas por parcela, calcular NDVI y devolver los resultados. Esto desacopla la lógica de procesamiento pesado del backend principal y permite escalar o modificar el procesamiento sin afectar el resto del sistema.
@Service
@RequiredArgsConstructor
public class ImageProcessingClientService {

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${image.processing.base-url}")
    private String imageProcessingBaseUrl;

    public SentinelImageProcessingResponse processSentinelScene(
            Terrain terrain,
            List<Parcel> parcels,
            Map<String, Object> scene,
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
                    .terrainGeoJson(terrain.getGeoJson())
                    .sceneId(sceneId)
                    .captureDate(captureDate)
                    .downloadUrl(scene == null ? null : (String) scene.get("downloadUrl"))
                    .epsg(null)
                    .ulx(null)
                    .uly(null)
                    .pixelSize(10.0)
                    .cloudCoverPercent(cloudCoverPercent)
                    .parcels(parcelRequests)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<SentinelImageProcessingResponse> response = restTemplate.postForEntity(
                    imageProcessingBaseUrl + "/ndvi/sentinel/analyze",
                    new HttpEntity<>(payload, headers),
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
            LocalDate captureDate,
            String sceneId,
            String downloadUrl,
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
                    .terrainGeoJson(terrain.getGeoJson())
                    .sceneId(sceneId)
                    .captureDate(captureDate)
                    .downloadUrl(downloadUrl)
                    .assetType(assetType)
                    .numBands(numBands)
                    .cloudCoverPercent(cloudCoverPercent)
                    .parcels(parcelRequests)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<PlanetImageProcessingResponse> response = restTemplate.postForEntity(
                    imageProcessingBaseUrl + "/ndvi/planet/analyze",
                    new HttpEntity<>(payload, headers),
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