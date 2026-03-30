package com.simgan.service;

import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;



 //BORRAR SERVICE

 
/**
 * Genera datos NDVI realistas para demostración.
 *
 * Simula un ciclo de pastoreo rotacional con:
 * - Parcelas en uso → NDVI decrece gradualmente (pastoreo)
 * - Parcelas en descanso → NDVI incrementa (recuperación)
 * - Parcelas disponibles → NDVI estable/alto
 *
 * Basado en dinámicas reales de pasturas tropicales Brachiaria/Estrella
 * en el trópico colombiano (8-30°C, 1000-3000mm precip anual).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NdviSeedService {

    private final NdviRecordRepository ndviRecordRepository;
    private final ParcelRepository parcelRepository;
    private final TerrainRepository terrainRepository;
    private final NdviProcessingService processingService;
    private final RotationHistoryRepository rotationHistoryRepository;

    @Value("${ndvi.seed.months:6}")
    private int seedMonths;

    private final Random random = new Random(42);

    /**
     * Genera datos seed para un terreno especifico
     */
    public int seedTerrainData(Long terrainId) {
        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado"));

        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
        if (parcels.isEmpty()) {
            log.warn("No hay parcelas para generar seed data en terreno {}", terrainId);
            return 0;
        }

        // Check if already has data
        if (ndviRecordRepository.countByTerrainId(terrainId) > 0) {
            log.info("Terreno {} ya tiene datos NDVI, saltando seed", terrainId);
            return 0;
        }

        int count = 0;
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(seedMonths);

        for (Parcel parcel : parcels) {
            count += seedParcelData(parcel, terrain, startDate, endDate);
        }

        log.info("Seed completado: {} registros NDVI para terreno {} ({} parcelas)",
                count, terrainId, parcels.size());
        return count;
    }

    private int seedParcelData(Parcel parcel, Terrain terrain, LocalDate start, LocalDate end) {
        int count = 0;

        // Determine parcel behavior profile
        ParcelProfile profile = getProfile(parcel);

        // Generate data every 5 days (PlanetScope revisit frequency)
        LocalDate date = start;
        double currentNdvi = profile.baseNdvi;
        Parcel.ParcelStatus simStatus = Parcel.ParcelStatus.DISPONIBLE;
        int daysInStatus = 0;

        while (!date.isAfter(end)) {
            // Simulate rotation events
            daysInStatus += 5;

            if (simStatus == Parcel.ParcelStatus.DISPONIBLE && daysInStatus > 20 + random.nextInt(15)) {
                simStatus = Parcel.ParcelStatus.EN_USO;
                daysInStatus = 0;
            } else if (simStatus == Parcel.ParcelStatus.EN_USO && daysInStatus > 15 + random.nextInt(10)) {
                simStatus = Parcel.ParcelStatus.EN_DESCANSO;
                daysInStatus = 0;
            } else if (simStatus == Parcel.ParcelStatus.EN_DESCANSO && daysInStatus > 18 + random.nextInt(12)) {
                simStatus = Parcel.ParcelStatus.DISPONIBLE;
                daysInStatus = 0;
            }

            // Calculate NDVI based on status
            switch (simStatus) {
                case EN_USO:
                    currentNdvi -= 0.015 + random.nextDouble() * 0.01; // Decreasing (grazing)
                    break;
                case EN_DESCANSO:
                    currentNdvi += 0.012 + random.nextDouble() * 0.008; // Increasing (recovery)
                    break;
                case DISPONIBLE:
                    currentNdvi += (random.nextDouble() - 0.4) * 0.005; // Stable with small changes
                    break;
            }

            // Add seasonal variation (wet/dry season Colombia)
            int month = date.getMonthValue();
            double seasonal = getSeasonalFactor(month);
            currentNdvi += seasonal * 0.003;

            // Add noise
            currentNdvi += (random.nextGaussian()) * 0.015;

            // Clamp to realistic pasture range
            currentNdvi = Math.max(0.10, Math.min(0.85, currentNdvi));

            // Skip some dates (cloud cover)
            if (random.nextDouble() < 0.25) {
                date = date.plusDays(5);
                continue;
            }

            // Generate NDVI values around the mean
            List<Double> pixelValues = generatePixelValues(currentNdvi, 200 + random.nextInt(300));

            NdviRecord record = processingService.buildNdviRecord(
                    pixelValues, parcel, terrain, date, "SEED-" + date, "SEED");

            record.setCloudCoverPercent(random.nextDouble() * 20);
            ndviRecordRepository.save(record);
            processingService.checkAndCreateAlerts(record);

            count++;
            date = date.plusDays(5);
        }

        // Log some rotation history
        seedRotationHistory(parcel);

        return count;
    }

    private void seedRotationHistory(Parcel parcel) {
        LocalDate date = LocalDate.now().minusMonths(seedMonths);
        Parcel.ParcelStatus[] cycle = {
                Parcel.ParcelStatus.DISPONIBLE,
                Parcel.ParcelStatus.EN_USO,
                Parcel.ParcelStatus.EN_DESCANSO
        };

        Parcel.ParcelStatus prev = null;
        for (int i = 0; i < 6 + random.nextInt(4); i++) {
            Parcel.ParcelStatus status = cycle[i % 3];
            date = date.plusDays(12 + random.nextInt(18));
            if (date.isAfter(LocalDate.now())) break;

            RotationHistory history = RotationHistory.builder()
                    .parcel(parcel)
                    .previousStatus(prev)
                    .newStatus(status)
                    .ndviAtChange(0.3 + random.nextDouble() * 0.4)
                    .biomassAtChange(1000 + random.nextDouble() * 4000)
                    .build();

            rotationHistoryRepository.save(history);
            prev = status;
        }
    }

    private List<Double> generatePixelValues(double mean, int count) {
        List<Double> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double val = mean + random.nextGaussian() * 0.08;
            val = Math.max(-0.1, Math.min(1.0, val));
            values.add(val);
        }
        return values;
    }

    /**
     * Seasonal factor for Colombian tropics
     * Wet season: Apr-May, Oct-Nov (higher NDVI)
     * Dry season: Dec-Mar, Jun-Sep (lower NDVI)
     */
    private double getSeasonalFactor(int month) {
        return switch (month) {
            case 1, 2 -> -0.8;    // Dry
            case 3 -> -0.4;       // Transitioning
            case 4, 5 -> 0.8;     // Wet
            case 6, 7 -> -0.3;    // Veranillo
            case 8, 9 -> -0.5;    // Dry
            case 10, 11 -> 0.9;   // Wet
            case 12 -> -0.6;      // Dry starting
            default -> 0;
        };
    }

    private ParcelProfile getProfile(Parcel parcel) {
        // Assign different base profiles per parcel for variety
        long id = parcel.getId() != null ? parcel.getId() : 1;
        int profileIdx = (int) (id % 4);

        return switch (profileIdx) {
            case 0 -> new ParcelProfile(0.55, "Pastura establecida Brachiaria");
            case 1 -> new ParcelProfile(0.45, "Pastura mixta Estrella");
            case 2 -> new ParcelProfile(0.65, "Pastura nueva, alta densidad");
            case 3 -> new ParcelProfile(0.35, "Pastura degradada, requiere renovación");
            default -> new ParcelProfile(0.50, "Pastura estándar");
        };
    }

    private record ParcelProfile(double baseNdvi, String description) {}
}
