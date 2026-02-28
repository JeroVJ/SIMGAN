package com.simgan.service;

import com.simgan.entity.NdviRecord;
import com.simgan.entity.Parcel;
import com.simgan.entity.Terrain;
import com.simgan.repository.NdviRecordRepository;
import com.simgan.repository.ParcelRepository;
import com.simgan.repository.TerrainRepository;
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
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(90);

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

                    // Lógica de reintento: Intentamos con las 3 más recientes por si la última aún no tiene assets listos
                    for (int i = 0; i < Math.min(scenes.size(), 3); i++) {
                        Map<String, Object> currentScene = scenes.get(i);
                        String sceneId = (String) currentScene.get("id");
                        Double cloudCover = (Double) currentScene.get("cloud_cover");
                        String acquiredStr = (String) currentScene.get("acquired");

                        log.info("Planet: Evaluando escena {}/{} -> ID: {} (Fecha: {}, Nubes: {}%)",
                                (i + 1), Math.min(scenes.size(), 3), sceneId, acquiredStr, cloudCover);

                        // Proceso de activación y obtención de URL de descarga
                        String downloadUrl = planetApi.activateAndGetDownloadUrl(sceneId);

                        if (downloadUrl != null) {
                            File geotiff = planetApi.downloadGeoTiff(downloadUrl, sceneId);

                            if (geotiff != null && geotiff.exists()) {
                                // Extraer la fecha real de captura para el registro
                                LocalDate captureDate = LocalDate.parse(acquiredStr.substring(0, 10));

                                // Procesar GeoTIFF (con soporte de coordenadas GeoTools corregido)
                                List<NdviRecord> records = processingService.processGeoTiff(
                                        geotiff, terrainId, captureDate, sceneId);

                                if (records != null && !records.isEmpty()) {
                                    result.put("selectedScene", sceneId);
                                    result.put("cloudCover", cloudCover);
                                    result.put("recordsProcessed", records.size());
                                    result.put("message", String.format(
                                            "✅ Monitoreo en tiempo real completado. %d parcelas analizadas. Fecha: %s",
                                            records.size(), captureDate));

                                    // Limpieza del archivo temporal y marcamos como éxito
                                    geotiff.delete();
                                    processedSuccess = true;
                                    break;
                                }
                                geotiff.delete();
                            }
                        } else {
                            log.warn("Planet: Asset no disponible para escena {}. Intentando con la siguiente más reciente...", sceneId);
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
            List<Map<String, Object>> scenes = sentinelApi.searchScenes(geoJson, startDate, endDate, 0.3);

            if (!scenes.isEmpty()) {
                result.put("source", result.containsKey("source") ? "PLANET+SENTINEL" : "SENTINEL");
                result.put("sentinelScenesFound", scenes.size());
                log.info("Sentinel: {} escenas encontradas", scenes.size());

                // For now, report what we found (full download needs Copernicus account)
                Map<String, Object> bestScene = scenes.get(0);
                result.put("sentinelScene", bestScene.get("id"));
                result.put("sentinelCloudCover", bestScene.get("cloud_cover"));

                if (sentinelApi.isConfigured()) {
                    // TODO: Download Red + NIR bands, compute NDVI
                    result.put("sentinelNote", "Escena encontrada. Descarga de bandas en desarrollo.");
                } else {
                    result.put("sentinelNote", String.format(
                            "Se encontraron %d escenas Sentinel-2. Configure sentinel.access.token " +
                            "para descargar imágenes. Usando datos demo mientras tanto.", scenes.size()));
                }
            } else {
                log.info("Sentinel: sin escenas en rango");
                result.put("sentinelNote", "Sin escenas Sentinel-2 en los últimos 30 días.");
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

        return result;
    }
}
