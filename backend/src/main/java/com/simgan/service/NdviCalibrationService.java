package com.simgan.service;

import com.simgan.dto.CalibrationDto;
import com.simgan.dto.PlanetImageProcessingResponse;
import com.simgan.dto.ProcessedParcelNdviDto;
import com.simgan.dto.SentinelImageProcessingResponse;
import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NdviCalibrationService {

    private final NdviCalibrationRepository calibrationRepository;
    private final TerrainRepository terrainRepository;
    private final ParcelRepository parcelRepository;
    private final FarmRepository farmRepository;
    private final SentinelApiService sentinelApi;
    private final PlanetApiService planetApi;
    private final ImageProcessingClientService imageProcessingClientService;

    private static final double MAX_CLOUD_COVER_CALIBRATION = 0.30;

    /**
     * Busca escenas disponibles para la fecha de calibración (±2 días)
     * en Sentinel y Planet, sin procesarlas.
     */
    public List<Map<String, Object>> searchAvailableScenes(Long terrainId, LocalDate calibrationDate) {
        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

        String geoJson = terrain.getGeoJson();
        LocalDate searchStart = calibrationDate.minusDays(2);
        LocalDate searchEnd = calibrationDate.plusDays(2);

        List<Map<String, Object>> availableScenes = new ArrayList<>();

        try {
            List<Map<String, Object>> scenes =
                    sentinelApi.searchScenes(geoJson, searchStart, searchEnd, MAX_CLOUD_COVER_CALIBRATION);

            for (Map<String, Object> scene : scenes) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("sceneId", scene.get("id"));

                String dt = (String) scene.get("datetime");
                if (dt != null && dt.length() >= 10) {
                    info.put("date", dt.substring(0, 10));
                }

                Object cc = scene.get("cloud_cover");
                if (cc instanceof Number n) {
                    info.put("cloudCoverPercent", Math.round(n.doubleValue() * 100.0) / 100.0);
                }
                info.put("source", "SENTINEL");
                availableScenes.add(info);
            }
        } catch (Exception e) {
            log.warn("Error buscando escenas disponibles en Sentinel: {}", e.getMessage());
        }

        try {
            if (planetApi.isConfigured()) {
                List<Map<String, Object>> scenes =
                        planetApi.searchScenes(geoJson, searchStart, searchEnd, MAX_CLOUD_COVER_CALIBRATION);

                for (Map<String, Object> scene : scenes) {
                    String sceneId = (String) scene.get("id");
                    if (sceneId == null || sceneId.isBlank()) {
                        continue;
                    }

                    if (!planetApi.hasDownloadableAsset(sceneId)) {
                        log.info("Escena Planet {} filtrada en calibración: sin assets descargables por licencia/estado.", sceneId);
                        continue;
                    }

                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("sceneId", sceneId);

                    String dt = (String) scene.get("acquired");
                    if (dt != null && dt.length() >= 10) {
                        info.put("date", dt.substring(0, 10));
                    }

                    Object cc = scene.get("cloud_cover");
                    if (cc instanceof Number n) {
                        info.put("cloudCoverPercent", Math.round(n.doubleValue() * 100.0) / 100.0);
                    }
                    info.put("source", "PLANET");
                    availableScenes.add(info);
                }
            }
        } catch (Exception e) {
            log.warn("Error buscando escenas disponibles en Planet: {}", e.getMessage());
        }

        // Ordenar por cercanía a la fecha solicitada, luego menor nubosidad
        availableScenes.sort(Comparator
                .<Map<String, Object>>comparingLong(s -> {
                    String d = (String) s.get("date");
                    if (d != null) {
                        return Math.abs(LocalDate.parse(d).toEpochDay() - calibrationDate.toEpochDay());
                    }
                    return Long.MAX_VALUE;
                })
                .thenComparingDouble(s -> {
                    Object cc = s.get("cloudCoverPercent");
                    return cc instanceof Number n ? n.doubleValue() : Double.MAX_VALUE;
                }));

        return availableScenes;
    }

    /**
     * Verifica si el terreno ya tiene calibración completa para el tipo dado.
     * @param calibrationType "OPTIM" o "ALERT"
     */
    public CalibrationDto.CalibrationStatus getCalibrationStatus(Long terrainId, String calibrationType) {
        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

        Farm farm = terrain.getFarm();
        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
        List<NdviCalibration> calibrations = calibrationRepository.findByTerrainIdAndCalibrationType(terrainId, calibrationType);

        List<CalibrationDto.CalibrationResponse> responses = calibrations.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        // Siempre una sola calibración a nivel terreno (promedio de todos los potreros)
        boolean calibrated = calibrations.stream().anyMatch(c -> c.getParcel() == null);
        int calibratedParcels = calibrated ? parcels.size() : 0;

        return CalibrationDto.CalibrationStatus.builder()
                .terrainId(terrainId)
                .calibrationType(calibrationType)
                .calibrated(calibrated)
                .homogeneous(true)
                .totalParcels(parcels.size())
                .calibratedParcels(calibratedParcels)
                .calibrations(responses)
                .build();
    }

    /**
     * Ejecuta la calibración NDVI para un terreno.
     * @param calibrationType "OPTIM" (referencia óptima) o "ALERT" (umbral de alerta)
     * @param selectedSceneId ID de escena específica seleccionada por el usuario (puede ser null)
     *
     * Se promedian los NDVI de todos los potreros para obtener una única referencia a nivel terreno.
     */
    public Map<String, Object> runCalibration(Long terrainId, LocalDate calibrationDate, String calibrationType) {
        return runCalibration(terrainId, calibrationDate, calibrationType, null);
    }

    public Map<String, Object> runCalibration(Long terrainId, LocalDate calibrationDate, String calibrationType, String selectedSceneId) {
        Map<String, Object> result = new LinkedHashMap<>();

        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

        Farm farm = terrain.getFarm();
        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);

        if (parcels.isEmpty()) {
            result.put("error", "No hay parcelas definidas en este terreno.");
            return result;
        }

        String geoJson = terrain.getGeoJson();
        result.put("terrainId", terrainId);
        result.put("terrainName", terrain.getName());
        result.put("calibrationType", calibrationType);
        result.put("calibrationDate", calibrationDate.toString());

        // Buscar escenas con <= 30% nubosidad para la fecha de calibración
        // Buscamos en ventana de +/- 2 días por si no hay imagen exacta
        LocalDate searchStart = calibrationDate.minusDays(2);
        LocalDate searchEnd = calibrationDate.plusDays(2);

        List<ProcessedParcelNdviDto> parcelResults = null;
        String usedSceneId = null;
        Double usedCloudCover = null;
        String usedSource = "SENTINEL";

        // === SENTINEL ===
        try {
            List<Map<String, Object>> scenes =
                    sentinelApi.searchScenes(geoJson, searchStart, searchEnd, MAX_CLOUD_COVER_CALIBRATION);

            // Si el usuario seleccionó una escena específica, filtrar solo esa
            if (selectedSceneId != null && !selectedSceneId.isBlank()) {
                scenes = scenes.stream()
                        .filter(s -> selectedSceneId.equals(s.get("id")))
                        .collect(Collectors.toList());
            }

            if (!scenes.isEmpty()) {
                // Ordenar por cercanía a la fecha de calibración, luego menor nubosidad
                scenes.sort(Comparator
                        .<Map<String, Object>>comparingLong(scene -> {
                            String dt = (String) scene.get("datetime");
                            if (dt != null && dt.length() >= 10) {
                                LocalDate d = LocalDate.parse(dt.substring(0, 10));
                                return Math.abs(d.toEpochDay() - calibrationDate.toEpochDay());
                            }
                            return Long.MAX_VALUE;
                        })
                        .thenComparingDouble(scene -> {
                            Object cc = scene.get("cloud_cover");
                            return cc instanceof Number n ? n.doubleValue() : Double.MAX_VALUE;
                        }));

                // Procesar con la mejor escena
                for (Map<String, Object> scene : scenes) {
                    String sceneId = (String) scene.get("id");
                    LocalDate sceneDate = LocalDate.parse(
                            ((String) scene.get("datetime")).substring(0, 10));

                    Double cloudCover = null;
                    Object ccVal = scene.get("cloud_cover");
                    if (ccVal instanceof Number n) {
                        cloudCover = n.doubleValue();
                    }

                    try {
                        SentinelImageProcessingResponse response = imageProcessingClientService.processSentinelScene(
                                terrain,
                                new ArrayList<>(parcels),
                                scene,
                                sceneDate,
                                sceneId,
                                cloudCover
                        );

                        if (response.getParcelResults() != null && !response.getParcelResults().isEmpty()) {
                            // Calcular biomasa
                            for (ProcessedParcelNdviDto dto : response.getParcelResults()) {
                                if (dto.getMeanNdvi() != null) {
                                    double biomass = Math.max(0.0, (dto.getMeanNdvi() - 0.1) * 12000.0);
                                    dto.setBiomassKgPerHa(Math.round(biomass * 100.0) / 100.0);
                                }
                            }
                            parcelResults = response.getParcelResults();
                            usedSceneId = sceneId;
                            usedCloudCover = cloudCover;
                            log.info("Calibración: escena Sentinel {} procesada para terreno {}", sceneId, terrainId);
                            break;
                        }
                    } catch (Exception e) {
                        log.warn("Calibración: error procesando escena Sentinel {}: {}", sceneId, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Calibración: error buscando escenas Sentinel: {}", e.getMessage());
        } finally {
            try { sentinelApi.cleanDownloadDir(); } catch (Exception ignored) {}
        }

        // === PLANET ===
        // Si no hubo resultados con Sentinel, o si el usuario eligió una escena específica
        // que no pertenece a Sentinel, intentamos con Planet.
        if (parcelResults == null || parcelResults.isEmpty()) {
            try {
                if (planetApi.isConfigured()) {
                    List<Map<String, Object>> scenes =
                            planetApi.searchScenes(geoJson, searchStart, searchEnd, MAX_CLOUD_COVER_CALIBRATION);

                    if (selectedSceneId != null && !selectedSceneId.isBlank()) {
                        scenes = scenes.stream()
                                .filter(s -> selectedSceneId.equals(s.get("id")))
                                .collect(Collectors.toList());
                    }

                    if (!scenes.isEmpty()) {
                        scenes.sort(Comparator
                                .<Map<String, Object>>comparingLong(scene -> {
                                    String dt = (String) scene.get("acquired");
                                    if (dt != null && dt.length() >= 10) {
                                        LocalDate d = LocalDate.parse(dt.substring(0, 10));
                                        return Math.abs(d.toEpochDay() - calibrationDate.toEpochDay());
                                    }
                                    return Long.MAX_VALUE;
                                })
                                .thenComparingDouble(scene -> {
                                    Object cc = scene.get("cloud_cover");
                                    return cc instanceof Number n ? n.doubleValue() : Double.MAX_VALUE;
                                }));

                        for (Map<String, Object> scene : scenes) {
                            String sceneId = (String) scene.get("id");
                            String acquired = (String) scene.get("acquired");
                            if (acquired == null || acquired.length() < 10) {
                                continue;
                            }
                            LocalDate sceneDate = LocalDate.parse(acquired.substring(0, 10));

                            Double cloudCover = null;
                            Object ccVal = scene.get("cloud_cover");
                            if (ccVal instanceof Number n) {
                                cloudCover = n.doubleValue();
                            }

                            try {
                                Map<String, Object> asset = planetApi.activateAndGetDownloadUrl(sceneId);
                                if (asset == null) {
                                    log.warn("Calibración: escena Planet {} sin asset descargable activo", sceneId);
                                    continue;
                                }

                                PlanetImageProcessingResponse response = imageProcessingClientService.processPlanetScene(
                                        terrain,
                                        new ArrayList<>(parcels),
                                        sceneDate,
                                        sceneId,
                                        (String) asset.get("url"),
                                        (String) asset.get("assetType"),
                                        (Integer) asset.get("numBands"),
                                        cloudCover
                                );

                                if (response.getParcelResults() != null && !response.getParcelResults().isEmpty()) {
                                    for (ProcessedParcelNdviDto dto : response.getParcelResults()) {
                                        if (dto.getMeanNdvi() != null) {
                                            double biomass = Math.max(0.0, (dto.getMeanNdvi() - 0.1) * 12000.0);
                                            dto.setBiomassKgPerHa(Math.round(biomass * 100.0) / 100.0);
                                        }
                                    }
                                    parcelResults = response.getParcelResults();
                                    usedSceneId = sceneId;
                                    usedCloudCover = cloudCover;
                                    usedSource = "PLANET";
                                    log.info("Calibración: escena Planet {} procesada para terreno {}", sceneId, terrainId);
                                    break;
                                }
                            } catch (Exception e) {
                                log.warn("Calibración: error procesando escena Planet {}: {}", sceneId, e.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Calibración: error buscando escenas Planet: {}", e.getMessage());
            } finally {
                try { planetApi.cleanDownloadDir(); } catch (Exception ignored) {}
            }
        }

        if ((parcelResults == null || parcelResults.isEmpty())
                && selectedSceneId != null
                && !selectedSceneId.isBlank()) {
            result.put("error", "La escena seleccionada " + selectedSceneId + " no se encontró o no fue procesable.");
            return result;
        }

        if (parcelResults == null || parcelResults.isEmpty()) {
            result.put("error", String.format(
                    "No se encontraron imágenes satelitales con ≤%.0f%% de nubosidad para la fecha %s (±2 días).",
                    MAX_CLOUD_COVER_CALIBRATION * 100, calibrationDate));
            return result;
        }

        // Guardar calibraciones — siempre promedio de todos los potreros a nivel terreno
        List<NdviCalibration> savedCalibrations = new ArrayList<>();

        double avgNdvi = parcelResults.stream()
                .filter(pr -> pr.getMeanNdvi() != null && pr.getPixelCount() != null && pr.getPixelCount() > 0)
                .mapToDouble(ProcessedParcelNdviDto::getMeanNdvi)
                .average()
                .orElse(0.0);

        int totalPixels = parcelResults.stream()
                .filter(pr -> pr.getPixelCount() != null)
                .mapToInt(ProcessedParcelNdviDto::getPixelCount)
                .sum();

        // Eliminar calibración previa del terreno (mismo tipo)
        calibrationRepository.findByTerrainIdAndParcelIdIsNullAndCalibrationType(terrainId, calibrationType)
                .ifPresent(calibrationRepository::delete);
        // También eliminar calibraciones por parcela del mismo tipo (limpieza de datos viejos)
        List<NdviCalibration> oldParcelCals = calibrationRepository.findByTerrainIdAndCalibrationType(terrainId, calibrationType);
        for (NdviCalibration old : oldParcelCals) {
            if (old.getParcel() != null) {
                calibrationRepository.delete(old);
            }
        }

        NdviCalibration cal = NdviCalibration.builder()
                .terrain(terrain)
                .parcel(null)
                .calibrationDate(calibrationDate)
                .calibrationType(calibrationType)
                .referenceNdvi(Math.round(avgNdvi * 10000.0) / 10000.0)
                .pastureType(farm.getPastureType())
                .source(usedSource)
                .sceneId(usedSceneId)
                .cloudCoverPercent(usedCloudCover)
                .pixelCount(totalPixels)
                .build();

        savedCalibrations.add(calibrationRepository.save(cal));
        log.info("Calibración guardada: terreno={} tipo={} ndvi={} parcelas_promediadas={}",
            terrainId, calibrationType, avgNdvi, parcelResults.size());

        result.put("calibrationsCreated", savedCalibrations.size());
        result.put("source", usedSource);
        result.put("sceneId", usedSceneId);
        result.put("cloudCoverPercent", usedCloudCover);
        result.put("calibrations", savedCalibrations.stream().map(this::toResponse).collect(Collectors.toList()));
        result.put("message", String.format(
                "Calibración completada. Se crearon %d referencias NDVI óptimas para el terreno.",
                savedCalibrations.size()));

        return result;
    }

    private CalibrationDto.CalibrationResponse toResponse(NdviCalibration cal) {
        return CalibrationDto.CalibrationResponse.builder()
                .id(cal.getId())
                .terrainId(cal.getTerrain().getId())
                .terrainName(cal.getTerrain().getName())
                .parcelId(cal.getParcel() != null ? cal.getParcel().getId() : null)
                .parcelName(cal.getParcel() != null ? cal.getParcel().getName() : null)
                .calibrationDate(cal.getCalibrationDate())
                .calibrationType(cal.getCalibrationType())
                .referenceNdvi(cal.getReferenceNdvi())
                .pastureType(cal.getPastureType())
                .source(cal.getSource())
                .sceneId(cal.getSceneId())
                .cloudCoverPercent(cal.getCloudCoverPercent())
                .pixelCount(cal.getPixelCount())
                .build();
    }
}
