package com.simgan.controller;

import com.simgan.dto.NdviDto;
import com.simgan.entity.*;
import com.simgan.repository.*;
import com.simgan.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ndvi")
@RequiredArgsConstructor
@Slf4j
public class NdviController {

    private final NdviRecommendationService recommendationService;
    private final NdviProcessingService processingService;
    private final NdviSeedService seedService;
    private final PlanetApiService planetApiService;
    private final SentinelApiService sentinelApiService;
    private final AnalysisOrchestrator analysisOrchestrator;
    private final NdviRecordRepository ndviRecordRepository;
    private final NdviAlertRepository alertRepository;
    private final LoteRepository loteRepository;
    private final ParcelRepository parcelRepository;
    private final GanadoRepository ganadoRepository;

    /**
     * GET /api/ndvi/dashboard/{terrainId}
     * Dashboard completo con NDVI, biomasa, alertas, timeline
     */
    @GetMapping("/dashboard/{terrainId}")
    public ResponseEntity<NdviDto.TerrainDashboard> getDashboard(@PathVariable Long terrainId) {
        return ResponseEntity.ok(recommendationService.getDashboard(terrainId));
    }

    /**
     * GET /api/ndvi/timeline/{terrainId}
     * Timeline de NDVI para graficas historicas
     */
    @GetMapping("/timeline/{terrainId}")
    public ResponseEntity<List<NdviDto.TimelinePoint>> getTimeline(
            @PathVariable Long terrainId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        if (start != null && end != null) {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);
            List<NdviRecord> records = ndviRecordRepository
                    .findByTerrainIdAndCaptureDateBetweenOrderByCaptureDate(terrainId, startDate, endDate);
            return ResponseEntity.ok(records.stream().map(this::toTimelinePoint).collect(Collectors.toList()));
        }

