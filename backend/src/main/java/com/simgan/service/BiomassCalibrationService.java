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
    private final NdviCalibrationRepository ndviCalibrationRepository;
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

            boolean calibrated = model != null;
            if (calibrated) calibratedCount++;

            parcelResponses.add(BiomassCalibrationDto.ParcelBiomassCalibrationResponse.builder()
                    .parcelId(parcel.getId())
                    .parcelName(parcel.getName())
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
     * 4. Runs linear regression: biomass = a * NDVI + b
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

        if (sceneId == null) {
            throw new RuntimeException("No se encontró una calibración NDVI óptima con escena válida. Ejecuta primero la calibración NDVI óptima.");
        }

        // Delete previous calibration data for this parcel
        pointRepository.deleteByParcelId(parcelId);
        modelRepository.deleteByParcelId(parcelId);

        // Calculate biomass (kg/ha) for each point and save
        List<BiomassCalibrationPoint> savedPoints = new ArrayList<>();
        List<PointNdviDto.PointNdviInput> ndviInputs = new ArrayList<>();

        for (BiomassCalibrationDto.SamplePointInput input : inputPoints) {
            double biomassKgPerHa = (input.getGreenWeightKg() / input.getCutAreaM2()) * 10000.0;

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
                    .build();
            savedPoints.add(pointRepository.save(point));

            ndviInputs.add(PointNdviDto.PointNdviInput.builder()
                    .pointIndex(input.getPointIndex())
                    .latitude(input.getLatitude())
                    .longitude(input.getLongitude())
                    .areaM2(input.getCutAreaM2())
                    .build());
        }

        // Call Processing service to compute NDVI at each point
        log.info("Enviando {} puntos a Processing para NDVI sceneId={}", ndviInputs.size(), sceneId);
        PointNdviDto.PointNdviResponse ndviResponse = imageProcessingClient.computePointNdvi(sceneId, downloadUrl, ndviInputs);

        // Update points with NDVI values
        Map<Integer, Double> ndviByIndex = new HashMap<>();
        if (ndviResponse.getResults() != null) {
            for (PointNdviDto.PointNdviResult r : ndviResponse.getResults()) {
                if (r.getNdvi() != null) {
                    ndviByIndex.put(r.getPointIndex(), r.getNdvi());
                }
            }
        }

        for (BiomassCalibrationPoint point : savedPoints) {
            Double ndvi = ndviByIndex.get(point.getPointIndex());
            if (ndvi != null) {
                point.setNdviAtPoint(ndvi);
                pointRepository.save(point);
            }
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

        // Linear regression: biomass = a * NDVI + b
        double[] regression = linearRegression(pairs);
        double a = regression[0];
        double b = regression[1];
        double rSquared = regression[2];

        BiomassCalibrationModel model = BiomassCalibrationModel.builder()
                .parcel(parcel)
                .terrain(terrain)
                .coefficientA(Math.round(a * 10000.0) / 10000.0)
                .coefficientB(Math.round(b * 10000.0) / 10000.0)
                .rSquared(Math.round(rSquared * 10000.0) / 10000.0)
                .sampleCount(pairs.size())
                .sceneId(sceneId)
                .calibrationDate(calibrationDate)
                .build();

        model = modelRepository.save(model);

        log.info("Calibración biomasa completada parcelId={} a={} b={} R²={} muestras={}",
                parcelId, a, b, rSquared, pairs.size());

        return BiomassCalibrationDto.CalibrateBiomassResponse.builder()
                .parcelId(parcelId)
                .parcelName(parcel.getName())
                .points(savedPoints.stream().map(this::toPointResponse).collect(Collectors.toList()))
                .model(toModelResponse(model))
                .message(String.format(
                        "Calibración completada. Modelo: biomasa = %.2f × NDVI + %.2f (R² = %.4f, %d muestras)",
                        a, b, rSquared, pairs.size()))
                .build();
    }

    /**
     * Simple linear regression: y = a*x + b
     * Returns [a, b, rSquared]
     */
    private double[] linearRegression(List<double[]> pairs) {
        int n = pairs.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;

        for (double[] pair : pairs) {
            double x = pair[0]; // NDVI
            double y = pair[1]; // biomass
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            sumY2 += y * y;
        }

        double meanX = sumX / n;
        double meanY = sumY / n;

        double denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1e-10) {
            // All X values are the same — can't fit a line
            return new double[]{0.0, meanY, 0.0};
        }

        double a = (n * sumXY - sumX * sumY) / denominator;
        double b = meanY - a * meanX;

        // R²
        double ssRes = 0.0;
        double ssTot = 0.0;
        for (double[] pair : pairs) {
            double x = pair[0];
            double y = pair[1];
            double predicted = a * x + b;
            ssRes += (y - predicted) * (y - predicted);
            ssTot += (y - meanY) * (y - meanY);
        }

        double rSquared = ssTot > 0 ? 1.0 - (ssRes / ssTot) : 0.0;

        return new double[]{a, b, rSquared};
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
                .build();
    }
}
