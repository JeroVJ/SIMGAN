package com.simgan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.locationtech.jts.geom.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.geom.Point2D;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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

    @Value("${ndvi.alert.threshold:0.3}")
    private double alertThreshold;

    @Value("${ndvi.optimal.threshold:0.6}")
    private double optimalThreshold;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Procesa un GeoTIFF completo y calcula NDVI para todas las parcelas del terreno.
     */
    public List<NdviRecord> processGeoTiff(File geotiffFile, Long terrainId, LocalDate captureDate, String sceneId) {
        try {
            Terrain terrain = terrainRepository.findById(terrainId)
                    .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

            List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
            if (parcels.isEmpty()) {
                log.warn("No hay parcelas en el terreno {}", terrainId);
                return Collections.emptyList();
            }

            // Read GeoTIFF
            GeoTiffReader reader = new GeoTiffReader(geotiffFile);
            GridCoverage2D coverage = reader.read(null);
            RenderedImage image = coverage.getRenderedImage();
            Raster raster = image.getData();

            int width = raster.getWidth();
            int height = raster.getHeight();
            int numBands = raster.getNumBands();

            log.info("GeoTIFF loaded: {}x{}, {} bands", width, height, numBands);

            if (numBands < 4) {
                log.error("GeoTIFF necesita 4 bandas (tiene {})", numBands);
                reader.dispose();
                return Collections.emptyList();
            }

            List<NdviRecord> records = new ArrayList<>();

            for (Parcel parcel : parcels) {
                // Skip if already processed for this date
                if (ndviRecordRepository.existsByParcelIdAndCaptureDate(parcel.getId(), captureDate)) {
                    continue;
                }

                try {
                    Geometry parcelGeom = parseGeoJsonGeometry(parcel.getGeoJson());
                    NdviRecord record = calculateNdviForParcel(coverage, raster, parcelGeom, parcel, terrain, captureDate, sceneId);
                    if (record != null) {
                        records.add(ndviRecordRepository.save(record));
                        checkAndCreateAlerts(record);
                    }
                } catch (Exception e) {
                    log.error("Error procesando parcela {}: {}", parcel.getId(), e.getMessage());
                }
            }

            reader.dispose();
            log.info("Procesadas {} parcelas para terreno {}", records.size(), terrainId);
            return records;

        } catch (Exception e) {
            log.error("Error procesando GeoTIFF: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Calcula NDVI para una parcela individual
     */
    private NdviRecord calculateNdviForParcel(
            GridCoverage2D coverage, Raster raster,
            Geometry parcelGeom, Parcel parcel, Terrain terrain,
            LocalDate captureDate, String sceneId) {

        Envelope env = parcelGeom.getEnvelopeInternal();
        List<Double> ndviValues = new ArrayList<>();

        // Sample points within the parcel geometry
        double resolution = 0.00003; // ~3m en grados (PlanetScope)
        for (double x = env.getMinX(); x <= env.getMaxX(); x += resolution) {
            for (double y = env.getMinY(); y <= env.getMaxY(); y += resolution) {
                Point point = parcelGeom.getFactory().createPoint(new Coordinate(x, y));
                if (parcelGeom.contains(point)) {
                    try {
                        Point2D pos = new Point2D.Double(x, y);
                        double[] values = coverage.evaluate(pos, (double[]) null);

                        if (values != null && values.length >= 4) {
                            double red = values[2];  // Band 3
                            double nir = values[3];  // Band 4

                            if (red + nir > 0) {
                                double ndvi = (nir - red) / (nir + red);
                                ndvi = Math.max(-1.0, Math.min(1.0, ndvi));
                                ndviValues.add(ndvi);
                            }
                        }
                    } catch (Exception e) {
                        // Point outside raster extent, skip
                    }
                }
            }
        }

        if (ndviValues.isEmpty()) {
            log.warn("No se encontraron pixeles NDVI para parcela {}", parcel.getId());
            return null;
        }

        return buildNdviRecord(ndviValues, parcel, terrain, captureDate, sceneId, "PLANET");
    }

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

            alertRepository.save(alert);
            log.info("Alerta creada: {} para parcela {}", type, record.getParcel().getId());
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
            alertRepository.save(alert);
        }
    }

    private Geometry parseGeoJsonGeometry(String geoJson) throws Exception {
        JsonNode node = objectMapper.readTree(geoJson);
        JsonNode geometry = node.has("geometry") ? node.get("geometry") : node;
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
}