        return ResponseEntity.ok(recommendationService.getTerrainTimeline(terrainId));
    }

    /**
     * GET /api/ndvi/parcel/{parcelId}/timeline
     * Timeline de una parcela especifica
     */
    @GetMapping("/parcel/{parcelId}/timeline")
    public ResponseEntity<List<NdviDto.TimelinePoint>> getParcelTimeline(@PathVariable Long parcelId) {
        List<NdviRecord> records = ndviRecordRepository.findByParcelIdOrderByCaptureDate(parcelId);
        return ResponseEntity.ok(records.stream().map(this::toTimelinePoint).collect(Collectors.toList()));
    }

    /**
     * GET /api/ndvi/comparison/{terrainId}
     * Tabla comparativa de parcelas (ranking)
     */
    @GetMapping("/comparison/{terrainId}")
    public ResponseEntity<List<NdviDto.ParcelComparison>> getComparison(@PathVariable Long terrainId) {
        return ResponseEntity.ok(recommendationService.getParcelComparison(terrainId));
    }

    /**
     * GET /api/ndvi/recommendations/{terrainId}
     * Recomendaciones de rotación basadas en NDVI
     */
    @GetMapping("/recommendations/{terrainId}")
    public ResponseEntity<List<NdviDto.RotationRecommendation>> getRecommendations(@PathVariable Long terrainId) {
        return ResponseEntity.ok(recommendationService.getRotationRecommendations(terrainId));
    }

    /**
     * GET /api/ndvi/history/{terrainId}
     * Historial de rotaciones
     */
    @GetMapping("/history/{terrainId}")
    public ResponseEntity<List<NdviDto.RotationHistoryEntry>> getRotationHistory(@PathVariable Long terrainId) {
        return ResponseEntity.ok(recommendationService.getRotationHistory(terrainId));
    }

    /**
     * GET /api/ndvi/alerts/{terrainId}
     * Alertas activas
     */
    @GetMapping("/alerts/{terrainId}")
    public ResponseEntity<List<NdviDto.AlertResponse>> getAlerts(@PathVariable Long terrainId) {
        return ResponseEntity.ok(
                alertRepository.findByParcelTerrainIdOrderByCreatedAtDesc(terrainId).stream()
                        .map(this::toAlertResponse)
                        .collect(Collectors.toList()));
    }

    /**
     * PATCH /api/ndvi/alerts/{alertId}/acknowledge
     * Marcar alerta como leída
     */
    @PatchMapping("/alerts/{alertId}/acknowledge")
    public ResponseEntity<Void> acknowledgeAlert(@PathVariable Long alertId) {
        NdviAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alerta no encontrada"));
        alert.setAcknowledged(true);
        alert.setAcknowledgedAt(java.time.LocalDateTime.now());
        alertRepository.save(alert);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/ndvi/analyze/{terrainId}
     * Ejecuta pipeline completo: Planet → Sentinel → Seed data
     */
    @PostMapping("/analyze/{terrainId}")
    public ResponseEntity<Map<String, Object>> analyzeTerrain(@PathVariable Long terrainId) {
        log.info("Iniciando análisis NDVI para terreno {}", terrainId);
        Map<String, Object> result = analysisOrchestrator.runAnalysis(terrainId);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/ndvi/planet/status
     * Estado de las conexiones satelitales
     */
    @GetMapping("/planet/status")
    public ResponseEntity<Map<String, Object>> getSatelliteStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        // Planet Labs
        Map<String, Object> planet = new LinkedHashMap<>();
        planet.put("configured", planetApiService.isConfigured());
        planet.put("itemType", "PSScene (PlanetScope 3-5m diario)");
        planet.put("assetType", "ortho_analytic_4b_sr (Surface Reflectance)");
        planet.put("bands", "Blue, Green, Red, NIR");
        planet.put("resolution", "3-5m");
        status.put("planet", planet);

        // Sentinel-2
        Map<String, Object> sentinel = new LinkedHashMap<>();
        sentinel.put("configured", sentinelApiService.isConfigured());
        sentinel.put("collection", "SENTINEL-2 L2A");
        sentinel.put("bands", "B04 (Red 665nm), B08 (NIR 842nm)");
        sentinel.put("resolution", "10m");
        sentinel.put("revisit", "5 días");
        sentinel.put("cost", "Gratuito (ESA/Copernicus)");
        sentinel.put("stacEndpoint", "catalogue.dataspace.copernicus.eu");
        status.put("sentinel", sentinel);

        status.put("ndviFormula", "(NIR - Red) / (NIR + Red)");
        status.put("alertThreshold", 0.3);
        status.put("optimalThreshold", 0.6);

        return ResponseEntity.ok(status);
    }

    /**
     * GET /api/ndvi/grazing-estimate/{terrainId}
     * Estimacion de dias de pastoreo por parcela con lote activo.
     * Formula: diasOcupacion = (biomasa_disponible_kg) / (consumo_MS_diario_total)
     * Consumo MS diario = 2.5% peso vivo * N cabezas
     * Biomasa disponible = (biomasa_total - 30% residual)
     */
    @GetMapping("/grazing-estimate/{terrainId}")
    public ResponseEntity<List<Map<String, Object>>> getGrazingEstimate(@PathVariable Long terrainId) {
        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
        List<Map<String, Object>> results = new ArrayList<>();

        for (Parcel parcel : parcels) {
            List<Lote> occupying = loteRepository.findByCurrentParcelId(parcel.getId());
            Lote activeLote = occupying.stream()
                    .filter(l -> l.getFechaSalida() == null)
                    .findFirst().orElse(null);
            if (activeLote == null) continue;

            // Get latest NDVI/biomass
            Optional<NdviRecord> latestNdvi = ndviRecordRepository
                    .findFirstByParcelIdOrderByCaptureDateDesc(parcel.getId());
            Double biomassKgPerHa = latestNdvi.map(NdviRecord::getBiomassKgPerHa).orElse(null);
            Double ndvi = latestNdvi.map(NdviRecord::getMeanNdvi).orElse(null);

            // Get ganado data for the lote
            List<Ganado> ganados = ganadoRepository.findByLoteIdOrderByNumeracion(activeLote.getId());
            if (ganados.isEmpty()) continue;

            double pesoPromedio = ganados.stream().mapToDouble(Ganado::getPesoActual).average().orElse(0);
            int cabezas = ganados.size();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("parcelId", parcel.getId());
            entry.put("parcelName", parcel.getName());
            entry.put("areaHectares", parcel.getAreaHectares());
            entry.put("loteId", activeLote.getId());
            entry.put("loteName", activeLote.getName());
            entry.put("cabezas", cabezas);
            entry.put("pesoPromedio", Math.round(pesoPromedio * 10.0) / 10.0);
            entry.put("ndvi", ndvi);
            entry.put("biomassKgPerHa", biomassKgPerHa);

            if (biomassKgPerHa != null && parcel.getAreaHectares() != null) {
                double totalBiomass = biomassKgPerHa * parcel.getAreaHectares();
                double residual = totalBiomass * 0.30;
                double available = Math.max(0, totalBiomass - residual);
                double consumoDiarioMS = pesoPromedio * 0.025 * cabezas;
                int estimatedDays = consumoDiarioMS > 0 ? (int) Math.floor(available / consumoDiarioMS) : 0;

                entry.put("totalBiomassKg", Math.round(totalBiomass));
                entry.put("availableBiomassKg", Math.round(available));
                entry.put("dailyConsumptionKg", Math.round(consumoDiarioMS * 10.0) / 10.0);
                entry.put("estimatedDays", Math.max(0, estimatedDays));

                // Generate alert if days <= 3
                boolean needsAlert = estimatedDays <= 3;
                entry.put("alert", needsAlert);
                entry.put("alertMessage", needsAlert
                        ? (estimatedDays == 0
                            ? "Sin pasto disponible. Retire el lote inmediatamente."
                            : "Solo " + estimatedDays + " dia(s) de pasto. Considere rotar.")
                        : null);
            } else {
                entry.put("estimatedDays", null);
                entry.put("alert", ndvi != null && ndvi < 0.25);
                entry.put("alertMessage", ndvi != null && ndvi < 0.25
                        ? "NDVI critico (" + String.format("%.2f", ndvi) + "). Se recomienda descanso."
                        : null);
            }

            results.add(entry);
        }

        return ResponseEntity.ok(results);
    }

    // ===== HELPERS =====

    private NdviDto.TimelinePoint toTimelinePoint(NdviRecord r) {
        return NdviDto.TimelinePoint.builder()
                .date(r.getCaptureDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .meanNdvi(r.getMeanNdvi())
                .biomassKgPerHa(r.getBiomassKgPerHa())
                .vegetationCoverPercent(r.getVegetationCoverPercent())
                .parcelName(r.getParcel() != null ? r.getParcel().getName() : null)
                .parcelId(r.getParcel() != null ? r.getParcel().getId() : null)
                .build();
    }

    private NdviDto.AlertResponse toAlertResponse(NdviAlert alert) {
        return NdviDto.AlertResponse.builder()
                .id(alert.getId())
                .parcelId(alert.getParcel().getId())
                .parcelName(alert.getParcel().getName())
                .alertType(alert.getAlertType())
                .severity(alert.getSeverity())
                .threshold(alert.getThreshold())
                .currentValue(alert.getCurrentValue())
                .message(alert.getMessage())
                .acknowledged(alert.getAcknowledged())
                .createdAt(alert.getCreatedAt() != null ? alert.getCreatedAt().toString() : null)
                .build();
    }
}
