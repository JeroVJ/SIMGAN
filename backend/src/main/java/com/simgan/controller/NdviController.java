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
@RequestMapping("/ndvi")
@RequiredArgsConstructor
@Slf4j
/**
 * Endpoints REST para analítica NDVI.
 *
 * Funcionalidades:
 * - Dashboard y timeline NDVI (terreno y por parcela).
 * - Ranking comparativo de parcelas.
 * - Recomendaciones de rotación basadas en umbrales/calibración NDVI.
 * - Ejecución manual de análisis NDVI por rango de fechas.
 * - Configuración/consulta de programación de análisis automático.
 * - Estado de conexiones satelitales (Planet/Sentinel) y metadatos de bandas.
 * - Estimación de días de pastoreo por parcela con lote activo.
 *
 * Valores usados en este controller:
 * - Fórmula NDVI: (NIR - Red) / (NIR + Red).
 * - Estimación de biomasa disponible: se descuenta 30% residual del total.
 * - Tasas de consumo diario (proporción del peso vivo): VACA=0.10, NOVILLA=0.12, NOVILLO=0.13, TORO=0.11.
 */
public class NdviController {

    private final NdviRecommendationService recommendationService;
  
    private final PlanetApiService planetApiService;
    private final SentinelApiService sentinelApiService;
    private final AnalysisOrchestrator analysisOrchestrator;
    private final NdviAnalysisScheduleService ndviAnalysisScheduleService;
    private final NdviRecordRepository ndviRecordRepository;
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

    @PostMapping("/analyze/{terrainId}")
    /**
     * Ejecuta un análisis NDVI manual para un terreno en un rango de fechas.
     *
     * Parámetros:
     * - startDate/endDate: fechas ISO (YYYY-MM-DD).
     * - biomassMethod: método de biomasa (string) consumido por el orquestador; "DEFAULT" por defecto.
     */
    public ResponseEntity<Map<String, Object>> analyzeTerrain(
        @PathVariable Long terrainId,
        @RequestParam String startDate,
        @RequestParam String endDate,
        @RequestParam(required = false, defaultValue = "DEFAULT") String biomassMethod) {

    // Parse directo (sin try/catch): si el formato es inválido, el handler global devolverá 400.
    LocalDate start = LocalDate.parse(startDate);
    LocalDate end = LocalDate.parse(endDate);
    LocalDate today = LocalDate.now();

    // Validaciones
    if (start.isAfter(end)) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", "La fecha inicial no puede ser mayor que la fecha final."
        ));
    }

    if (end.isAfter(today)) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", "La fecha final no puede ser mayor que hoy."
        ));
    }

    log.info("Iniciando análisis NDVI para terreno {} en rango {} -> {} con método biomasa={}",
            terrainId, start, end, biomassMethod);

    Map<String, Object> result =
            analysisOrchestrator.runAnalysis(terrainId, start, end, biomassMethod);

    return ResponseEntity.ok(result);
    
  }

    @GetMapping("/schedule/{terrainId}")
    /**
     * Consulta la programación de análisis automático NDVI del terreno.
     */
    public ResponseEntity<Map<String, Object>> getAnalysisSchedule(@PathVariable Long terrainId) {
        return ResponseEntity.ok(ndviAnalysisScheduleService.getSchedule(terrainId));
    }

    @PostMapping("/schedule/{terrainId}")
    /**
     * Configura la programación de análisis automático NDVI (cada N días).
     */
    public ResponseEntity<Map<String, Object>> configureAnalysisSchedule(
            @PathVariable Long terrainId,
            @RequestParam int days) {
        try {
            return ResponseEntity.ok(ndviAnalysisScheduleService.configureSchedule(terrainId, days));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
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
        status.put("alertThreshold", 0.1);
        status.put("optimalThreshold", 0.6);

        return ResponseEntity.ok(status);
    }

    /**
     * GET /api/ndvi/grazing-estimate/{terrainId}
     * Estimacion de dias de pastoreo por parcela con lote activo.
     * Formula: diasOcupacion = (biomasa_disponible_kg) / (consumo_MS_diario_total)
        * Consumo diario por tipo: vaca=10%, novilla=12%, novillo=13%, toro=11% del peso vivo
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
                double consumoDiarioMS = ganados.stream()
                        .mapToDouble(g -> {
                            Double peso = g.getPesoActual();
                            if (peso == null || peso <= 0) return 0.0;
                            return peso * getForageRateByType(g.getTipo());
                        })
                        .sum();
                int estimatedDays = consumoDiarioMS > 0 ? (int) Math.floor(available / consumoDiarioMS) : 0;

                entry.put("totalBiomassKg", Math.round(totalBiomass));
                entry.put("availableBiomassKg", Math.round(available));
                entry.put("dailyConsumptionKg", Math.round(consumoDiarioMS * 10.0) / 10.0);
                entry.put("estimatedDays", Math.max(0, estimatedDays));

                // Alerta simple: si quedan 3 días o menos de forraje, sugerir rotación.
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

    private double getForageRateByType(Ganado.TipoGanado tipo) {
        if (tipo == null) return 0.10;
        return switch (tipo) {
            case VACA -> 0.10;
            case NOVILLA -> 0.12;
            case NOVILLO -> 0.13;
            case TORO -> 0.11;
        };
    }

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

}
