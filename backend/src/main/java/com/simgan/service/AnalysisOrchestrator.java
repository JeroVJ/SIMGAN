package com.simgan.service;

import com.simgan.dto.PlanetImageProcessingResponse;
import com.simgan.dto.SentinelImageProcessingResponse;
import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

/**
 * Orquestador del pipeline de análisis NDVI.
 *
 * Orden de prioridad:
 *   1. Planet Labs (si API key configurada)
 *   2. Sentinel-2 / Copernicus (gratuito, búsqueda STAC)
 *   3. Seed data (datos demo realistas)
 *
 * Pipeline completo:
 *   1. Obtener GeoJSON del terreno
 *   2. Buscar escenas disponibles (Planet o Sentinel)
 *   3. Seleccionar mejor escena (menor nubosidad, más reciente)
 *   4. Activar/descargar assets
 *   5. Procesar GeoTIFF → calcular NDVI por parcela
 *   6. Guardar registros + generar alertas
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisOrchestrator {

    private final PlanetApiService planetApi;
    private final SentinelApiService sentinelApi;
    private final ImageProcessingClientService imageProcessingClientService;
    private final NdviProcessingService processingService;
    //private final NdviSeedService seedService;
    private final TerrainRepository terrainRepository;
    private final ParcelRepository parcelRepository;
    private final NdviRecordRepository ndviRecordRepository;
    private final LoteRepository loteRepository;
    private final GanadoRepository ganadoRepository;
    private final NdviAlertRepository ndviAlertRepository;

    /**
     * Ejecuta el análisis completo para un terreno.
     * Intenta Planet → Sentinel → Seed data como fallback.
     */ 

    /* 
    public Map<String, Object> runAnalysis(Long terrainId) {
        Map<String, Object> result = new LinkedHashMap<>();

        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
        if (parcels.isEmpty()) {
            result.put("error", "No hay parcelas definidas en este terreno. Crea parcelas primero.");
            return result;
        }

        result.put("terrainId", terrainId);
        result.put("terrainName", terrain.getName());
        result.put("parcelCount", parcels.size());

        String geoJson = terrain.getGeoJson();
        LocalDate endDate = LocalDate.now().minusDays(35);
        LocalDate startDate = endDate.minusDays(180);

        // ===== 1. Try Planet Labs =====
        if (planetApi.isConfigured()) {
            log.info("Intentando análisis con Planet Labs para terreno {}", terrainId);
            result.put("source", "PLANET");

            try {

                // Buscamos escenas con un umbral de nubes aceptable (ej: 20%)
                List<Map<String, Object>> scenes = planetApi.searchScenes(terrain.getGeoJson(), startDate, endDate, 0.2);

                if (!scenes.isEmpty()) {
                    result.put("scenesFound", scenes.size());
                    log.info("Planet: {} escenas encontradas en los últimos 15 días", scenes.size());

                    // Ordenamos las escenas por fecha de adquisición (la más reciente primero)
                    scenes.sort((a, b) -> ((String) b.get("acquired")).compareTo((String) a.get("acquired")));

                    boolean processedSuccess = false;

                    // Lógica de reintento: Intentamos con las 5 más recientes
                    for (int i = 0; i < Math.min(scenes.size(), 5); i++) {
                        Map<String, Object> currentScene = scenes.get(i);
                        String sceneId = (String) currentScene.get("id");
                        Double cloudCover = (Double) currentScene.get("cloud_cover");
                        String acquiredStr = (String) currentScene.get("acquired");

                        log.info("Planet: Evaluando escena {}/{} -> ID: {} (Fecha: {}, Nubes: {}%)",
                                (i + 1), Math.min(scenes.size(), 5), sceneId, acquiredStr, cloudCover);

                        // activateAndGetDownloadUrl ahora retorna Map con url, assetType, numBands
                        Map<String, Object> assetInfo = planetApi.activateAndGetDownloadUrl(sceneId);

                        if (assetInfo != null) {
                            String downloadUrl = (String) assetInfo.get("url");
                            String usedAssetType = (String) assetInfo.get("assetType");
                            int numBands = (int) assetInfo.get("numBands");

                            File geotiff = planetApi.downloadGeoTiff(downloadUrl, sceneId);

                            if (geotiff != null && geotiff.exists()) {
                                LocalDate captureDate = LocalDate.parse(acquiredStr.substring(0, 10));

                                // Procesar GeoTIFF — pasar info de bandas
                                List<NdviRecord> records = processingService.processGeoTiff(
                                        geotiff, terrainId, captureDate, sceneId);

                                if (records != null && !records.isEmpty()) {
                                    result.put("selectedScene", sceneId);
                                    result.put("assetType", usedAssetType);
                                    result.put("numBands", numBands);
                                    result.put("cloudCover", cloudCover);
                                    result.put("recordsProcessed", records.size());
                                    result.put("message", String.format(
                                            "✅ Análisis completado. %d parcelas analizadas. Escena: %s (%s, %d bandas)",
                                            records.size(), captureDate, usedAssetType, numBands));

                                    geotiff.delete();
                                    processedSuccess = true;
                                    break;
                                }
                                geotiff.delete();
                            }
                        } else {
                            log.warn("Planet: Ningún asset analítico disponible para escena {}.", sceneId);
                        }
                    }

                    if (processedSuccess) {
                        return result;
                    } else {
                        result.put("planetNote", "Escenas recientes encontradas pero sus assets aún están en preparación.");
                    }

                } else {
                    log.info("Planet: no hay fotos recientes (últimos 15 días)");
                    result.put("planetNote", "Sin imágenes recientes disponibles. Intentando Sentinel-2...");
                }
            } catch (Exception e) {
                log.error("Error en pipeline Planet: {}", e.getMessage(), e);
                result.put("planetError", e.getMessage());
                result.put("planetNote", "Error técnico con Planet Labs. Intentando Sentinel-2...");
            }
        }

        // ===== 2. Try Sentinel-2 =====
        log.info("Intentando análisis con Sentinel-2 para terreno {}", terrainId);
        try {
            List<Map<String, Object>> scenes = sentinelApi.searchScenes(geoJson, startDate, endDate, 0.6);

            if (!scenes.isEmpty()) {
                result.put("source", result.containsKey("source") ? "PLANET+SENTINEL" : "SENTINEL");
                result.put("sentinelScenesFound", scenes.size());
                log.info("Sentinel: {} escenas encontradas. Intentando descarga...", scenes.size());

                // SIEMPRE intentar descargar - isConfigured() logea el error si falta auth
                for (int si = 0; si < Math.min(scenes.size(), 3); si++) {
                    Map<String, Object> bestScene = scenes.get(si);
                    String sentSceneId = (String) bestScene.get("id");
                    log.info("Sentinel: Procesando escena {}/{}: {} (nubes: {}%)",
                            si + 1, Math.min(scenes.size(), 3), sentSceneId, bestScene.get("cloud_cover"));

                    try {
                        Map<String, Object> bandData = sentinelApi.downloadAndExtractBands(bestScene);

                        if (bandData != null) {
                            File redFile = (File) bandData.get("redFile");
                            File nirFile = (File) bandData.get("nirFile");
                            int sentEpsg = (int) bandData.getOrDefault("epsg", 32618);
                            double sentUlx = (double) bandData.getOrDefault("ulx", 600000.0);
                            double sentUly = (double) bandData.getOrDefault("uly", 500000.0);

                            String dtStr = (String) bestScene.getOrDefault("datetime", "");
                            LocalDate sentCaptureDate = !dtStr.isEmpty()
                                    ? LocalDate.parse(dtStr.substring(0, 10)) : endDate;

                            List<NdviRecord> sentRecords = processingService.processSentinelBands(
                                    redFile, nirFile, sentEpsg, sentUlx, sentUly,
                                    terrainId, sentCaptureDate, sentSceneId);

                            if (sentRecords != null && !sentRecords.isEmpty()) {
                                result.put("selectedScene", sentSceneId);
                                result.put("sentinelCloudCover", bestScene.get("cloud_cover"));
                                result.put("recordsProcessed", sentRecords.size());
                                result.put("message", String.format(
                                        "✅ Análisis Sentinel-2 completado. %d parcelas procesadas (EPSG:%d). Escena: %s",
                                        sentRecords.size(), sentEpsg, sentCaptureDate));
                                return result;
                            } else {
                                log.warn("Sentinel: bandas descargadas pero 0 NDVI records generados para escena {}", sentSceneId);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error procesando Sentinel escena {}: {}", sentSceneId, e.getMessage());
                        result.put("sentinelError", e.getMessage());
                    }
                }
                result.put("sentinelNote", "Escenas encontradas pero la descarga/procesamiento falló. Ver logs del backend.");
            } else {
                log.info("Sentinel: sin escenas en rango");
                result.put("sentinelNote", "Sin escenas Sentinel-2 en los últimos 90 días.");
            }
        } catch (Exception e) {
            log.warn("Error buscando Sentinel-2: {}", e.getMessage());
            result.put("sentinelError", e.getMessage());
        }

        // ===== 3. Fallback: Seed Data =====
        boolean hasExistingData = ndviRecordRepository.countByTerrainId(terrainId) > 0;

        if (!hasExistingData) {
            log.info("Generando seed data para terreno {}", terrainId);
            int seedCount = seedService.seedTerrainData(terrainId);
            result.put("source", result.getOrDefault("source", "") + "+SEED");
            result.put("seedRecords", seedCount);
            result.put("message", String.format(
                    "Se generaron %d registros NDVI demo (6 meses de histórico simulado). " +
                    "Configure API keys para usar imágenes satelitales reales.", seedCount));
        } else if (!result.containsKey("recordsProcessed")) {
            // Has existing data but no new records from Planet/Sentinel
            long existingCount = ndviRecordRepository.countByTerrainId(terrainId);
            result.put("existingRecords", existingCount);
            if (!result.containsKey("message")) {
                result.put("message", String.format(
                        "Ya existen %d registros NDVI. Los datos de Planet/Sentinel no generaron nuevos registros. " +
                        "Los datos existentes se muestran en el dashboard.", existingCount));
            }
        }

        // ===== 4. Generate grazing-based alerts =====
        generateGrazingAlerts(terrainId);

        return result;
    }
    
     */   


    /* 
    public Map<String, Object> runAnalysis(Long terrainId) {
    LocalDate endDate = LocalDate.now();
    LocalDate startDate = endDate.minusDays(180);
    return runAnalysis(terrainId, startDate, endDate);
}

*/

    public Map<String, Object> runAnalysis(Long terrainId, LocalDate startDate, LocalDate endDate) {
    Map<String, Object> result = new LinkedHashMap<>();

    Terrain terrain = terrainRepository.findById(terrainId)
            .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

    List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
    if (parcels.isEmpty()) {
        result.put("error", "No hay parcelas definidas en este terreno.");
        return result;
    }

    result.put("terrainId", terrainId);
    result.put("terrainName", terrain.getName());
    result.put("parcelCount", parcels.size());

    String geoJson = terrain.getGeoJson();

    result.put("startDate", startDate);
    result.put("endDate", endDate);

    int totalRecordsProcessed = 0;
    int totalScenesProcessed = 0;
    int planetRecordsProcessed = 0;
    int planetScenesProcessed = 0;
    int sentinelRecordsProcessed = 0;
    int sentinelScenesProcessed = 0;
    long totalProcessingDurationMs = 0L;
    Set<String> sourcesUsed = new LinkedHashSet<>();
    Set<LocalDate> processedDates = new LinkedHashSet<>();
    String planetLastError = null;

    // =========================
    // 1. PLANET
    // =========================
    try {
        if (planetApi.isConfigured()) {
            List<Map<String, Object>> scenes =
                    planetApi.searchScenes(geoJson, startDate, endDate, 0.2);

            if (!scenes.isEmpty()) {

                scenes.sort(Comparator
                    .comparing((Map<String, Object> scene) -> extractSceneDate(scene, "acquired"))
                    .thenComparingDouble(this::extractCloudCover));

                for (Map<String, Object> scene : scenes) {
                    String sceneId = (String) scene.get("id");

                    try {
                        String acquired = (String) scene.get("acquired");

                        Map<String, Object> asset = planetApi.activateAndGetDownloadUrl(sceneId);
                        if (asset == null) {
                            continue;
                        }

                        File geotiff = planetApi.downloadGeoTiff((String) asset.get("url"), sceneId);
                        if (geotiff == null || !geotiff.exists()) {
                            continue;
                        }

                        log.info(
                                "Descarga Planet finalizada para escena {}: {} imagen descargada y enviada a procesamientoImagen. Archivo={}",
                                sceneId,
                                1,
                                geotiff.getName()
                        );

                        LocalDate captureDate = LocalDate.parse(acquired.substring(0, 10));

                        Map<Long, Parcel> pendingParcels = new LinkedHashMap<>();
                        for (Parcel parcel : parcels) {
                            pendingParcels.put(parcel.getId(), parcel);
                        }

                        Double cloudCover = null;
                        Object cloudCoverValue = scene.get("cloud_cover");
                        if (cloudCoverValue instanceof Number number) {
                            cloudCover = number.doubleValue();
                        }

                        PlanetImageProcessingResponse processingResponse = imageProcessingClientService.processPlanetScene(
                                terrain,
                                new ArrayList<>(pendingParcels.values()),
                                geotiff,
                                captureDate,
                                sceneId,
                                (String) asset.get("assetType"),
                                (Integer) asset.get("numBands"),
                                cloudCover
                        );

                        List<NdviRecord> records = processingService.persistProcessedResults(
                                terrain,
                                captureDate,
                                sceneId,
                                cloudCover,
                                "PLANET",
                                processingResponse.getParcelResults(),
                                pendingParcels);

                        geotiff.delete();

                        if (records != null && !records.isEmpty()) {
                            int recordCount = records.size();
                            totalRecordsProcessed += recordCount;
                            totalScenesProcessed++;
                            planetRecordsProcessed += recordCount;
                            planetScenesProcessed++;
                            totalProcessingDurationMs += processingResponse.getProcessingDurationMs();
                            sourcesUsed.add("PLANET");
                            processedDates.add(captureDate);

                            result.put("assetType", processingResponse.getAssetType());
                            result.put("numBands", processingResponse.getNumBands());
                            result.put("rasterWidth", processingResponse.getRasterWidth());
                            result.put("rasterHeight", processingResponse.getRasterHeight());

                            log.info(
                                    "Escena Planet {} procesada correctamente: {} registros NDVI generados para fecha {}",
                                    sceneId,
                                    recordCount,
                                    captureDate
                            );
                        } else {
                            log.warn("Planet escena {} no generó registros NDVI persistibles", sceneId);
                        }
                    } catch (Exception sceneError) {
                        planetLastError = sceneError.getMessage();
                        log.warn("Error procesando escena Planet {}: {}", sceneId, sceneError.getMessage());
                    }
                }
            }
        }
    } catch (Exception e) {
        log.warn("Error con Planet: {}", e.getMessage());
        planetLastError = e.getMessage();
    }

    // =========================
    // 2. SENTINEL
    // =========================
    String sentinelLastError = null;
    try {
        List<Map<String, Object>> scenes =
                sentinelApi.searchScenes(geoJson, startDate, endDate, 0.6);

        if (!scenes.isEmpty()) {

            scenes.sort(Comparator
                    .comparing((Map<String, Object> scene) -> extractSceneDate(scene, "datetime"))
                    .thenComparingDouble(this::extractCloudCover));

            for (Map<String, Object> scene : scenes) {
                String sceneId = (String) scene.get("id");

                try {
                    Map<String, Object> bandData = sentinelApi.downloadAndExtractBands(scene);
                    if (bandData == null) {
                        continue;
                    }

                    File red = (File) bandData.get("redFile");
                    File nir = (File) bandData.get("nirFile");

                    int downloadedImages = 0;
                    if (red != null && red.exists()) {
                        downloadedImages++;
                    }
                    if (nir != null && nir.exists()) {
                        downloadedImages++;
                    }

                    log.info(
                            "Descarga Sentinel finalizada para escena {}: {} imagenes descargadas y enviadas a procesamientoImagen. Red={} NIR={}",
                            sceneId,
                            downloadedImages,
                            red != null ? red.getName() : "N/A",
                            nir != null ? nir.getName() : "N/A"
                    );

                    LocalDate date = LocalDate.parse(
                            ((String) scene.get("datetime")).substring(0, 10));

                    Map<Long, Parcel> pendingParcels = new LinkedHashMap<>();
                    for (Parcel parcel : parcels) {
                        pendingParcels.put(parcel.getId(), parcel);
                    }

                    Double cloudCover = null;
                    Object cloudCoverValue = scene.get("cloud_cover");
                    if (cloudCoverValue instanceof Number number) {
                        cloudCover = number.doubleValue();
                    }

                    SentinelImageProcessingResponse processingResponse = imageProcessingClientService.processSentinelScene(
                            terrain,
                            new ArrayList<>(pendingParcels.values()),
                            red,
                            nir,
                            (int) bandData.getOrDefault("epsg", 32618),
                            (double) bandData.getOrDefault("ulx", 0.0),
                            (double) bandData.getOrDefault("uly", 0.0),
                            date,
                            sceneId,
                            cloudCover
                    );

                    List<NdviRecord> records = processingService.persistSentinelResults(
                            terrain,
                            date,
                            sceneId,
                            cloudCover,
                            processingResponse.getParcelResults(),
                            pendingParcels
                    );

                    if (records != null && !records.isEmpty()) {
                        int recordCount = records.size();
                        totalRecordsProcessed += recordCount;
                        totalScenesProcessed++;
                        sentinelRecordsProcessed += recordCount;
                        sentinelScenesProcessed++;
                        totalProcessingDurationMs += processingResponse.getProcessingDurationMs();
                        sourcesUsed.add("SENTINEL");
                        processedDates.add(date);

                        result.put("rasterWidth", processingResponse.getRasterWidth());
                        result.put("rasterHeight", processingResponse.getRasterHeight());
                        result.put("pixelSize", processingResponse.getPixelSize());

                        log.info(
                                "Escena Sentinel {} procesada correctamente: {} registros NDVI generados para fecha {}",
                                sceneId,
                                recordCount,
                                date
                        );
                    } else {
                        sentinelLastError = processingResponse.getWarnings() == null || processingResponse.getWarnings().isEmpty()
                                ? "procesamientoImagen no devolvió parcelas válidas para la escena Sentinel."
                                : String.join(" | ", processingResponse.getWarnings());
                        log.warn("Sentinel escena {} no generó registros NDVI persistibles", sceneId);
                    }
                } catch (Exception sceneError) {
                    sentinelLastError = sceneError.getMessage();
                    log.warn("Error procesando escena Sentinel {}: {}", sceneId, sceneError.getMessage());
                }
            }
        }
    } catch (Exception e) {
        log.warn("Error con Sentinel: {}", e.getMessage());
        sentinelLastError = e.getMessage();
    }

    if (totalRecordsProcessed > 0) {
        if (!sourcesUsed.isEmpty()) {
            result.put("source", String.join("+", sourcesUsed));
        }
        result.put("recordsProcessed", totalRecordsProcessed);
        result.put("scenesProcessed", totalScenesProcessed);
        result.put("datesProcessed", processedDates.size());
        result.put("processingDurationMs", totalProcessingDurationMs);

        if (planetScenesProcessed > 0) {
            result.put("planetScenesProcessed", planetScenesProcessed);
            result.put("planetRecordsProcessed", planetRecordsProcessed);
        }
        if (sentinelScenesProcessed > 0) {
            result.put("sentinelScenesProcessed", sentinelScenesProcessed);
            result.put("sentinelRecordsProcessed", sentinelRecordsProcessed);
        }
        result.put(
                "message",
                String.format(
                        "Análisis completado. Se generaron %d registros NDVI a partir de %d escenas en %d fechas dentro del rango solicitado.",
                        totalRecordsProcessed,
                        totalScenesProcessed,
                        processedDates.size()
                )
        );
        return result;
    }

    // =========================
    // 3. SIN DATOS
    // =========================
    result.put("source", "NONE");
    if (sentinelLastError != null && !sentinelLastError.isBlank()) {
        result.put("sentinelError", sentinelLastError);
        result.put("message", "Se encontraron imágenes Sentinel en el rango, pero el procesamiento falló al decodificar bandas JP2 grandes.");
    } else if (planetLastError != null && !planetLastError.isBlank()) {
        result.put("planetError", planetLastError);
        result.put("message", "Se encontraron imágenes Planet en el rango, pero no fue posible procesarlas correctamente.");
    } else {
        result.put("message", "No hay datos disponibles ni en Planet ni en Sentinel para este rango de fechas.");
    }

    return result;
}

    private LocalDate extractSceneDate(Map<String, Object> scene, String key) {
        Object rawValue = scene.get(key);
        if (rawValue instanceof String value && value.length() >= 10) {
            try {
                return LocalDate.parse(value.substring(0, 10));
            } catch (Exception ignored) {
                // Keep invalid dates at the end of the processing order.
            }
        }
        return LocalDate.MAX;
    }

    private double extractCloudCover(Map<String, Object> scene) {
        Object rawValue = scene.get("cloud_cover");
        if (rawValue instanceof Number number) {
            return number.doubleValue();
        }
        return Double.MAX_VALUE;
    }

    
    /**
     * Generate alerts for parcels where the estimated grazing days are low
     * or where NDVI/biomass indicates the parcel needs rest.
     */
    private void generateGrazingAlerts(Long terrainId) {
        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);

        for (Parcel parcel : parcels) {
            List<Lote> occupying = loteRepository.findByCurrentParcelId(parcel.getId());
            Lote activeLote = occupying.stream()
                    .filter(l -> l.getFechaSalida() == null)
                    .findFirst().orElse(null);
            if (activeLote == null) continue;

            Optional<NdviRecord> latestNdvi = ndviRecordRepository
                    .findFirstByParcelIdOrderByCaptureDateDesc(parcel.getId());
            if (latestNdvi.isEmpty()) continue;

            Double biomassKgPerHa = latestNdvi.get().getBiomassKgPerHa();
            Double ndvi = latestNdvi.get().getMeanNdvi();
            List<Ganado> ganados = ganadoRepository.findByLoteIdOrderByNumeracion(activeLote.getId());
            if (ganados.isEmpty()) continue;

            double pesoPromedio = ganados.stream().mapToDouble(Ganado::getPesoActual).average().orElse(0);
            int cabezas = ganados.size();

            // Calculate estimated days
            if (biomassKgPerHa != null && parcel.getAreaHectares() != null) {
                double totalBiomass = biomassKgPerHa * parcel.getAreaHectares();
                double residual = totalBiomass * 0.30;
                double available = Math.max(0, totalBiomass - residual);
                double consumoDiarioMS = pesoPromedio * 0.025 * cabezas;
                int estimatedDays = consumoDiarioMS > 0 ? (int) Math.floor(available / consumoDiarioMS) : 0;

                if (estimatedDays == 0) {
                    NdviAlert alert = NdviAlert.builder()
                            .parcel(parcel)
                            .alertType(NdviAlert.AlertType.PASTURE_DEPLETED)
                            .severity(NdviAlert.AlertSeverity.CRITICAL)
                            .threshold(0.0)
                            .currentValue((double) estimatedDays)
                            .message(String.format(
                                "Sin pasto disponible en '%s' para el lote '%s' (%d cab.). "
                                + "Retire el lote y ponga la parcela en descanso.",
                                parcel.getName(), activeLote.getName(), cabezas))
                            .build();
                    ndviAlertRepository.save(alert);
                    log.warn("ALERTA CRITICA: Pasto agotado en parcela {} para lote {}",
                            parcel.getName(), activeLote.getName());
                } else if (estimatedDays <= 3) {
                    NdviAlert alert = NdviAlert.builder()
                            .parcel(parcel)
                            .alertType(NdviAlert.AlertType.GRAZING_DAYS_LOW)
                            .severity(NdviAlert.AlertSeverity.HIGH)
                            .threshold(3.0)
                            .currentValue((double) estimatedDays)
                            .message(String.format(
                                "Solo %d dia(s) de pasto restante en '%s' para '%s' (%d cab.). "
                                + "Planifique la rotacion.",
                                estimatedDays, parcel.getName(), activeLote.getName(), cabezas))
                            .build();
                    ndviAlertRepository.save(alert);
                    log.warn("ALERTA: {} dias de pasto en parcela {} para lote {}",
                            estimatedDays, parcel.getName(), activeLote.getName());
                }
            }

            // NDVI-based alert: if NDVI is critical and parcel is in use
            if (ndvi != null && ndvi < 0.25) {
                NdviAlert alert = NdviAlert.builder()
                        .parcel(parcel)
                        .alertType(NdviAlert.AlertType.REST_RECOMMENDED)
                        .severity(NdviAlert.AlertSeverity.CRITICAL)
                        .threshold(0.25)
                        .currentValue(ndvi)
                        .message(String.format(
                            "NDVI critico (%.2f) en '%s' con lote '%s' activo. "
                            + "El pasto se ha agotado. Se recomienda poner en descanso.",
                            ndvi, parcel.getName(), activeLote.getName()))
                        .build();
                ndviAlertRepository.save(alert);
            }
        }
    }
}