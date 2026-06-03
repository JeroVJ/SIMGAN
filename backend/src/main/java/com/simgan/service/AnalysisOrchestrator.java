package com.simgan.service;

import com.simgan.dto.ProcessedParcelNdviDto;
import com.simgan.dto.PlanetImageProcessingResponse;
import com.simgan.dto.SentinelImageProcessingResponse;
import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Orquestador del pipeline de análisis NDVI.
 *
 * Pipeline:
 *   1. Obtener GeoJSON del terreno
 *   2. Buscar escenas disponibles (Sentinel-2)
 *   3. Enviar al servicio de procesamiento Python
 *   4. Guardar registros + generar alertas
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisOrchestrator {

    private final PlanetApiService planetApi;
    private final SentinelApiService sentinelApi;
    private final ImageProcessingClientService imageProcessingClientService;
    private final TerrainRepository terrainRepository;
    private final ParcelRepository parcelRepository;
    private final BiomassCalibrationModelRepository biomassModelRepository;
    private final NdviCalibrationRepository ndviCalibrationRepository;
    private final AlertRepository alertRepository;
    private final EmailAlertService emailAlertService;

    @Value("${ndvi.alert.threshold:0.1}")
    private double defaultAlertThreshold;

    @Value("${ndvi.optimal.threshold:0.6}")
    private double defaultOptimalThreshold;
    


    public Map<String, Object> runAnalysis(Long terrainId, LocalDate startDate, LocalDate endDate, String biomassMethod) {
    Map<String, Object> result = new LinkedHashMap<>();

    Terrain terrain = terrainRepository.findById(terrainId)
            .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

    List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
    if (parcels.isEmpty()) {
        String msg = "El terreno no tiene potreros. Crea al menos uno antes de pedir un análisis NDVI.";
        result.put("error", msg);
        result.put("message", msg);
        return result;
    }

    result.put("terrainId", terrainId);
    result.put("terrainName", terrain.getName());
    result.put("parcelCount", parcels.size());

    String geoJson = terrain.getGeoJson();

    result.put("startDate", startDate);
    result.put("endDate", endDate);

    // Pre-load biomass calibration models indexed by parcel ID (used for SAMPLING method)
    Map<Long, BiomassCalibrationModel> samplingModels = new HashMap<>();
    if ("SAMPLING".equalsIgnoreCase(biomassMethod)) {
        biomassModelRepository.findByTerrainId(terrainId)
                .forEach(m -> samplingModels.put(m.getParcel().getId(), m));
        log.info("Método SAMPLING: {} modelos de calibración cargados para terreno {}", samplingModels.size(), terrainId);
    }

    int totalRecordsProcessed = 0;
    int totalScenesProcessed = 0;
    int planetRecordsProcessed = 0;
    int planetScenesProcessed = 0;
    int planetScenesFound = 0;
    int planetScenesSkippedNoAsset = 0;
    int sentinelRecordsProcessed = 0;
    int sentinelScenesProcessed = 0;
    long totalProcessingDurationMs = 0L;
    Set<String> sourcesUsed = new LinkedHashSet<>();
    Set<LocalDate> processedDates = new LinkedHashSet<>();
    String planetLastError = null;  


    /* 

    // =========================
    // 1. PLANET
    // =========================
    try {
        if (planetApi.isConfigured()) {
            List<Map<String, Object>> scenes =
                    planetApi.searchScenes(geoJson, startDate, endDate, 0.2);

            planetScenesFound = scenes.size();
            if (planetScenesFound == 0) {
                log.info("Planet no devolvió escenas para terreno {} en rango {} -> {}", terrainId, startDate, endDate);
            }

            if (!scenes.isEmpty()) {
            //    - Ordena las escenas:
            //    - Primero por fecha de adquisición
            //    - Luego por menor cobertura de nubes
                scenes.sort(Comparator
                    .comparing((Map<String, Object> scene) -> extractSceneDate(scene, "acquired"))
                    .thenComparingDouble(this::extractCloudCover));

                for (Map<String, Object> scene : scenes) {
                    String sceneId = (String) scene.get("id");

                    try {
                        String acquired = (String) scene.get("acquired");

                        Map<String, Object> asset = planetApi.activateAndGetDownloadUrl(sceneId);
                        if (asset == null) {
                            planetScenesSkippedNoAsset++;
                            log.warn("Planet escena {} omitida: no hay asset descargable activo (todavía activando o sin permisos para asset).", sceneId);
                            continue;
                        }

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
                                captureDate,
                                sceneId,
                            (String) asset.get("url"),
                                (String) asset.get("assetType"),
                                (Integer) asset.get("numBands"),
                                cloudCover
                        );

                        calculateBiomassIfNeeded(processingResponse.getParcelResults(), biomassMethod, samplingModels);

                        List<NdviRecord> records = imageProcessingClientService.persistProcessedResults(
                                terrain,
                                captureDate,
                                sceneId,
                                cloudCover,
                                "PLANET",
                                processingResponse.getParcelResults(),
                                pendingParcels);

                        if (records != null && !records.isEmpty()) {
                            createNdviThresholdAlerts(records, terrain.getId());

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
        } else {
            log.warn("Planet está deshabilitado para esta corrida: isConfigured()=false");
        }
    } catch (Exception e) {
        log.warn("Error con Planet: {}", e.getMessage());
        planetLastError = e.getMessage();
    } finally {
        try { planetApi.cleanDownloadDir(); } catch (Exception ignored) {}
    } 

    */

    // =========================
    // 2. SENTINEL
    // =========================
    String sentinelLastError = null;
    try {
        List<Map<String, Object>> scenes =
                sentinelApi.searchScenes(geoJson, startDate, endDate, 0.3);

        if (!scenes.isEmpty()) {

            scenes.sort(Comparator
                    .comparing((Map<String, Object> scene) -> extractSceneDate(scene, "datetime"))
                    .thenComparingDouble(this::extractCloudCover));

            // Agrupar escenas por fecha para evitar tiles redundantes
            Map<LocalDate, List<Map<String, Object>>> scenesByDate = new LinkedHashMap<>();
            for (Map<String, Object> scene : scenes) {
                LocalDate date = extractSceneDate(scene, "datetime");
                scenesByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(scene);
            }

            for (Map.Entry<LocalDate, List<Map<String, Object>>> entry : scenesByDate.entrySet()) {
                LocalDate date = entry.getKey();
                List<Map<String, Object>> tilesForDate = entry.getValue();

                // Parcelas pendientes: las que aún no tienen resultado válido para esta fecha
                Set<Long> coveredParcelIds = new HashSet<>();

                for (Map<String, Object> scene : tilesForDate) {
                    String sceneId = (String) scene.get("id");

                    // Solo enviar parcelas que aún no están cubiertas
                    Map<Long, Parcel> pendingParcels = new LinkedHashMap<>();
                    for (Parcel parcel : parcels) {
                        if (!coveredParcelIds.contains(parcel.getId())) {
                            pendingParcels.put(parcel.getId(), parcel);
                        }
                    }

                    if (pendingParcels.isEmpty()) {
                        log.info("Sentinel tile {} omitido: todas las parcelas ya cubiertas para fecha {}", sceneId, date);
                        break;
                    }

                    try {
                        Double cloudCover = null;
                        Object cloudCoverValue = scene.get("cloud_cover");
                        if (cloudCoverValue instanceof Number number) {
                            cloudCover = number.doubleValue();
                        }

                        SentinelImageProcessingResponse processingResponse = imageProcessingClientService.processSentinelScene(
                                terrain,
                                new ArrayList<>(pendingParcels.values()),
                                scene,
                                date,
                                sceneId,
                                cloudCover
                        );

                        calculateBiomassIfNeeded(processingResponse.getParcelResults(), biomassMethod, samplingModels);

                        // Filtrar solo parcelas con pixelCount > 0 (cobertura real del tile)
                        List<ProcessedParcelNdviDto> validResults = new ArrayList<>();
                        if (processingResponse.getParcelResults() != null) {
                            for (ProcessedParcelNdviDto pr : processingResponse.getParcelResults()) {
                                if (pr != null && pr.getParcelId() != null
                                        && pr.getPixelCount() != null && pr.getPixelCount() > 0) {
                                    validResults.add(pr);
                                    coveredParcelIds.add(pr.getParcelId());
                                }
                            }
                        }

                        List<NdviRecord> records = imageProcessingClientService.persistSentinelResults(
                                terrain,
                                date,
                                sceneId,
                                cloudCover,
                                validResults,
                                pendingParcels
                        );

                        if (records != null && !records.isEmpty()) {
                            createNdviThresholdAlerts(records, terrain.getId());

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
                                    "Escena Sentinel {} procesada: {} registros NDVI para fecha {} (cubiertas {}/{} parcelas)",
                                    sceneId,
                                    recordCount,
                                    date,
                                    coveredParcelIds.size(),
                                    parcels.size()
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
        }
    } catch (Exception e) {
        log.warn("Error con Sentinel: {}", e.getMessage());
        sentinelLastError = e.getMessage();
    } finally {
        try { sentinelApi.cleanDownloadDir(); } catch (Exception ignored) {}
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
        if (planetScenesFound > 0) {
            result.put("planetScenesFound", planetScenesFound);
            result.put("planetScenesSkippedNoAsset", planetScenesSkippedNoAsset);
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
    result.put("planetScenesFound", planetScenesFound);
    result.put("planetScenesSkippedNoAsset", planetScenesSkippedNoAsset);
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

    private void createNdviThresholdAlerts(List<NdviRecord> records, Long terrainId) {
        for (NdviRecord record : records) {
            if (record == null || record.getParcel() == null || record.getMeanNdvi() == null) {
                continue;
            }

            double alertThreshold = resolveAlertThreshold(record.getParcel().getId(), terrainId);
            if (record.getMeanNdvi() <= alertThreshold) {
                if (existsAlertTypeToday(record.getParcel().getId(), Alert.AlertType.ESTADO_FORRAJE_BAJO_O_EN_UMBRAL)) {
                    continue;
                }

                Alert savedAlert = alertRepository.save(Alert.builder()
                        .parcel(record.getParcel())
                        .alertType(Alert.AlertType.ESTADO_FORRAJE_BAJO_O_EN_UMBRAL)
                        .message("estado de forraje en mal estado, riesgo de sobrepastoreo.")
                        .build());
                emailAlertService.sendAlertEmail(savedAlert);

            } else if (record.getParcel().getStatus() == Parcel.ParcelStatus.EN_DESCANSO) {
                // Recovery: a resting parcel whose NDVI climbed back to optimal is
                // ready for grazing again — notify the owner.
                double optimalThreshold = resolveOptimalThreshold(record.getParcel().getId(), terrainId);
                if (record.getMeanNdvi() >= optimalThreshold) {
                    if (existsAlertTypeToday(record.getParcel().getId(), Alert.AlertType.POTRERO_RECUPERADO)) {
                        continue;
                    }
                    Alert savedAlert = alertRepository.save(Alert.builder()
                            .parcel(record.getParcel())
                            .alertType(Alert.AlertType.POTRERO_RECUPERADO)
                            .message(String.format(
                                    "El potrero se recuperó (NDVI %.2f ≥ óptimo %.2f). Ya está listo para volver a pastoreo.",
                                    record.getMeanNdvi(), optimalThreshold))
                            .build());
                    emailAlertService.sendAlertEmail(savedAlert);
                }
            }
        }
    }

    private double resolveOptimalThreshold(Long parcelId, Long terrainId) {
        return ndviCalibrationRepository
                .findByParcelIdAndCalibrationType(parcelId, "OPTIM")
                .map(NdviCalibration::getReferenceNdvi)
                .or(() -> ndviCalibrationRepository.findByTerrainIdAndParcelIdIsNullAndCalibrationType(terrainId, "OPTIM")
                        .map(NdviCalibration::getReferenceNdvi))
                .orElse(defaultOptimalThreshold);
    }

    private boolean existsAlertTypeToday(Long parcelId, Alert.AlertType alertType) {
        return alertRepository.findByParcelIdAndDate(parcelId, LocalDate.now())
                .stream()
                .anyMatch(a -> a.getAlertType() == alertType);
    }

    private double resolveAlertThreshold(Long parcelId, Long terrainId) {
        return ndviCalibrationRepository
                .findByParcelIdAndCalibrationType(parcelId, "ALERT")
                .map(NdviCalibration::getReferenceNdvi)
                .or(() -> ndviCalibrationRepository.findByTerrainIdAndParcelIdIsNullAndCalibrationType(terrainId, "ALERT")
                        .map(NdviCalibration::getReferenceNdvi))
                .orElse(defaultAlertThreshold);
    }

    /**
     * Calcula biomasa en los resultados de parcela según el método seleccionado.
     * DEFAULT:  biomass_kg_ha = max(0, (meanNDVI - 0.1) * 12000)
    * SAMPLING: biomass_kg_ha = max(0, a * meanNDVI)  usando el modelo de regresión calibrado por potrero
     *           Si la parcela no tiene modelo calibrado, aplica la fórmula DEFAULT como fallback.
     */
    private void calculateBiomassIfNeeded(
            Collection<ProcessedParcelNdviDto> parcelResults,
            String biomassMethod,
            Map<Long, BiomassCalibrationModel> samplingModels) {

        if (parcelResults == null) return;

        boolean isSampling = "SAMPLING".equalsIgnoreCase(biomassMethod);
        boolean isDefault  = "DEFAULT".equalsIgnoreCase(biomassMethod);

        if (!isSampling && !isDefault) return;

        for (ProcessedParcelNdviDto dto : parcelResults) {
            if (dto.getMeanNdvi() == null) continue;

            double biomass;
            if (isSampling) {
                BiomassCalibrationModel model = samplingModels.get(dto.getParcelId());
                if (model != null && model.getCoefficientA() != null) {
                    biomass = Math.max(0.0, model.getCoefficientA() * dto.getMeanNdvi());
                    log.debug("SAMPLING parcel={} ndvi={} => biomass={} (a={})",
                            dto.getParcelId(), dto.getMeanNdvi(), biomass,
                            model.getCoefficientA());
                } else {
                    // Fallback: parcel not yet calibrated — use DEFAULT formula
                    biomass = Math.max(0.0, (dto.getMeanNdvi() - 0.1) * 12000.0);
                    log.warn("SAMPLING: parcela {} sin modelo calibrado, usando fórmula DEFAULT", dto.getParcelId());
                }
            } else {
                biomass = Math.max(0.0, (dto.getMeanNdvi() - 0.1) * 12000.0);
            }

            dto.setBiomassKgPerHa(Math.round(biomass * 100.0) / 100.0);
        }
    }

    
   
}