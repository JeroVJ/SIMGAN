package com.simgan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simgan.dto.ProcessedParcelNdviDto;
import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;


/**
 * Procesa GeoTIFF de PlanetScope 4-band para calcular NDVI por parcela.
 *
 * PlanetScope Surface Reflectance (ortho_analytic_4b_sr):
 *   Band 1 = Blue   (455-515 nm)
 *   Band 2 = Green  (500-590 nm)
 *   Band 3 = Red    (590-670 nm)
 *   Band 4 = NIR    (780-860 nm)
 *
 * NDVI = (NIR - Red) / (NIR + Red) = (Band4 - Band3) / (Band4 + Band3)
 * Rango: -1 a 1
 *   < 0.1  = agua, roca, suelo desnudo
 *   0.1-0.3 = vegetacion dispersa / pasto seco
 *   0.3-0.6 = vegetacion moderada / pasto en crecimiento
 *   0.6-0.8 = vegetacion densa / pasto saludable
 *   > 0.8  = vegetacion muy densa / bosque
 *
 * Modelo de biomasa simplificado para pasturas tropicales:
 *   biomass_kg_ha = max(0, (NDVI - 0.1) * 12000)
 *   Basado en literatura de pasturas tropicales en Colombia
 *   Rango tipico: 500 - 8000 kg MS/ha
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NdviProcessingService {

    private final NdviRecordRepository ndviRecordRepository;
    private final NdviAlertRepository alertRepository;
    private final ParcelRepository parcelRepository;
    private final TerrainRepository terrainRepository;
    private final EmailAlertService emailAlertService;

    @Value("${ndvi.alert.threshold:0.3}")
    private double alertThreshold;

    @Value("${ndvi.optimal.threshold:0.6}")
    private double optimalThreshold;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Construye un NdviRecord a partir de valores NDVI crudos.
     * Usado tanto por el procesamiento real como por el seed.
     */
    public NdviRecord buildNdviRecord(List<Double> ndviValues, Parcel parcel, Terrain terrain,
                                       LocalDate captureDate, String sceneId, String source) {
        Collections.sort(ndviValues);

        double mean = ndviValues.stream().mapToDouble(d -> d).average().orElse(0);
        double min = ndviValues.stream().mapToDouble(d -> d).min().orElse(0);
        double max = ndviValues.stream().mapToDouble(d -> d).max().orElse(0);
        double median = ndviValues.get(ndviValues.size() / 2);
        double std = Math.sqrt(ndviValues.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0));

        // Cobertura vegetal: % de pixeles con NDVI > 0.2
        long vegetatedPixels = ndviValues.stream().filter(v -> v > 0.2).count();
        double vegetationCover = (double) vegetatedPixels / ndviValues.size() * 100;

        // Biomasa estimada (modelo simplificado para pasturas tropicales)
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
            checkAndCreateAlerts(savedRecord);
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

    /**
     * Genera alertas si los valores NDVI están por debajo del umbral
     */
    public void checkAndCreateAlerts(NdviRecord record) {
        if (record.getMeanNdvi() == null || record.getParcel() == null) return;

        double ndvi = record.getMeanNdvi();

        if (ndvi < alertThreshold) {
            NdviAlert.AlertSeverity severity;
            NdviAlert.AlertType type;
            String message;

            if (ndvi < 0.15) {
                severity = NdviAlert.AlertSeverity.CRITICAL;
                type = NdviAlert.AlertType.OVERGRAZING_RISK;
                message = String.format("⚠️ CRÍTICO: NDVI=%.2f en %s. Posible sobrepastoreo. " +
                        "Se recomienda descanso inmediato.", ndvi, record.getParcel().getName());
            } else if (ndvi < 0.25) {
                severity = NdviAlert.AlertSeverity.HIGH;
                type = NdviAlert.AlertType.NDVI_BELOW_THRESHOLD;
                message = String.format("Pasto degradado: NDVI=%.2f en %s. " +
                        "Biomasa estimada: %.0f kg/ha. Considerar descanso.",
                        ndvi, record.getParcel().getName(), record.getBiomassKgPerHa());
            } else {
                severity = NdviAlert.AlertSeverity.MEDIUM;
                type = NdviAlert.AlertType.REST_RECOMMENDED;
                message = String.format("NDVI bajo (%.2f) en %s. " +
                        "Cobertura vegetal: %.0f%%. Programar periodo de descanso.",
                        ndvi, record.getParcel().getName(), record.getVegetationCoverPercent());
            }

            NdviAlert alert = NdviAlert.builder()
                    .parcel(record.getParcel())
                    .alertType(type)
                    .severity(severity)
                    .threshold(alertThreshold)
                    .currentValue(ndvi)
                    .message(message)
                    .build();

            NdviAlert saved = upsertActiveAlert(alert);
            if (saved != null) {
                emailAlertService.sendAlertEmail(saved);
            }
            log.info("Alerta {}: {} para parcela {}", saved != null ? "creada" : "actualizada",
                    type, record.getParcel().getId());
        }

        // Alert when parcel is ready for grazing
        if (ndvi >= optimalThreshold && record.getParcel().getStatus() == Parcel.ParcelStatus.EN_DESCANSO) {
            NdviAlert alert = NdviAlert.builder()
                    .parcel(record.getParcel())
                    .alertType(NdviAlert.AlertType.READY_FOR_GRAZING)
                    .severity(NdviAlert.AlertSeverity.LOW)
                    .threshold(optimalThreshold)
                    .currentValue(ndvi)
                    .message(String.format("✅ %s lista para pastoreo. NDVI=%.2f, Biomasa=%.0f kg/ha.",
                            record.getParcel().getName(), ndvi, record.getBiomassKgPerHa()))
                    .build();
            NdviAlert saved = upsertActiveAlert(alert);
            if (saved != null) {
                emailAlertService.sendAlertEmail(saved);
            }
        }
    }

    /**
     * Creates a new alert or silently updates the existing one.
     * @return the newly persisted alert, or null if an existing alert was updated.
     */
    private NdviAlert upsertActiveAlert(NdviAlert alert) {
        Optional<NdviAlert> existingAlert = alertRepository
            .findFirstByParcelIdAndAlertTypeAndAcknowledgedFalseOrderByCreatedAtDesc(
                alert.getParcel().getId(),
                alert.getAlertType());

        if (existingAlert.isPresent()) {
            NdviAlert persistedAlert = existingAlert.get();
            persistedAlert.setSeverity(alert.getSeverity());
            persistedAlert.setThreshold(alert.getThreshold());
            persistedAlert.setCurrentValue(alert.getCurrentValue());
            persistedAlert.setMessage(alert.getMessage());
            alertRepository.save(persistedAlert);
            return null; // not a new alert — no email
        }

        return alertRepository.save(alert); // new alert — triggers email
    }

    private Geometry parseGeoJsonGeometry(String geoJson) throws Exception {
        JsonNode node = objectMapper.readTree(geoJson);
        JsonNode geometry;

        // --- NUEVA LÓGICA PARA EXTRAER EL POLÍGONO ---
        if (node.has("features") && node.get("features").isArray() && node.get("features").size() > 0) {
            // Viene como FeatureCollection (típico de Leaflet/Mapbox)
            geometry = node.get("features").get(0).get("geometry");
        } else if (node.has("geometry")) {
            // Viene como un Feature único
            geometry = node.get("geometry");
        } else {
            // Viene directo como Polygon o MultiPolygon
            geometry = node;
        }
        // ---------------------------------------------

        String type = geometry.get("type").asText();
        JsonNode coords = geometry.get("coordinates");

        GeometryFactory factory = new GeometryFactory();

        if ("Polygon".equals(type)) {
            // GeoJSON Polygon: [ [ [lng,lat], [lng,lat], ... ] ]
            JsonNode ring = coords.get(0);
            Coordinate[] coordinates = new Coordinate[ring.size()];
            for (int i = 0; i < ring.size(); i++) {
                JsonNode point = ring.get(i);
                coordinates[i] = new Coordinate(point.get(0).asDouble(), point.get(1).asDouble());
            }
            return factory.createPolygon(coordinates);
        } else if ("MultiPolygon".equals(type)) {
            Polygon[] polygons = new Polygon[coords.size()];
            for (int p = 0; p < coords.size(); p++) {
                JsonNode ring = coords.get(p).get(0);
                Coordinate[] coordinates = new Coordinate[ring.size()];
                for (int i = 0; i < ring.size(); i++) {
                    JsonNode point = ring.get(i);
                    coordinates[i] = new Coordinate(point.get(0).asDouble(), point.get(1).asDouble());
                }
                polygons[p] = factory.createPolygon(coordinates);
            }
            return factory.createMultiPolygon(polygons);
        }

        throw new IllegalArgumentException("Tipo de geometría no soportado: " + type);
    }

    // ===== SENTINEL-2: Procesamiento de dos bandas separadas (B04 Red + B08 NIR) =====

    /**
     * Procesa dos bandas Sentinel-2 (JP2 single-band) para calcular NDVI por parcela.
     *
     * Sentinel-2 L2A valores: enteros con scale factor 10000 (val 1000 = reflectance 0.1)
     *
     * @param redBandFile  B04 (Red 665nm) JP2/TIF file
     * @param nirBandFile  B08 (NIR 842nm) JP2/TIF file
     * @param epsg         CRS EPSG code (ej: 32618 para UTM 18N)
     * @param ulx          Upper-Left X in CRS coordinates
     * @param uly          Upper-Left Y in CRS coordinates
     * @param terrainId    ID del terreno
     * @param captureDate  Fecha de captura
     * @param sceneId      ID de la escena Sentinel-2
     */
}
