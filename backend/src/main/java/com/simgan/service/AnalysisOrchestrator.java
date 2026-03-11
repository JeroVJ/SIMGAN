package com.simgan.service;

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
    private final NdviProcessingService processingService;
    private final NdviSeedService seedService;
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
