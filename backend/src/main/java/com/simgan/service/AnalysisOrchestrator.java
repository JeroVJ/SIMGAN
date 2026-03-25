package com.simgan.service;

import com.simgan.dto.ProcessedParcelNdviDto;
import com.simgan.dto.PlanetImageProcessingResponse;
import com.simgan.dto.SentinelImageProcessingResponse;
import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final NdviProcessingService processingService;
    private final TerrainRepository terrainRepository;
    private final ParcelRepository parcelRepository;
    private final NdviRecordRepository ndviRecordRepository;
    private final LoteRepository loteRepository;
    private final GanadoRepository ganadoRepository;
    private final NdviAlertRepository ndviAlertRepository;

    public Map<String, Object> runAnalysis(Long terrainId, LocalDate startDate, LocalDate endDate, String biomassMethod) {
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

                        calculateBiomassIfNeeded(processingResponse.getParcelResults(), biomassMethod);

                        List<NdviRecord> records = processingService.persistProcessedResults(
                                terrain,
                                captureDate,
                                sceneId,
                                cloudCover,
                                "PLANET",
                                processingResponse.getParcelResults(),
                                pendingParcels);

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
    } finally {
        try { planetApi.cleanDownloadDir(); } catch (Exception ignored) {}
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
                            scene,
                            date,
                            sceneId,
                            cloudCover
                    );

                    calculateBiomassIfNeeded(processingResponse.getParcelResults(), biomassMethod);

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
     * Calcula biomasa en los resultados de parcela según el método seleccionado.
     * DEFAULT: biomass_kg_ha = max(0, (meanNDVI - 0.1) * 12000)
     * SAMPLING: (no implementado aún)
     */
    private void calculateBiomassIfNeeded(Collection<ProcessedParcelNdviDto> parcelResults, String biomassMethod) {
        if (parcelResults == null || !"DEFAULT".equalsIgnoreCase(biomassMethod)) {
            return;
        }
        for (ProcessedParcelNdviDto dto : parcelResults) {
            if (dto.getMeanNdvi() != null) {
                double biomass = Math.max(0.0, (dto.getMeanNdvi() - 0.1) * 12000.0);
                dto.setBiomassKgPerHa(Math.round(biomass * 100.0) / 100.0);
            }
        }
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