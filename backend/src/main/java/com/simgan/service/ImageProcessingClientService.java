package com.simgan.service;

import com.simgan.dto.ParcelProcessingRequest;
import com.simgan.dto.PlanetImageProcessingRequest;
import com.simgan.dto.PlanetImageProcessingResponse;
import com.simgan.dto.SentinelImageProcessingRequest;
import com.simgan.dto.SentinelImageProcessingResponse;
import com.simgan.dto.SentinelTerrainAnalyzeRequest;
import com.simgan.dto.SentinelTerrainAnalyzeResponse;
import com.simgan.dto.PointNdviDto;
import com.simgan.dto.ProcessedParcelNdviDto;
import com.simgan.entity.NdviRecord;
import com.simgan.entity.Parcel;
import com.simgan.entity.Terrain;
import com.simgan.repository.NdviRecordRepository;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;



// Se comubnica con el microservicio de procesamiento de imágenes (procesamientoImagen) para enviarle las escenas satelitales y las geometrías de las parcelas para que él se encargue de descargar las imágenes, recortarlas por parcela, calcular NDVI y devolver los resultados. Esto desacopla la lógica de procesamiento pesado del backend principal y permite escalar o modificar el procesamiento sin afectar el resto del sistema.
@Service
@RequiredArgsConstructor
public class ImageProcessingClientService {

    private final RestTemplateBuilder restTemplateBuilder;
        private final NdviRecordRepository ndviRecordRepository;

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

    /**
     * Aggregates NDVI over the whole terrain polygon for a single Sentinel scene.
     * Used by 12-month auto-calibration; does not require parcels.
     */
    public SentinelTerrainAnalyzeResponse processTerrainScene(
            Terrain terrain,
            Map<String, Object> scene,
            LocalDate captureDate,
            String sceneId,
            Double cloudCoverPercent) {

        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .setConnectTimeout(Duration.ofSeconds(10))
                    .setReadTimeout(Duration.ofMinutes(10))
                    .build();

            SentinelTerrainAnalyzeRequest payload = SentinelTerrainAnalyzeRequest.builder()
                    .terrainId(terrain.getId())
                    .terrainName(terrain.getName())
                    .terrainGeoJson(terrain.getGeoJson())
                    .sceneId(sceneId)
                    .captureDate(captureDate)
                    .downloadUrl(scene == null ? null : (String) scene.get("downloadUrl"))
                    .cloudCoverPercent(cloudCoverPercent)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<SentinelTerrainAnalyzeResponse> response = restTemplate.postForEntity(
                    imageProcessingBaseUrl + "/ndvi/sentinel/analyze-terrain",
                    new HttpEntity<>(payload, headers),
                    SentinelTerrainAnalyzeResponse.class
            );

            SentinelTerrainAnalyzeResponse responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("procesamientoImagen respondió sin cuerpo en analyze-terrain.");
            }
            return responseBody;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo procesar terrain Sentinel con procesamientoImagen: " + e.getMessage(), e);
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

