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
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.*;
import javax.imageio.ImageIO;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.referencing.CRS;
import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.geom.*;


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
                log.error("GeoTIFF necesita mínimo 4 bandas (tiene {})", numBands);
                reader.dispose();
                return Collections.emptyList();
            }

            // Determinar índices Red/NIR según número de bandas
            // 4-band PlanetScope: B1=Blue, B2=Green, B3=Red, B4=NIR
            // 8-band PlanetScope: B1=coastal, B2=blue, B3=greenI, B4=green, B5=yellow, B6=Red, B7=redEdge, B8=NIR
            int redIdx = numBands >= 8 ? 5 : 2;
            int nirIdx = numBands >= 8 ? 7 : 3;
            log.info("Usando bandas: Red=idx{} NIR=idx{} ({}-band mode)", redIdx, nirIdx, numBands >= 8 ? 8 : 4);

            List<NdviRecord> records = new ArrayList<>();

            for (Parcel parcel : parcels) {
                // Skip if already processed for this date
                if (ndviRecordRepository.existsByParcelIdAndCaptureDate(parcel.getId(), captureDate)) {
                    continue;
                }

                try {
                    Geometry parcelGeom = parseGeoJsonGeometry(parcel.getGeoJson());
                    NdviRecord record = calculateNdviForParcel(coverage, raster, parcelGeom, parcel, terrain, captureDate, sceneId, redIdx, nirIdx);
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
            LocalDate captureDate, String sceneId,
            int redIdx, int nirIdx) {

        List<Double> ndviValues = new ArrayList<>();

        try {
            // 1. Obtener el CRS de la imagen (usa la interfaz de org.geotools.api)
            CoordinateReferenceSystem tiffCrs = coverage.getCoordinateReferenceSystem();

            // 2. Definir el CRS de la parcela
            CoordinateReferenceSystem parcelCrs = CRS.decode("EPSG:4326", true);

            // 3. Crear la transformación (esto ahora devolverá org.geotools.api.referencing.operation.MathTransform)
            MathTransform transform = CRS.findMathTransform(parcelCrs, tiffCrs, true);

            // 4. Transformar la geometría
            Geometry projectedParcel = JTS.transform(parcelGeom, transform);

            org.locationtech.jts.geom.Envelope env = projectedParcel.getEnvelopeInternal();
            double step = 3.0;
            GeometryFactory factory = projectedParcel.getFactory();

            for (double x = env.getMinX(); x <= env.getMaxX(); x += step) {
                for (double y = env.getMinY(); y <= env.getMaxY(); y += step) {
                    Point point = factory.createPoint(new Coordinate(x, y));

                    if (projectedParcel.contains(point)) {
                        try {
                            Point2D pos = new Point2D.Double(x, y);
                            double[] values = coverage.evaluate(pos, (double[]) null);

                            if (values != null && values.length > Math.max(redIdx, nirIdx)) {
                                double red = values[redIdx];
                                double nir = values[nirIdx];

                                if (red > 0 || nir > 0) {
                                    double ndvi = (nir - red) / (nir + red);
                                    if (ndvi >= -1.0 && ndvi <= 1.0) {
                                        ndviValues.add(ndvi);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Punto fuera de la cobertura física de la imagen
                        }
                    }
                }
            }

            log.info("Parcela {}: {} pixeles válidos extraídos", parcel.getId(), ndviValues.size());

        } catch (Exception e) {
            log.error("Error proyectando coordenadas para parcela {}: {}", parcel.getId(), e.getMessage());
            return null;
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
    public List<NdviRecord> processSentinelBands(File redBandFile, File nirBandFile,
                                                  int epsg, double ulx, double uly,
                                                  Long terrainId, LocalDate captureDate, String sceneId) {
        try {
            Terrain terrain = terrainRepository.findById(terrainId)
                    .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

            List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
            if (parcels.isEmpty()) {
                log.warn("No hay parcelas en terreno {}", terrainId);
                return Collections.emptyList();
            }

            // 1. Read band images
            log.info("Leyendo bandas Sentinel-2: B04={}, B08={}", redBandFile.getName(), nirBandFile.getName());
            BufferedImage redImage = ImageIO.read(redBandFile);
            BufferedImage nirImage = ImageIO.read(nirBandFile);

            if (redImage == null || nirImage == null) {
                log.error("No se pudieron leer las imágenes JP2. ¿Falta jai-imageio-jpeg2000 en classpath?");
                return Collections.emptyList();
            }

            Raster redRaster = redImage.getRaster();
            Raster nirRaster = nirImage.getRaster();

            int width = redRaster.getWidth();
            int height = redRaster.getHeight();
            double pixelSize = 10.0; // 10m resolution for Sentinel-2 R10m bands

            log.info("Sentinel-2 bandas: {}x{} pixels, EPSG:{}, ULX={}, ULY={}", width, height, epsg, ulx, uly);

            // 2. Setup CRS transformation (WGS84 → UTM of the tile)
            CoordinateReferenceSystem parcelCrs = CRS.decode("EPSG:4326", true);
            CoordinateReferenceSystem tileCrs = CRS.decode("EPSG:" + epsg, true);
            MathTransform toUtm = CRS.findMathTransform(parcelCrs, tileCrs, true);

            List<NdviRecord> records = new ArrayList<>();

            for (Parcel parcel : parcels) {
                if (ndviRecordRepository.existsByParcelIdAndCaptureDate(parcel.getId(), captureDate)) {
                    continue;
                }

                try {
                    Geometry parcelGeom = parseGeoJsonGeometry(parcel.getGeoJson());
                    Geometry utmParcel = JTS.transform(parcelGeom, toUtm);
                    Envelope env = utmParcel.getEnvelopeInternal();
                    GeometryFactory factory = utmParcel.getFactory();

                    List<Double> ndviValues = new ArrayList<>();

                    // 3. Sample pixels inside parcel polygon
                    for (double x = env.getMinX(); x <= env.getMaxX(); x += pixelSize) {
                        for (double y = env.getMinY(); y <= env.getMaxY(); y += pixelSize) {
                            Point point = factory.createPoint(new Coordinate(x, y));
                            if (!utmParcel.contains(point)) continue;

                            // Convert UTM coords to pixel coords
                            int px = (int) ((x - ulx) / pixelSize);
                            int py = (int) ((uly - y) / pixelSize); // Y is inverted

                            if (px < 0 || px >= width || py < 0 || py >= height) continue;

                            try {
                                double red = redRaster.getSampleDouble(px, py, 0);
                                double nir = nirRaster.getSampleDouble(px, py, 0);

                                // Sentinel-2 L2A: values are reflectance * 10000
                                // Skip nodata (0) and saturated (65535)
                                if (red <= 0 || nir <= 0 || red >= 65535 || nir >= 65535) continue;

                                // Normalize to 0-1 reflectance
                                double redRefl = red / 10000.0;
                                double nirRefl = nir / 10000.0;

                                double ndvi = (nirRefl - redRefl) / (nirRefl + redRefl);
                                if (ndvi >= -1.0 && ndvi <= 1.0) {
                                    ndviValues.add(ndvi);
                                }
                            } catch (Exception e) {
                                // Pixel outside raster bounds
                            }
                        }
                    }

                    log.info("Parcela {}: {} pixels NDVI válidos (Sentinel-2)", parcel.getId(), ndviValues.size());

                    if (!ndviValues.isEmpty()) {
                        NdviRecord record = buildNdviRecord(ndviValues, parcel, terrain, captureDate, sceneId, "SENTINEL");
                        records.add(ndviRecordRepository.save(record));
                        checkAndCreateAlerts(record);
                    }
                } catch (Exception e) {
                    log.error("Error procesando parcela {} con Sentinel: {}", parcel.getId(), e.getMessage());
                }
            }

            log.info("Sentinel-2: {} parcelas procesadas para terreno {}", records.size(), terrainId);
            return records;

        } catch (Exception e) {
            log.error("Error procesando bandas Sentinel-2: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
