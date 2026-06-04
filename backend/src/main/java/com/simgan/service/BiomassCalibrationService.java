package com.simgan.service;

import com.simgan.dto.BiomassCalibrationDto;
import com.simgan.dto.PointNdviDto;
import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BiomassCalibrationService {

    private final BiomassCalibrationPointRepository pointRepository;
    private final BiomassCalibrationModelRepository modelRepository;
    private final NdviRecordRepository ndviRecordRepository;
    private final NdviCalibrationRepository ndviCalibrationRepository;
    private final NdviTerrainRecordRepository ndviTerrainRecordRepository;
    private final TerrainRepository terrainRepository;
    private final ParcelRepository parcelRepository;
    private final ImageProcessingClientService imageProcessingClient;

    private static final int MIN_POINTS = 7;

    /**
     * Returns the biomass calibration status for all parcels in a terrain.
     */
    public BiomassCalibrationDto.BiomassCalibrationStatus getStatus(Long terrainId) {
        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
        List<BiomassCalibrationModel> models = modelRepository.findByTerrainId(terrainId);
        Map<Long, BiomassCalibrationModel> modelByParcel = models.stream()
                .collect(Collectors.toMap(m -> m.getParcel().getId(), m -> m, (a, b) -> b));

        List<BiomassCalibrationDto.ParcelBiomassCalibrationResponse> parcelResponses = new ArrayList<>();
        int calibratedCount = 0;

        for (Parcel parcel : parcels) {
            BiomassCalibrationModel model = modelByParcel.get(parcel.getId());
            List<BiomassCalibrationPoint> points = pointRepository.findByParcelIdOrderByPointIndex(parcel.getId());

            // Auto-heal: always try to provide rSquared/formula in status response
            if (model != null && (model.getRSquared() == null || model.getFormula() == null)) {
                List<double[]> pairs = new ArrayList<>();
                for (BiomassCalibrationPoint pt : points) {
                    if (pt.getNdviAtPoint() != null && pt.getBiomassKgPerHa() != null) {
                        pairs.add(new double[]{pt.getNdviAtPoint(), pt.getBiomassKgPerHa()});
                    }
                }

                boolean changed = false;
                if (model.getRSquared() == null) {
                    if (pairs.size() >= 2) {
                        double[] reg = linearRegression(pairs);
                        model.setRSquared(Math.round(reg[2] * 10000.0) / 10000.0);
                    } else {
                        // Fallback for legacy records without enough valid NDVI pairs
                        model.setRSquared(0.0);
                    }
                    changed = true;
                }

                if (model.getFormula() == null
                        && model.getCoefficientA() != null) {
                    model.setFormula(String.format("Biomasa = %.2f × NDVI", model.getCoefficientA()));
                    changed = true;
                }

                if (changed) {
                    modelRepository.save(model);
                    log.info("Auto-healed rSquared/formula for parcelId={}", parcel.getId());
                }
            }

            boolean calibrated = model != null;
            if (calibrated) calibratedCount++;

            parcelResponses.add(BiomassCalibrationDto.ParcelBiomassCalibrationResponse.builder()
                    .parcelId(parcel.getId())
                    .parcelName(parcel.getName())
                    .geoJson(parcel.getGeoJson())
                    .calibrated(calibrated)
                    .points(points.stream().map(this::toPointResponse).collect(Collectors.toList()))
                    .model(model != null ? toModelResponse(model) : null)
                    .build());
        }

        return BiomassCalibrationDto.BiomassCalibrationStatus.builder()
                .terrainId(terrainId)
                .totalParcels(parcels.size())
                .calibratedParcels(calibratedCount)
                .allCalibrated(calibratedCount >= parcels.size() && !parcels.isEmpty())
                .parcels(parcelResponses)
                .build();
    }

    /**
     * Runs the biomass calibration for a single parcel:
     * 1. Validates at least MIN_POINTS sample points
     * 2. Calculates biomass (kg/ha) from weight and area
     * 3. Sends points to Processing service to get NDVI at each point
    * 4. Runs linear regression through origin: biomass = a * NDVI
     * 5. Saves points and model
     */
    @Transactional
    public BiomassCalibrationDto.CalibrateBiomassResponse calibrateParcel(
            Long terrainId, Long parcelId,
            List<BiomassCalibrationDto.SamplePointInput> inputPoints) {

        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new RuntimeException("Parcela no encontrada: " + parcelId));

        if (inputPoints == null || inputPoints.size() < MIN_POINTS) {
            throw new RuntimeException(
                    String.format("Se requieren al menos %d puntos de muestreo. Se recibieron %d.",
                            MIN_POINTS, inputPoints == null ? 0 : inputPoints.size()));
        }

        // Find the NDVI optimal calibration to get the scene used
        List<NdviCalibration> optimCalibrations = ndviCalibrationRepository
                .findByTerrainIdAndCalibrationType(terrainId, "OPTIM");

        String sceneId = null;
        String downloadUrl = null;
        LocalDate calibrationDate = null;

        // Try parcel-specific calibration first, then terrain-level
        for (NdviCalibration cal : optimCalibrations) {
            if (cal.getParcel() != null && cal.getParcel().getId().equals(parcelId)) {
                sceneId = cal.getSceneId();
                calibrationDate = cal.getCalibrationDate();
                break;
            }
        }
        if (sceneId == null) {
            for (NdviCalibration cal : optimCalibrations) {
                if (cal.getSceneId() != null) {
                    sceneId = cal.getSceneId();
                    calibrationDate = cal.getCalibrationDate();
                    break;
                }
            }
        }

        // Fallback: la calibración automática deja la fila OPTIM sin sceneId (usa muchas
        // escenas). En ese caso usamos la escena más reciente que procesó como referencia
        // para calcular el NDVI en los puntos de muestreo.
        if (sceneId == null && !optimCalibrations.isEmpty()) {
            var latest = ndviTerrainRecordRepository
                    .findFirstByTerrainIdAndSceneIdIsNotNullOrderByCaptureDateDesc(terrainId);
            if (latest.isPresent()) {
                sceneId = latest.get().getSceneId();
                calibrationDate = latest.get().getCaptureDate();
            }
        }

        if (sceneId == null) {
            throw new RuntimeException("No se encontró una calibración NDVI óptima con escena válida. Ejecuta primero la calibración NDVI óptima.");
        }

        // ─── Reuse existing NDVI values by coordinate ─────────────────────────────
        Map<String, Double> cachedNdvi = new HashMap<>();
        for (BiomassCalibrationPoint existing : pointRepository.findByParcelIdOrderByPointIndex(parcelId)) {
            if (existing.getNdviAtPoint() != null) {
                String key = String.format("%.6f,%.6f", existing.getLatitude(), existing.getLongitude());
                cachedNdvi.put(key, existing.getNdviAtPoint());
            }
        }

        // Delete previous points
        pointRepository.deleteByParcelId(parcelId);

        List<BiomassCalibrationPoint> savedPoints = new ArrayList<>();
        List<PointNdviDto.PointNdviInput> ndviInputs = new ArrayList<>();

        for (BiomassCalibrationDto.SamplePointInput input : inputPoints) {
            double biomassKgPerHa = (input.getGreenWeightKg() / input.getCutAreaM2()) * 10000.0;
            String coordKey = String.format("%.6f,%.6f", input.getLatitude(), input.getLongitude());
            Double reusedNdvi = cachedNdvi.get(coordKey);

            BiomassCalibrationPoint point = BiomassCalibrationPoint.builder()
                    .parcel(parcel)
                    .terrain(terrain)
                    .pointIndex(input.getPointIndex())
                    .latitude(input.getLatitude())
                    .longitude(input.getLongitude())
                    .cutAreaM2(input.getCutAreaM2())
                    .greenWeightKg(input.getGreenWeightKg())
                    .biomassKgPerHa(Math.round(biomassKgPerHa * 100.0) / 100.0)
                    .sceneId(sceneId)
                    .ndviAtPoint(reusedNdvi)
                    .build();
            savedPoints.add(pointRepository.save(point));

            if (reusedNdvi == null) {
                ndviInputs.add(PointNdviDto.PointNdviInput.builder()
                        .pointIndex(input.getPointIndex())
                        .latitude(input.getLatitude())
                        .longitude(input.getLongitude())
                        .areaM2(input.getCutAreaM2())
                        .build());
            }
        }

        // Call Processing only for new coordinates without cached NDVI
        if (!ndviInputs.isEmpty()) {
            log.info("Consultando NDVI en Processing para {} puntos nuevos (sceneId={})", ndviInputs.size(), sceneId);
            PointNdviDto.PointNdviResponse ndviResponse = imageProcessingClient.computePointNdvi(sceneId, downloadUrl, ndviInputs);
            Map<Integer, Double> ndviByIndex = new HashMap<>();
            if (ndviResponse.getResults() != null) {
                for (PointNdviDto.PointNdviResult r : ndviResponse.getResults()) {
                    if (r.getNdvi() != null) {
                        ndviByIndex.put(r.getPointIndex(), r.getNdvi());
                    }
                }
            }
            for (BiomassCalibrationPoint point : savedPoints) {
                if (point.getNdviAtPoint() == null) {
                    Double ndvi = ndviByIndex.get(point.getPointIndex());
                    if (ndvi != null) {
                        point.setNdviAtPoint(ndvi);
                        pointRepository.save(point);
                    }
                }
            }
        } else {
            log.info("Todos los puntos tienen NDVI cacheado, omitiendo llamada a Processing.");
        }

        // Build (NDVI, biomass) pairs for regression
        List<double[]> pairs = new ArrayList<>();
        for (BiomassCalibrationPoint point : savedPoints) {
            if (point.getNdviAtPoint() != null && point.getBiomassKgPerHa() != null) {
                pairs.add(new double[]{point.getNdviAtPoint(), point.getBiomassKgPerHa()});
            }
        }

        if (pairs.size() < 2) {
            throw new RuntimeException("No hay suficientes puntos con NDVI válido para construir el modelo de regresión (" + pairs.size() + " válidos).");
        }

        // Linear regression through origin: biomass = a * NDVI
        double[] regression = linearRegression(pairs);
        double a = regression[0];
        double rSquared = regression[2];

        BiomassCalibrationModel model = modelRepository.findByParcelId(parcelId)
                .orElse(BiomassCalibrationModel.builder()
                        .parcel(parcel)
                        .terrain(terrain)
                        .build());

        model.setCoefficientA(Math.round(a * 10000.0) / 10000.0);
        model.setCoefficientB(0.0);
        model.setRSquared(Math.round(rSquared * 10000.0) / 10000.0);
        model.setSampleCount(pairs.size());
        model.setSceneId(sceneId);
        model.setCalibrationDate(calibrationDate);
        model.setFormula(String.format("Biomasa = %.2f × NDVI", a));
        model = modelRepository.save(model);
        backfillParcelNdviBiomass(parcelId, model);

        log.info("Calibración biomasa completada parcelId={} a={} R²={} muestras={}",
            parcelId, a, rSquared, pairs.size());

        return BiomassCalibrationDto.CalibrateBiomassResponse.builder()
                .parcelId(parcelId)
                .parcelName(parcel.getName())
                .points(savedPoints.stream().map(this::toPointResponse).collect(Collectors.toList()))
                .model(toModelResponse(model))
                .message(String.format(
                    "Calibración completada. Modelo: biomasa = %.2f × NDVI (R² = %.4f, %d muestras)",
                    a, rSquared, pairs.size()))
                .build();
    }

    /**
     * Simple linear regression through origin: y = a*x
     * Returns [a, b(=0), rSquared]
     */
    private double[] linearRegression(List<double[]> pairs) {
        int n = pairs.size();
        double sumY = 0, sumXY = 0, sumX2 = 0;

        for (double[] pair : pairs) {
            double x = pair[0]; // NDVI
            double y = pair[1]; // biomass
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double meanY = sumY / n;

        if (Math.abs(sumX2) < 1e-10) {
            // All X values are the same — can't fit a line
            return new double[]{0.0, 0.0, 0.0};
        }

        double a = sumXY / sumX2;

        // R²
        double ssRes = 0.0;
        double ssTot = 0.0;
        for (double[] pair : pairs) {
            double x = pair[0];
            double y = pair[1];
            double predicted = a * x;
            ssRes += (y - predicted) * (y - predicted);
            ssTot += (y - meanY) * (y - meanY);
        }

        double rSquared = ssTot > 0 ? 1.0 - (ssRes / ssTot) : 0.0;

        return new double[]{a, 0.0, rSquared};
    }

    private BiomassCalibrationDto.SamplePointResponse toPointResponse(BiomassCalibrationPoint point) {
        return BiomassCalibrationDto.SamplePointResponse.builder()
                .id(point.getId())
                .pointIndex(point.getPointIndex())
                .latitude(point.getLatitude())
                .longitude(point.getLongitude())
                .cutAreaM2(point.getCutAreaM2())
                .greenWeightKg(point.getGreenWeightKg())
                .biomassKgPerHa(point.getBiomassKgPerHa())
                .ndviAtPoint(point.getNdviAtPoint())
                .sceneId(point.getSceneId())
                .build();
    }

    private BiomassCalibrationDto.RegressionModelResponse toModelResponse(BiomassCalibrationModel model) {
        return BiomassCalibrationDto.RegressionModelResponse.builder()
                .id(model.getId())
                .parcelId(model.getParcel().getId())
                .parcelName(model.getParcel().getName())
                .coefficientA(model.getCoefficientA())
                .coefficientB(model.getCoefficientB())
                .rSquared(model.getRSquared())
                .sampleCount(model.getSampleCount())
                .sceneId(model.getSceneId())
                .calibrationDate(model.getCalibrationDate())
                .formula(model.getFormula())
                .build();
    }

    private void backfillParcelNdviBiomass(Long parcelId, BiomassCalibrationModel model) {
        if (parcelId == null || model == null || model.getCoefficientA() == null) return;

        List<NdviRecord> records = ndviRecordRepository.findByParcelIdOrderByCaptureDate(parcelId);
        if (records.isEmpty()) return;

        int updated = 0;
        for (NdviRecord record : records) {
            if (record.getMeanNdvi() == null) continue;

            double biomass = Math.max(0.0, model.getCoefficientA() * record.getMeanNdvi());
            double roundedBiomass = Math.round(biomass * 100.0) / 100.0;
            if (!Objects.equals(record.getBiomassKgPerHa(), roundedBiomass)) {
                record.setBiomassKgPerHa(roundedBiomass);
                updated++;
            }
        }

        if (updated > 0) {
            ndviRecordRepository.saveAll(records);
            log.info("Biomasa NDVI recalculada parcelId={} registrosActualizados={} coefficientA={}",
                    parcelId, updated, model.getCoefficientA());
        }
    }

}
