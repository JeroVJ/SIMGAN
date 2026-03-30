package com.simgan.service;

import com.simgan.dto.CalibrationDto;
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
    // private final PlanetApiService planetApi;
    private final ImageProcessingClientService imageProcessingClientService;

    private static final double MAX_CLOUD_COVER_CALIBRATION = 0.20;

    /**
     * Verifica si el terreno ya tiene calibración completa para el tipo dado.
     * @param calibrationType "OPTIM" o "ALERT"
     */
    public CalibrationDto.CalibrationStatus getCalibrationStatus(Long terrainId, String calibrationType) {
        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

        Farm farm = terrain.getFarm();
        boolean homogeneous = Boolean.TRUE.equals(farm.getIsHomogeneous());
        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
        List<NdviCalibration> calibrations = calibrationRepository.findByTerrainIdAndCalibrationType(terrainId, calibrationType);

        List<CalibrationDto.CalibrationResponse> responses = calibrations.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        boolean calibrated;
        int calibratedParcels;

        if (homogeneous) {
            // Finca homogénea: basta con 1 calibración a nivel terreno
            calibrated = calibrations.stream().anyMatch(c -> c.getParcel() == null);
            calibratedParcels = calibrated ? parcels.size() : 0;
        } else {
            // Finca no homogénea: cada tipo de pasto necesita calibración
            Set<String> calibratedPastureTypes = calibrations.stream()
                    .filter(c -> c.getPastureType() != null)
                    .map(NdviCalibration::getPastureType)
                    .collect(Collectors.toSet());

            calibratedParcels = 0;
            for (Parcel p : parcels) {
                if (calibrations.stream().anyMatch(c -> c.getParcel() != null && c.getParcel().getId().equals(p.getId()))) {
                    calibratedParcels++;
                } else if (p.getPastureType() != null && calibratedPastureTypes.contains(p.getPastureType())) {
                    calibratedParcels++;
                }
            }
            calibrated = calibratedParcels >= parcels.size();
        }

        return CalibrationDto.CalibrationStatus.builder()
                .terrainId(terrainId)
                .calibrationType(calibrationType)
                .calibrated(calibrated)
                .homogeneous(homogeneous)
                .totalParcels(parcels.size())
                .calibratedParcels(calibratedParcels)
                .calibrations(responses)
                .build();
    }

    /**
     * Ejecuta la calibración NDVI para un terreno.
     * @param calibrationType "OPTIM" (referencia óptima) o "ALERT" (umbral de alerta)
     *
     * Para fincas homogéneas: una sola calibración a nivel terreno.
     * Para fincas no homogéneas: calibración por parcela, reutilizando si comparten tipo de pasto.
     */
    public Map<String, Object> runCalibration(Long terrainId, LocalDate calibrationDate, String calibrationType) {
        Map<String, Object> result = new LinkedHashMap<>();

        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

        Farm farm = terrain.getFarm();
        boolean homogeneous = Boolean.TRUE.equals(farm.getIsHomogeneous());
        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);

        if (parcels.isEmpty()) {
            result.put("error", "No hay parcelas definidas en este terreno.");
            return result;
        }

        String geoJson = terrain.getGeoJson();
        result.put("terrainId", terrainId);
        result.put("terrainName", terrain.getName());
        result.put("homogeneous", homogeneous);
        result.put("calibrationType", calibrationType);
        result.put("calibrationDate", calibrationDate.toString());

        // Buscar escenas Sentinel con <= 20% nubosidad para la fecha de calibración
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

        // === PLANET (comentado - sin escenas disponibles) ===
        // if (parcelResults == null) {
        //     try {
        //         if (planetApi.isConfigured()) {
        //             List<Map<String, Object>> scenes =
        //                     planetApi.searchScenes(geoJson, searchStart, searchEnd, MAX_CLOUD_COVER_CALIBRATION);
        //             // ... procesar Planet igual que Sentinel ...
        //         }
        //     } catch (Exception e) {
        //         log.warn("Calibración: error con Planet: {}", e.getMessage());
        //     }
        // }

        if (parcelResults == null || parcelResults.isEmpty()) {
            result.put("error", String.format(
                    "No se encontraron imágenes satelitales con ≤%.0f%% de nubosidad para la fecha %s (±2 días).",
                    MAX_CLOUD_COVER_CALIBRATION * 100, calibrationDate));
            return result;
        }

        // Guardar calibraciones
        List<NdviCalibration> savedCalibrations = new ArrayList<>();

        if (homogeneous) {
            // Finca homogénea: promedio de todas las parcelas como referencia del terreno
            double avgNdvi = parcelResults.stream()
                    .filter(pr -> pr.getMeanNdvi() != null && pr.getPixelCount() != null && pr.getPixelCount() > 0)
                    .mapToDouble(ProcessedParcelNdviDto::getMeanNdvi)
                    .average()
                    .orElse(0.0);

            double avgBiomass = parcelResults.stream()
                    .filter(pr -> pr.getBiomassKgPerHa() != null)
                    .mapToDouble(ProcessedParcelNdviDto::getBiomassKgPerHa)
                    .average()
                    .orElse(0.0);

            // Eliminar calibración previa del terreno (mismo tipo)
            calibrationRepository.findByTerrainIdAndParcelIdIsNullAndCalibrationType(terrainId, calibrationType)
                    .ifPresent(calibrationRepository::delete);

            NdviCalibration cal = NdviCalibration.builder()
                    .terrain(terrain)
                    .parcel(null)
                    .calibrationDate(calibrationDate)
                    .calibrationType(calibrationType)
                    .referenceNdvi(Math.round(avgNdvi * 10000.0) / 10000.0)
                    .referenceBiomass(Math.round(avgBiomass * 100.0) / 100.0)
                    .pastureType(farm.getPastureType())
                    .source(usedSource)
                    .sceneId(usedSceneId)
                    .cloudCoverPercent(usedCloudCover)
                    .build();

            savedCalibrations.add(calibrationRepository.save(cal));
            log.info("Calibración homogénea guardada: terreno={} ndvi={} biomasa={}", terrainId, avgNdvi, avgBiomass);

        } else {
            // Finca no homogénea: calibración por parcela, reutilizando por tipo de pasto
            Map<Long, ProcessedParcelNdviDto> resultsByParcelId = parcelResults.stream()
                    .filter(pr -> pr.getParcelId() != null)
                    .collect(Collectors.toMap(ProcessedParcelNdviDto::getParcelId, pr -> pr, (a, b) -> b));

            // Agrupar parcelas por tipo de pasto
            Map<String, List<Parcel>> parcelsByPasture = new LinkedHashMap<>();
            List<Parcel> noPastureType = new ArrayList<>();
            for (Parcel p : parcels) {
                if (p.getPastureType() != null && !p.getPastureType().isBlank()) {
                    parcelsByPasture.computeIfAbsent(p.getPastureType(), k -> new ArrayList<>()).add(p);
                } else {
                    noPastureType.add(p);
                }
            }

            // Para cada grupo de pasto, tomar el NDVI de la primera parcela con resultado como referencia
            Map<String, Double> referenceNdviByPasture = new LinkedHashMap<>();
            Map<String, Double> referenceBiomassByPasture = new LinkedHashMap<>();

            for (Map.Entry<String, List<Parcel>> entry : parcelsByPasture.entrySet()) {
                String pastureType = entry.getKey();
                for (Parcel p : entry.getValue()) {
                    ProcessedParcelNdviDto pr = resultsByParcelId.get(p.getId());
                    if (pr != null && pr.getMeanNdvi() != null && pr.getPixelCount() != null && pr.getPixelCount() > 0) {
                        referenceNdviByPasture.put(pastureType, pr.getMeanNdvi());
                        referenceBiomassByPasture.put(pastureType, pr.getBiomassKgPerHa());
                        break;
                    }
                }
            }

            // Guardar calibración para cada parcela
            for (Parcel p : parcels) {
                // Eliminar calibración previa de esta parcela (mismo tipo)
                calibrationRepository.findByParcelIdAndCalibrationType(p.getId(), calibrationType)
                        .ifPresent(calibrationRepository::delete);

                ProcessedParcelNdviDto pr = resultsByParcelId.get(p.getId());
                Double refNdvi = null;
                Double refBiomass = null;
                Integer pixelCount = null;

                if (pr != null && pr.getMeanNdvi() != null && pr.getPixelCount() != null && pr.getPixelCount() > 0) {
                    refNdvi = pr.getMeanNdvi();
                    refBiomass = pr.getBiomassKgPerHa();
                    pixelCount = pr.getPixelCount();
                } else if (p.getPastureType() != null && referenceNdviByPasture.containsKey(p.getPastureType())) {
                    // Reutilizar referencia del mismo tipo de pasto
                    refNdvi = referenceNdviByPasture.get(p.getPastureType());
                    refBiomass = referenceBiomassByPasture.get(p.getPastureType());
                }

                if (refNdvi != null) {
                    NdviCalibration cal = NdviCalibration.builder()
                            .terrain(terrain)
                            .parcel(p)
                            .calibrationDate(calibrationDate)
                            .calibrationType(calibrationType)
                            .referenceNdvi(Math.round(refNdvi * 10000.0) / 10000.0)
                            .referenceBiomass(refBiomass != null ? Math.round(refBiomass * 100.0) / 100.0 : null)
                            .pastureType(p.getPastureType())
                            .source(usedSource)
                            .sceneId(usedSceneId)
                            .cloudCoverPercent(usedCloudCover)
                            .pixelCount(pixelCount)
                            .build();

                    savedCalibrations.add(calibrationRepository.save(cal));
                    log.info("Calibración parcela guardada: parcel={} pastureType={} ndvi={}", p.getId(), p.getPastureType(), refNdvi);
                }
            }
        }

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
                .referenceBiomass(cal.getReferenceBiomass())
                .pastureType(cal.getPastureType())
                .source(cal.getSource())
                .sceneId(cal.getSceneId())
                .cloudCoverPercent(cal.getCloudCoverPercent())
                .pixelCount(cal.getPixelCount())
                .build();
    }
}