    public PointNdviDto.PointNdviResponse computePointNdvi(
            String sceneId,
            String downloadUrl,
            List<PointNdviDto.PointNdviInput> points) {

        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .setConnectTimeout(Duration.ofSeconds(10))
                    .setReadTimeout(Duration.ofMinutes(10))
                    .build();

            PointNdviDto.PointNdviRequest payload = PointNdviDto.PointNdviRequest.builder()
                    .sceneId(sceneId)
                    .downloadUrl(downloadUrl)
                    .points(points)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<PointNdviDto.PointNdviResponse> response = restTemplate.postForEntity(
                    imageProcessingBaseUrl + "/ndvi/sentinel/point-ndvi",
                    new HttpEntity<>(payload, headers),
                    PointNdviDto.PointNdviResponse.class
            );

            PointNdviDto.PointNdviResponse responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("procesamientoImagen respondió sin cuerpo para point-ndvi.");
            }
            return responseBody;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo calcular NDVI en puntos: " + e.getMessage(), e);
        }
    }

        public NdviRecord buildNdviRecord(List<Double> ndviValues, Parcel parcel, Terrain terrain,
                                                                          LocalDate captureDate, String sceneId, String source) {
                ndviValues.sort(Double::compareTo);

                double mean = ndviValues.stream().mapToDouble(d -> d).average().orElse(0);
                double min = ndviValues.stream().mapToDouble(d -> d).min().orElse(0);
                double max = ndviValues.stream().mapToDouble(d -> d).max().orElse(0);
                double median = ndviValues.get(ndviValues.size() / 2);
                double std = Math.sqrt(ndviValues.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0));

                long vegetatedPixels = ndviValues.stream().filter(v -> v > 0.2).count();
                double vegetationCover = (double) vegetatedPixels / ndviValues.size() * 100;

                double biomass = Math.max(0, (mean - 0.1) * 12000);

                return NdviRecord.builder()
                                .parcel(parcel)
                                .terrain(terrain)
                                .captureDate(captureDate)
                                .meanNdvi(Math.round(mean * 10000.0) / 10000.0)
                                .minNdvi(Math.round(min * 10000.0) / 10000.0)
                                .maxNdvi(Math.round(max * 10000.0) / 10000.0)
                                .stdNdvi(Math.round(std * 10000.0) / 10000.0)
                                .medianNdvi(Math.round(median * 10000.0) / 10000.0)
                                .pixelCount(ndviValues.size())
                                .biomassKgPerHa(Math.round(biomass * 100.0) / 100.0)
                                .vegetationCoverPercent(Math.round(vegetationCover * 100.0) / 100.0)
                                .planetSceneId(sceneId)
                                .cloudCoverPercent(0.0)
                                .source(source)
                                .build();
        }

        public List<NdviRecord> persistProcessedResults(
                        Terrain terrain,
                        LocalDate captureDate,
                        String sceneId,
                        Double cloudCoverPercent,
                        String source,
                        Collection<ProcessedParcelNdviDto> parcelResults,
                        Map<Long, Parcel> parcelsById) {

                if (parcelResults == null || parcelResults.isEmpty()) {
                        return Collections.emptyList();
                }

                List<NdviRecord> records = new ArrayList<>();

                for (ProcessedParcelNdviDto parcelResult : parcelResults) {
                        if (parcelResult == null || parcelResult.getParcelId() == null) {
                                continue;
                        }

                        Parcel parcel = parcelsById.get(parcelResult.getParcelId());
                        if (parcel == null) {
                                continue;
                        }

                        if (parcelResult.getPixelCount() == null || parcelResult.getPixelCount() <= 0) {
                                continue;
                        }

                        NdviRecord record = ndviRecordRepository.findByParcelIdAndCaptureDate(parcel.getId(), captureDate)
                                        .orElseGet(() -> NdviRecord.builder()
                                                        .parcel(parcel)
                                                        .terrain(terrain)
                                                        .captureDate(captureDate)
                                                        .build());

                        record.setParcel(parcel);
                        record.setTerrain(terrain);
                        record.setCaptureDate(captureDate);
                        record.setMeanNdvi(parcelResult.getMeanNdvi());
                        record.setMinNdvi(parcelResult.getMinNdvi());
                        record.setMaxNdvi(parcelResult.getMaxNdvi());
                        record.setStdNdvi(parcelResult.getStdNdvi());
                        record.setMedianNdvi(parcelResult.getMedianNdvi());
                        record.setPixelCount(parcelResult.getPixelCount());
                        record.setBiomassKgPerHa(parcelResult.getBiomassKgPerHa());
                        record.setVegetationCoverPercent(parcelResult.getVegetationCoverPercent());
                        record.setPlanetSceneId(sceneId);
                        record.setCloudCoverPercent(cloudCoverPercent);
                        record.setSource(source);

                        NdviRecord savedRecord = ndviRecordRepository.save(record);
                        records.add(savedRecord);
                }

                return records;
        }

        public List<NdviRecord> persistSentinelResults(
                        Terrain terrain,
                        LocalDate captureDate,
                        String sceneId,
                        Double cloudCoverPercent,
                        Collection<ProcessedParcelNdviDto> parcelResults,
                        Map<Long, Parcel> parcelsById) {
                return persistProcessedResults(terrain, captureDate, sceneId, cloudCoverPercent, "SENTINEL", parcelResults, parcelsById);
        }

}