package com.simgan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.simgan.dto.NdviDto;
import com.simgan.entity.Alert;
import com.simgan.entity.Lote;
import com.simgan.entity.Parcel;
import com.simgan.entity.Terrain;
import com.simgan.repository.AlertRepository;
import com.simgan.repository.LoteRepository;
import com.simgan.repository.NdviRecordRepository;
import com.simgan.repository.ParcelRepository;
import com.simgan.repository.TerrainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfReportService {

    private final TerrainRepository terrainRepository;
    private final ParcelRepository parcelRepository;
    private final NdviRecordRepository ndviRecordRepository;
    private final NdviRecommendationService recommendationService;
    private final AlertRepository alertRepository;
    private final LoteRepository loteRepository;

    // ─── Brand colours ───────────────────────────────────────────────────────
    private static final DeviceRgb C_DARK_GREEN  = new DeviceRgb(21,  83,  46);
    private static final DeviceRgb C_MID_GREEN   = new DeviceRgb(22, 101,  52);
    private static final DeviceRgb C_LIGHT_GREEN = new DeviceRgb(134, 239, 172);
    private static final DeviceRgb C_BG          = new DeviceRgb(249, 250, 249);
    private static final DeviceRgb C_WHITE        = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb C_TEXT_DARK    = new DeviceRgb(17,  24,  39);
    private static final DeviceRgb C_TEXT_MUTED   = new DeviceRgb(107, 114, 128);
    private static final DeviceRgb C_BORDER       = new DeviceRgb(229, 231, 235);
    private static final DeviceRgb C_RED          = new DeviceRgb(239,  68,  68);
    private static final DeviceRgb C_ORANGE       = new DeviceRgb(249, 115,  22);
    private static final DeviceRgb C_AMBER        = new DeviceRgb(245, 158,  11);
    private static final DeviceRgb C_BLUE         = new DeviceRgb(59, 130, 246);
    private static final DeviceRgb C_MAP_BG       = new DeviceRgb(220, 240, 220);
    private static final DeviceRgb C_TERRAIN_FILL = new DeviceRgb(187, 222, 187);
    private static final DeviceRgb C_TERRAIN_STROKE = new DeviceRgb(34,  85,  34);

    private final ObjectMapper mapper = new ObjectMapper();

    // ─── Public API ───────────────────────────────────────────────────────────

    public byte[] generateTerrainReport(Long terrainId) {
        Terrain terrain = terrainRepository.findById(terrainId)
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado: " + terrainId));

        NdviDto.TerrainDashboard dashboard    = recommendationService.getDashboard(terrainId);
        List<NdviDto.ParcelComparison> comparison = recommendationService.getParcelComparison(terrainId);
        List<NdviDto.RotationRecommendation> recs = recommendationService.getRotationRecommendations(terrainId);
        List<NdviDto.RotationHistoryEntry> history = recommendationService.getRotationHistory(terrainId);
        List<Parcel> parcels = parcelRepository.findByTerrainId(terrainId);
        List<Alert> allAlerts = alertRepository.findByTerrainIdOrderByCreatedAtDesc(terrainId);
        List<Lote> lotes = loteRepository.findByTerrainIdAndFechaSalidaIsNullOrderByCreatedAtDesc(terrainId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf  = new PdfDocument(writer);
            Document doc     = new Document(pdf, PageSize.A4);
            doc.setMargins(0, 0, 0, 0);

            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            buildCoverPage(pdf, doc, terrain, dashboard, bold, regular);
            buildSummaryPage(pdf, doc, terrain, dashboard, bold, regular);
            buildMapPage(pdf, doc, terrain, parcels, comparison, bold, regular);
            buildParcelsTablePage(pdf, doc, terrain, parcels, comparison, bold, regular);
            buildAlertsAndRecsPage(pdf, doc, terrain, parcels, allAlerts, recs, bold, regular);
            buildHistoryAndRotationPage(pdf, doc, terrain, parcels, lotes, history, bold, regular);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generating PDF report for terrain {}", terrainId, e);
            throw new RuntimeException("Error generando el informe PDF", e);
        }
    }

    // ─── Page builders ───────────────────────────────────────────────────────

    /** Page 1 — Cover */
    private void buildCoverPage(PdfDocument pdf, Document doc,
                                 Terrain terrain, NdviDto.TerrainDashboard dashboard,
                                 PdfFont bold, PdfFont regular) throws Exception {

        PdfPage page = pdf.addNewPage(PageSize.A4);
        PdfCanvas cv = new PdfCanvas(page);
        float W = PageSize.A4.getWidth(), H = PageSize.A4.getHeight();

        // Full background
        cv.setFillColor(C_BG).rectangle(0, 0, W, H).fill();

        // Dark green top band
        float topBand = 220;
        cv.setFillColor(C_DARK_GREEN).rectangle(0, H - topBand, W, topBand).fill();

        // Decorative diagonal stripe
        cv.setFillColor(C_MID_GREEN)
          .moveTo(0, H - topBand + 30)
          .lineTo(W * 0.45f, H - topBand)
          .lineTo(W * 0.45f, H - topBand + 30)
          .closePath().fill();

        // Bottom accent line
        cv.setFillColor(C_LIGHT_GREEN).rectangle(0, 0, W, 6).fill();

        // SIMGAN title
        try (Canvas canvas = new Canvas(cv, new Rectangle(40, H - 160, W - 80, 120))) {
            canvas.add(new Paragraph("SIMGAN")
                    .setFont(bold).setFontSize(42).setFontColor(C_WHITE)
                    .setMargin(0));
            canvas.add(new Paragraph("Sistema de gestión de potreros en ganadería bovina rotativa de ceba")
                    .setFont(regular).setFontSize(13).setFontColor(C_LIGHT_GREEN)
                    .setMargin(0));
        }

        // White content card
        float cardTop = H - topBand - 30;
        float cardH   = cardTop - 80;
        cv.setFillColor(C_WHITE)
          .roundRectangle(36, cardTop - cardH, W - 72, cardH, 12).fill();
        cv.setStrokeColor(C_BORDER).setLineWidth(1)
          .roundRectangle(36, cardTop - cardH, W - 72, cardH, 12).stroke();

        try (Canvas canvas = new Canvas(cv, new Rectangle(60, cardTop - cardH + 20, W - 120, cardH - 40))) {
            canvas.add(new Paragraph("Informe de Analítica de Pasturas")
                    .setFont(bold).setFontSize(20).setFontColor(C_DARK_GREEN)
                    .setMarginBottom(20));

            addCoverField(canvas, "Finca", dashboard.getFarmName(), bold, regular);
            addCoverField(canvas, "Terreno", terrain.getName(), bold, regular);
            if (terrain.getFarm() != null) {
                addCoverField(canvas, "Departamento", nvl(terrain.getFarm().getDepartment()), bold, regular);
                addCoverField(canvas, "Municipio", nvl(terrain.getFarm().getMunicipality()), bold, regular);
            }
            addCoverField(canvas, "Área Total",
                    String.format("%.2f ha  (%d potreros)", dashboard.getTerrainAreaHa(),
                            dashboard.getParcels() != null ? dashboard.getParcels().size() : 0),
                    bold, regular);
            addCoverField(canvas, "Generado el",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    bold, regular);

            if (dashboard.getLastAnalysisDate() != null) {
                addCoverField(canvas, "Último Análisis", dashboard.getLastAnalysisDate(), bold, regular);
            }

            // NDVI pill
            String ndviText = dashboard.getAvgNdvi() != null
                    ? String.format("NDVI Promedio: %.3f", dashboard.getAvgNdvi()) : "Sin datos NDVI";
            DeviceRgb healthColor = ndviColor(dashboard.getAvgNdvi());

            canvas.add(new Paragraph("\n"));
            Paragraph pill = new Paragraph(ndviText)
                    .setFont(bold).setFontSize(14).setFontColor(C_WHITE)
                    .setBackgroundColor(healthColor)
                    .setPaddingLeft(14).setPaddingRight(14)
                    .setFixedLeading(28);
            canvas.add(pill);
        }

        // Footer
        try (Canvas canvas = new Canvas(cv, new Rectangle(40, 10, W - 80, 30))) {
            canvas.add(new Paragraph("Informe generado automáticamente por SIMGAN  ·  Página 1")
                    .setFont(regular).setFontSize(9).setFontColor(C_TEXT_MUTED)
                    .setTextAlignment(TextAlignment.CENTER));
        }
    }

    /** Page 2 — Summary + Farm info */
    private void buildSummaryPage(PdfDocument pdf, Document doc,
                                   Terrain terrain, NdviDto.TerrainDashboard dashboard,
                                   PdfFont bold, PdfFont regular) throws Exception {

        PdfPage page = pdf.addNewPage(PageSize.A4);
        PdfCanvas cv = new PdfCanvas(page);
        float W = PageSize.A4.getWidth(), H = PageSize.A4.getHeight();

        drawPageChrome(cv, bold, regular, W, H, "Resumen Ejecutivo", 2);

        float bodyTop = H - 80;
        float margin  = 36;
        float inner   = W - margin * 2;

        try (Canvas canvas = new Canvas(cv, new Rectangle(margin, 40, inner, bodyTop - 40))) {

            // ── NDVI metrics row ──
            canvas.add(sectionTitle("Indicadores Clave de Pastura", bold));

            float[] colW = {inner / 4f, inner / 4f, inner / 4f, inner / 4f};
            Table metrics = new Table(colW).setWidth(UnitValue.createPercentValue(100));

            addMetricCell(metrics, "NDVI Promedio",
                    fmt(dashboard.getAvgNdvi(), "%.3f"), ndviColor(dashboard.getAvgNdvi()), bold, regular);
            addMetricCell(metrics, "Biomasa Total",
                    dashboard.getTotalBiomassKg() != null
                            ? String.format("%.1f t ", dashboard.getTotalBiomassKg() / 1000) : "—",
                    C_MID_GREEN, bold, regular);
            addMetricCell(metrics, "Biomasa / ha",
                    fmt(dashboard.getAvgBiomassPerHa(), "%.0f kg/ha"), C_BLUE, bold, regular);
            /*
            addMetricCell(metrics, "Alertas Activas",
                    String.valueOf(dashboard.getActiveAlerts()),
                    dashboard.getActiveAlerts() > 0 ? C_RED : C_MID_GREEN, bold, regular); 

                    */
            canvas.add(metrics.setMarginBottom(20));

            // ── Farm info ──
            canvas.add(sectionTitle("Información de la Finca", bold));
            if (terrain.getFarm() != null) {
                var farm = terrain.getFarm();
                float[] fc = {inner * 0.35f, inner * 0.65f};
                Table info = new Table(fc).setWidth(UnitValue.createPercentValue(100));
                addInfoRow(info, "Nombre", nvl(farm.getName()), bold, regular);
                addInfoRow(info, "Departamento", nvl(farm.getDepartment()), bold, regular);
                addInfoRow(info, "Municipio", nvl(farm.getMunicipality()), bold, regular);
                addInfoRow(info, "Tipo de Suelo", nvl(farm.getSoilType()), bold, regular);
                addInfoRow(info, "Tipo de Pasto", nvl(farm.getPastureType()), bold, regular);
                addInfoRow(info, "IoT Activo", Boolean.TRUE.equals(farm.getIotEnabled()) ? "Sí" : "No", bold, regular);
                canvas.add(info.setMarginBottom(20));
            }

            // ── Terrain info ──
            canvas.add(sectionTitle("Información del Terreno", bold));
            float[] tc = {inner * 0.35f, inner * 0.65f};
            Table tInfo = new Table(tc).setWidth(UnitValue.createPercentValue(100));
            addInfoRow(tInfo, "Nombre del Terreno", terrain.getName(), bold, regular);
            addInfoRow(tInfo, "Área", String.format("%.4f ha (%.0f m²)",
                    terrain.getAreaHectares(), terrain.getAreaSqMeters()), bold, regular);
            addInfoRow(tInfo, "Número de Potreros",
                    String.valueOf(parcelRepository.findByTerrainId(terrain.getId()).size()), bold, regular);
            if (dashboard.getLastAnalysisDate() != null) {
                addInfoRow(tInfo, "Último Análisis NDVI", dashboard.getLastAnalysisDate(), bold, regular);
            }
            canvas.add(tInfo);
        }
    }

    /** Page 3 — Terrain + Parcels map */
    private void buildMapPage(PdfDocument pdf, Document doc,
                               Terrain terrain, List<Parcel> parcels,
                               List<NdviDto.ParcelComparison> comparison,
                               PdfFont bold, PdfFont regular) throws Exception {

        PdfPage page = pdf.addNewPage(PageSize.A4);
        PdfCanvas cv = new PdfCanvas(page);
        float W = PageSize.A4.getWidth(), H = PageSize.A4.getHeight();

        drawPageChrome(cv, bold, regular, W, H, "Mapa del Terreno y Potreros", 3);

        // Map canvas bounds
        float mapX = 36, mapY = 80, mapW = W - 72, mapH = H - 200;

        // ── Compute bounding box from all geometries ──
        List<List<double[]>> allRings = new ArrayList<>();
        List<double[]> terrainCoords = parseRing(terrain.getGeoJson());
        allRings.add(terrainCoords);
        Map<Long, List<double[]>> parcelRings = new LinkedHashMap<>();
        for (Parcel p : parcels) {
            List<double[]> ring = parseRing(p.getGeoJson());
            parcelRings.put(p.getId(), ring);
            allRings.add(ring);
        }

        double[] bbox = computeBoundingBox(allRings);
        double minLng = bbox[0], minLat = bbox[1], maxLng = bbox[2], maxLat = bbox[3];
        double lngSpan = maxLng - minLng;
        double latSpan = maxLat - minLat;
        if (lngSpan == 0) lngSpan = 0.001;
        if (latSpan == 0) latSpan = 0.001;

        // Aspect-correct scaling with padding
        float padFrac = 0.08f;
        float drawW = mapW * (1 - 2 * padFrac);
        float drawH = mapH * (1 - 2 * padFrac);
        float scaleX = (float) (drawW / lngSpan);
        float scaleY = (float) (drawH / latSpan);
        float scale  = Math.min(scaleX, scaleY);
        float offX   = mapX + (mapW - (float)(lngSpan * scale)) / 2f;
        float offY   = mapY + (mapH - (float)(latSpan * scale)) / 2f;

        // ── Map background ──
        cv.setFillColor(C_MAP_BG)
          .roundRectangle(mapX, mapY, mapW, mapH, 8).fill();
        cv.setStrokeColor(C_BORDER).setLineWidth(0.5f)
          .roundRectangle(mapX, mapY, mapW, mapH, 8).stroke();

        // ── Draw terrain fill ──
        if (!terrainCoords.isEmpty()) {
            cv.setFillColor(C_TERRAIN_FILL);
            drawPolygon(cv, terrainCoords, minLng, minLat, scale, offX, offY);
            cv.fill();
            cv.setStrokeColor(C_TERRAIN_STROKE).setLineWidth(2f);
            drawPolygon(cv, terrainCoords, minLng, minLat, scale, offX, offY);
            cv.stroke();
        }

        // Build quick-lookup from parcelId → comparison entry
        Map<Long, NdviDto.ParcelComparison> compMap = new HashMap<>();
        if (comparison != null) {
            comparison.forEach(c -> compMap.put(c.getParcelId(), c));
        }

        // ── Draw parcel fills ──
        for (Parcel p : parcels) {
            List<double[]> ring = parcelRings.get(p.getId());
            if (ring == null || ring.isEmpty()) continue;

            NdviDto.ParcelComparison comp = compMap.get(p.getId());
            DeviceRgb fillColor = parcelHealthColor(comp);

            cv.setFillColor(fillColor);
            drawPolygon(cv, ring, minLng, minLat, scale, offX, offY);
            cv.fill();

            cv.setStrokeColor(C_WHITE).setLineWidth(1.2f);
            drawPolygon(cv, ring, minLng, minLat, scale, offX, offY);
            cv.stroke();
        }

        // ── Parcel name labels ──
        for (Parcel p : parcels) {
            List<double[]> ring = parcelRings.get(p.getId());
            if (ring == null || ring.isEmpty()) continue;

            double[] centroid = centroid(ring);
            float cx = offX + (float)((centroid[0] - minLng) * scale);
            float cy = offY + (float)((centroid[1] - minLat) * scale);

            NdviDto.ParcelComparison comp = compMap.get(p.getId());
            String label = p.getName();
            if (comp != null && comp.getLatestNdvi() != null) {
                label += String.format("\n%.2f", comp.getLatestNdvi());
            }

            float lblW = 60, lblH = 22;
            try (Canvas lc = new Canvas(cv, new Rectangle(cx - lblW / 2f, cy - lblH / 2f, lblW, lblH))) {
                lc.add(new Paragraph(label)
                        .setFont(bold).setFontSize(7).setFontColor(C_TEXT_DARK)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setBackgroundColor(new DeviceRgb(255, 255, 255), 0.75f)
                        .setPadding(1));
            }
        }

        // ── Legend ──
        float legY = mapY - 2, legX = mapX;
        float itemW = 110, itemH = 18;
        String[][] legend = {
                {"#4ade80", "Excelente  (> umbral óptimo)"},
                {"#f59e0b", "Óptimo  (alerta–óptimo)"},
                {"#ef4444", "Crítico  (≤ umbral alerta)"},
                {"#9ca3af", "Sin datos"},
        };
        for (String[] entry : legend) {
            DeviceRgb lc = hexColor(entry[0]);
            cv.setFillColor(lc).rectangle(legX, legY - 10, 10, 10).fill();
            try (Canvas tc = new Canvas(cv, new Rectangle(legX + 13, legY - 12, itemW - 13, 14))) {
                tc.add(new Paragraph(entry[1])
                        .setFont(regular).setFontSize(8).setFontColor(C_TEXT_DARK));
            }
            legX += itemW;
        }
    }

    /** Page 4 — Parcels detail table */
    private void buildParcelsTablePage(PdfDocument pdf, Document doc,
                                        Terrain terrain, List<Parcel> parcels,
                                        List<NdviDto.ParcelComparison> comparison,
                                        PdfFont bold, PdfFont regular) throws Exception {

        PdfPage page = pdf.addNewPage(PageSize.A4);
        PdfCanvas cv = new PdfCanvas(page);
        float W = PageSize.A4.getWidth(), H = PageSize.A4.getHeight();

        drawPageChrome(cv, bold, regular, W, H, "Estado Detallado de Potreros", 4);

        float margin = 36;
        float inner  = W - margin * 2;

        // Build parcel threshold map for health classification
        Map<Long, double[]> parcelThresholds = new LinkedHashMap<>();
        for (Parcel p : parcels) {
            parcelThresholds.put(p.getId(),
                    recommendationService.getThresholds(p.getId(), terrain.getId()));
        }

        try (Canvas canvas = new Canvas(cv, new Rectangle(margin, 40, inner, H - 100))) {

            canvas.add(sectionTitle("Análisis por Potrero", bold));

            float[] cw = {inner * 0.14f, inner * 0.07f, inner * 0.10f,
                          inner * 0.09f, inner * 0.09f, inner * 0.09f,
                          inner * 0.09f, inner * 0.10f, inner * 0.23f};
            Table t = new Table(cw).setWidth(UnitValue.createPercentValue(100));

            String[] headers = {"Potrero", "Área", "Estado", "NDVI\nActual",
                                 "NDVI\nProm.", "Biomasa\n(kg/ha)", "Cob.\n(%)",
                                 "Salud", "Recomendación"};
            for (String h : headers) {
                t.addHeaderCell(headerCell(h, bold));
            }

            boolean alt = false;
            for (NdviDto.ParcelComparison c : comparison) {
                DeviceRgb rowBg = alt ? new DeviceRgb(247, 250, 247) : C_WHITE;
                alt = !alt;

                double[] thresh = parcelThresholds.getOrDefault(c.getParcelId(), new double[]{0.6, 0.1});
                String healthLabel = classifyHealthLabel(c.getLatestNdvi(), thresh[0], thresh[1]);

                t.addCell(dataCell(c.getParcelName(), regular, rowBg));
                t.addCell(dataCell(c.getAreaHectares() != null
                        ? String.format("%.2f", c.getAreaHectares()) : "—", regular, rowBg));
                t.addCell(statusCell(c.getStatus(), bold, rowBg));
                t.addCell(ndviCell(c.getLatestNdvi(), bold, rowBg));
                t.addCell(ndviCell(c.getAvgNdvi(), regular, rowBg));
                t.addCell(dataCell(c.getBiomassKgPerHa() != null
                        ? String.format("%.0f", c.getBiomassKgPerHa()) : "—", regular, rowBg));
                t.addCell(dataCell(c.getVegetationCoverPercent() != null
                        ? String.format("%.0f%%", c.getVegetationCoverPercent()) : "—", regular, rowBg));
                t.addCell(healthCell(healthLabel, bold, rowBg));
                t.addCell(dataCell(nvl(c.getRecommendation()), regular, rowBg).setFontSize(8));
            }

            canvas.add(t.setMarginBottom(16));

            // Biomass bar chart
            canvas.add(sectionTitle("Biomasa por Potrero (kg MS/ha)", bold));
            double maxBiomass = comparison.stream()
                    .filter(c -> c.getBiomassKgPerHa() != null)
                    .mapToDouble(NdviDto.ParcelComparison::getBiomassKgPerHa)
                    .max().orElse(1);

            float barAreaW = inner - 140;
            for (NdviDto.ParcelComparison c : comparison) {
                if (c.getBiomassKgPerHa() == null) continue;
                float ratio = (float)(c.getBiomassKgPerHa() / maxBiomass);
                double[] thresh = parcelThresholds.getOrDefault(c.getParcelId(), new double[]{0.6, 0.1});
                canvas.add(biomassBarRow(c.getParcelName(), c.getBiomassKgPerHa(),
                        ratio, barAreaW, ndviColorByThreshold(c.getLatestNdvi(), thresh[0], thresh[1]),
                        bold, regular));
            }
        }
    }

    /** Page 5 — Alerts & Recommendations */
    private void buildAlertsAndRecsPage(PdfDocument pdf, Document doc,
                                         Terrain terrain, List<Parcel> parcels,
                                         List<Alert> allAlerts,
                                         List<NdviDto.RotationRecommendation> recs,
                                         PdfFont bold, PdfFont regular) throws Exception {

        PdfPage page = pdf.addNewPage(PageSize.A4);
        PdfCanvas cv = new PdfCanvas(page);
        float W = PageSize.A4.getWidth(), H = PageSize.A4.getHeight();

        drawPageChrome(cv, bold, regular, W, H, "Alertas y Recomendaciones de Rotación", 5);

        float margin = 36;
        float inner  = W - margin * 2;

        // Build map parcelId -> parcel for combined-alert logic
        Map<Long, Parcel> parcelMap = new LinkedHashMap<>();
        for (Parcel p : parcels) parcelMap.put(p.getId(), p);

        // Alertas de hoy agrupadas por parcela
        java.time.LocalDate today = java.time.LocalDate.now();
        Map<Long, java.util.Set<Alert.AlertType>> alertsByParcel = new LinkedHashMap<>();
        for (Alert a : allAlerts) {
            if (a.getCreatedAt() != null && a.getCreatedAt().toLocalDate().isEqual(today)) {
                alertsByParcel.computeIfAbsent(a.getParcel().getId(), k -> new java.util.LinkedHashSet<>())
                              .add(a.getAlertType());
            }
        }

        try (Canvas canvas = new Canvas(cv, new Rectangle(margin, 40, inner, H - 100))) {

            // ── Alertas activas ──
            canvas.add(sectionTitle(String.format("Alertas Activas (%d)", allAlerts.size()), bold));

            if (allAlerts.isEmpty()) {
                canvas.add(new Paragraph("✅  Sin alertas activas. Todas las parcelas están dentro de los umbrales normales.")
                        .setFont(regular).setFontSize(11).setFontColor(C_MID_GREEN)
                        .setMarginBottom(16));
            } else {
                for (Alert alert : allAlerts) {
                    canvas.add(alertCard(alert, bold, regular));
                }
            }

            // ── Recomendaciones de Rotación ──
            canvas.add(sectionTitle("Recomendaciones de Rotación", bold));

            // Build enriched recommendations including alert-based rules
            List<String[]> recRows = new ArrayList<>();
            // First: from standard recommendations
            for (NdviDto.RotationRecommendation rec : recs) {
                recRows.add(new String[]{
                        rec.getParcelName(),
                        rec.getCurrentStatus() != null ? rec.getCurrentStatus().name() : "—",
                        rec.getRecommendedStatus() != null ? rec.getRecommendedStatus().name() : "—",
                        fmt(rec.getCurrentNdvi(), "%.3f"),
                        rec.getBiomass() != null ? String.format("%.0f", rec.getBiomass()) : "—",
                        rec.getUrgency(),
                        nvl(rec.getReason())
                });
            }

            // Alert-based rotation adjustments (only for parcels not already in recs)
            Set<Long> recParcelIds = new java.util.HashSet<>();
            for (NdviDto.RotationRecommendation r : recs) recParcelIds.add(r.getParcelId());

            for (Map.Entry<Long, java.util.Set<Alert.AlertType>> entry : alertsByParcel.entrySet()) {
                Long parcelId = entry.getKey();
                java.util.Set<Alert.AlertType> types = entry.getValue();
                Parcel parcel = parcelMap.get(parcelId);
                if (parcel == null) continue;

                boolean hasForraje  = types.contains(Alert.AlertType.ESTADO_FORRAJE_BAJO_O_EN_UMBRAL);
                boolean hasEncharcado = types.contains(Alert.AlertType.POTRERO_ENCHARCADO);
                boolean hasEstres   = types.contains(Alert.AlertType.POTRERO_CON_ESTRES_HIDRICO);

                // Combined: FORRAJE + ENCHARCADO or FORRAJE + ESTRES → suggest move to healthy parcel
                if (hasForraje && (hasEncharcado || hasEstres)) {
                    // Find a parcel without either issue
                    Parcel candidate = findCandidateParcel(parcels, parcelId, alertsByParcel, true, true);
                    if (candidate == null) {
                        candidate = findCandidateParcel(parcels, parcelId, alertsByParcel, true, false);
                    }
                    String candidateName = candidate != null ? candidate.getName() : "ninguno disponible";
                    String motivo = hasEncharcado
                            ? "Potrero encharcado + forraje bajo. Mover ganado a: " + candidateName
                            : "Estrés hídrico + forraje bajo. Mover ganado a: " + candidateName;
                    if (!recParcelIds.contains(parcelId)) {
                        recRows.add(new String[]{
                                parcel.getName(),
                                parcel.getStatus() != null ? parcel.getStatus().name() : "—",
                                "EN_DESCANSO",
                                "—", "—", "URGENTE", motivo
                        });
                        recParcelIds.add(parcelId);
                    }
                    continue;
                }

                // Only ENCHARCADO → reduce días ocupación 45%
                if (hasEncharcado && !recParcelIds.contains(parcelId)) {
                    double diasOcup = parcel.getDiasOcupacion() != null ? parcel.getDiasOcupacion() : 0;
                    double reducido = Math.max(1, diasOcup * 0.55);
                    recRows.add(new String[]{
                            parcel.getName(),
                            parcel.getStatus() != null ? parcel.getStatus().name() : "—",
                            parcel.getStatus() != null ? parcel.getStatus().name() : "—",
                            "—", "—", "ALTA",
                            String.format("Potrero encharcado. Reducir días de ocupación de %.0f a %.0f días (−45%%).",
                                    diasOcup, reducido)
                    });
                    recParcelIds.add(parcelId);
                }

                // Only ESTRES_HIDRICO → reduce días ocupación 30%
                if (hasEstres && !recParcelIds.contains(parcelId)) {
                    double diasOcup = parcel.getDiasOcupacion() != null ? parcel.getDiasOcupacion() : 0;
                    double reducido = Math.max(1, diasOcup * 0.70);
                    recRows.add(new String[]{
                            parcel.getName(),
                            parcel.getStatus() != null ? parcel.getStatus().name() : "—",
                            parcel.getStatus() != null ? parcel.getStatus().name() : "—",
                            "—", "—", "ALTA",
                            String.format("Estrés hídrico. Reducir días de ocupación de %.0f a %.0f días (−30%%).",
                                    diasOcup, reducido)
                    });
                    recParcelIds.add(parcelId);
                }
            }

            if (recRows.isEmpty()) {
                canvas.add(new Paragraph("✅  Todas las parcelas están en estado óptimo. No hay cambios de rotación recomendados.")
                        .setFont(regular).setFontSize(11).setFontColor(C_MID_GREEN)
                        .setMarginBottom(16));
            } else {
                float[] rc = {inner * 0.18f, inner * 0.10f, inner * 0.10f,
                              inner * 0.10f, inner * 0.10f, inner * 0.12f, inner * 0.30f};
                Table rt = new Table(rc).setWidth(UnitValue.createPercentValue(100));
                String[] rh = {"Potrero", "Estado Actual", "Cambio Rec.", "NDVI", "Biomasa", "Urgencia", "Motivo"};
                for (String h : rh) rt.addHeaderCell(headerCell(h, bold));

                boolean alt = false;
                for (String[] row : recRows) {
                    DeviceRgb rowBg = alt ? new DeviceRgb(247, 250, 247) : C_WHITE;
                    alt = !alt;
                    rt.addCell(dataCell(row[0], bold, rowBg));
                    rt.addCell(dataCell(statusLabel(row[1]), regular, rowBg).setFontSize(8));
                    rt.addCell(dataCell(statusLabel(row[2]), regular, rowBg).setFontSize(8));
                    rt.addCell(dataCell(row[3], regular, rowBg));
                    rt.addCell(dataCell(row[4], regular, rowBg));
                    rt.addCell(urgencyCell(row[5], bold, rowBg));
                    rt.addCell(dataCell(row[6], regular, rowBg).setFontSize(8));
                }
                canvas.add(rt);
            }
        }
    }

    /** Encuentra un potrero candidato sin los problemas indicados */
    private Parcel findCandidateParcel(List<Parcel> all, Long excludeId,
                                        Map<Long, java.util.Set<Alert.AlertType>> alertsByParcel,
                                        boolean excludeEncharcado, boolean excludeForraje) {
        for (Parcel p : all) {
            if (p.getId().equals(excludeId)) continue;
            java.util.Set<Alert.AlertType> types = alertsByParcel.getOrDefault(p.getId(), java.util.Set.of());
            if (excludeEncharcado && (types.contains(Alert.AlertType.POTRERO_ENCHARCADO)
                    || types.contains(Alert.AlertType.POTRERO_CON_ESTRES_HIDRICO))) continue;
            if (excludeForraje && types.contains(Alert.AlertType.ESTADO_FORRAJE_BAJO_O_EN_UMBRAL)) continue;
            return p;
        }
        return null;
    }

    /** Page 6 — History, rotation plan and lotes */
    private void buildHistoryAndRotationPage(PdfDocument pdf, Document doc,
                                              Terrain terrain, List<Parcel> parcels,
                                              List<Lote> lotes,
                                              List<NdviDto.RotationHistoryEntry> history,
                                              PdfFont bold, PdfFont regular) throws Exception {

        PdfPage page = pdf.addNewPage(PageSize.A4);
        PdfCanvas cv = new PdfCanvas(page);
        float W = PageSize.A4.getWidth(), H = PageSize.A4.getHeight();

        drawPageChrome(cv, bold, regular, W, H, "Historial y Plan de Rotación", 6);

        float margin = 36;
        float inner  = W - margin * 2;

        try (Canvas canvas = new Canvas(cv, new Rectangle(margin, 40, inner, H - 100))) {

            // ── Orden de rotación programado ──
            canvas.add(sectionTitle("Orden y Plan de Rotación por Potrero", bold));

            List<Parcel> ordered = new ArrayList<>(parcels);
            ordered.sort(Comparator.comparingInt(p -> (p.getRotationOrder() != null ? p.getRotationOrder() : 9999)));

            float[] pc = {inner * 0.05f, inner * 0.20f, inner * 0.12f, inner * 0.12f,
                          inner * 0.12f, inner * 0.12f, inner * 0.27f};
            Table pt = new Table(pc).setWidth(UnitValue.createPercentValue(100));
            String[] ph = {"#", "Potrero", "Estado", "Días Ocup.", "Días Desc.", "Área (ha)", "Lote Actual"};
            for (String h : ph) pt.addHeaderCell(headerCell(h, bold));

            boolean altP = false;
            for (Parcel p : ordered) {
                DeviceRgb rowBg = altP ? new DeviceRgb(247, 250, 247) : C_WHITE;
                altP = !altP;

                // Find lote currently in this parcel
                String loteEnParcel = lotes.stream()
                        .filter(l -> l.getCurrentParcel() != null && l.getCurrentParcel().getId().equals(p.getId()))
                        .map(l -> l.getName() + " (" + l.getCabezas() + " cab.)")
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("—");

                pt.addCell(dataCell(p.getRotationOrder() != null ? String.valueOf(p.getRotationOrder()) : "—", regular, rowBg));
                pt.addCell(dataCell(p.getName(), bold, rowBg));
                pt.addCell(statusCell(p.getStatus(), regular, rowBg));
                pt.addCell(dataCell(p.getDiasOcupacion() != null ? String.format("%.0f", p.getDiasOcupacion()) : "—", regular, rowBg));
                pt.addCell(dataCell(p.getDiasDescanso() != null ? String.format("%.0f", p.getDiasDescanso()) : "—", regular, rowBg));
                pt.addCell(dataCell(p.getAreaHectares() != null ? String.format("%.2f", p.getAreaHectares()) : "—", regular, rowBg));
                pt.addCell(dataCell(loteEnParcel, regular, rowBg).setFontSize(8));
            }
            canvas.add(pt.setMarginBottom(16));

            // ── Lotes activos en el terreno ──
            canvas.add(sectionTitle("Lotes Activos en el Terreno", bold));

            if (lotes.isEmpty()) {
                canvas.add(new Paragraph("Sin lotes activos registrados en este terreno.")
                        .setFont(regular).setFontSize(10).setFontColor(C_TEXT_MUTED).setMarginBottom(12));
            } else {
                float[] lc = {inner * 0.22f, inner * 0.12f, inner * 0.12f, inner * 0.12f, inner * 0.42f};
                Table lt = new Table(lc).setWidth(UnitValue.createPercentValue(100));
                String[] lh = {"Lote", "Cabezas", "F. Ingreso", "Potrero Actual", "Historial reciente"};
                for (String h : lh) lt.addHeaderCell(headerCell(h, bold));

                boolean altL = false;
                for (Lote lote : lotes) {
                    DeviceRgb rowBg = altL ? new DeviceRgb(247, 250, 247) : C_WHITE;
                    altL = !altL;

                    String parcelActual = lote.getCurrentParcel() != null
                            ? lote.getCurrentParcel().getName() : "Sin asignar";
                    String histRecent = lote.getParcelHistory().stream()
                            .limit(3)
                            .map(h -> h.getParcel().getName() +
                                    (h.getFechaIngreso() != null ? " (" + h.getFechaIngreso() + ")" : ""))
                            .reduce((a, b) -> a + " → " + b)
                            .orElse("—");

                    lt.addCell(dataCell(lote.getName(), bold, rowBg));
                    lt.addCell(dataCell(String.valueOf(lote.getCabezas()), regular, rowBg));
                    lt.addCell(dataCell(lote.getFechaIngreso() != null ? lote.getFechaIngreso().toString() : "—", regular, rowBg).setFontSize(8));
                    lt.addCell(dataCell(parcelActual, regular, rowBg));
                    lt.addCell(dataCell(histRecent, regular, rowBg).setFontSize(8));
                }
                canvas.add(lt.setMarginBottom(16));
            }

            // ── Historial de cambios de estado ──
            canvas.add(sectionTitle("Historial de Cambios de Estado de Potreros", bold));

            if (history.isEmpty()) {
                canvas.add(new Paragraph("Sin historial de rotación registrado.")
                        .setFont(regular).setFontSize(10).setFontColor(C_TEXT_MUTED));
            } else {
                float[] hc = {inner * 0.16f, inner * 0.20f, inner * 0.18f,
                          inner * 0.18f, inner * 0.14f, inner * 0.14f};
                Table ht = new Table(hc).setWidth(UnitValue.createPercentValue(100));
                String[] hh = {"Fecha", "Potrero", "Estado anterior", "Estado nuevo", "NDVI", "Biomasa"};
                for (String h : hh) ht.addHeaderCell(headerCell(h, bold));

                boolean alt = false;
                for (NdviDto.RotationHistoryEntry h : history) {
                    DeviceRgb rowBg = alt ? new DeviceRgb(247, 250, 247) : C_WHITE;
                    alt = !alt;

                    String date = h.getChangedAt() != null
                            ? h.getChangedAt().substring(0, 16).replace("T", " ") : "—";
                    String previousStatus = h.getPreviousStatus() != null
                        ? statusLabel(h.getPreviousStatus()) : "—";
                    String newStatus = h.getNewStatus() != null
                        ? statusLabel(h.getNewStatus()) : "—";

                    ht.addCell(dataCell(date, regular, rowBg).setFontSize(9));
                    ht.addCell(dataCell(h.getParcelName(), bold, rowBg));
                    ht.addCell(dataCell(previousStatus, regular, rowBg).setFontSize(9));
                    ht.addCell(dataCell(newStatus, regular, rowBg).setFontSize(9));
                    ht.addCell(dataCell(h.getNdviAtChange() != null
                            ? String.format("%.3f", h.getNdviAtChange()) : "—", regular, rowBg));
                    ht.addCell(dataCell(h.getBiomassAtChange() != null
                            ? String.format("%.0f", h.getBiomassAtChange()) : "—", regular, rowBg));
                }
                canvas.add(ht);
            }
        }
    }

    // ─── Drawing helpers ────────────────────────────────────────────────────

    private void drawPageChrome(PdfCanvas cv, PdfFont bold, PdfFont regular,
                                 float W, float H, String title, int pageNum) throws Exception {
        cv.setFillColor(C_BG).rectangle(0, 0, W, H).fill();

        // Header bar
        cv.setFillColor(C_DARK_GREEN).rectangle(0, H - 50, W, 50).fill();
        cv.setFillColor(C_LIGHT_GREEN).rectangle(0, H - 52, W, 2).fill();

        try (Canvas hc = new Canvas(cv, new Rectangle(20, H - 44, W - 40, 36))) {
            hc.add(new Paragraph("SIMGAN  ·  " + title)
                    .setFont(bold).setFontSize(13).setFontColor(C_WHITE));
        }
        try (Canvas pc = new Canvas(cv, new Rectangle(W - 80, H - 44, 60, 36))) {
            pc.add(new Paragraph("Pág. " + pageNum)
                    .setFont(regular).setFontSize(10).setFontColor(C_LIGHT_GREEN)
                    .setTextAlignment(TextAlignment.RIGHT));
        }

        // Footer bar
        cv.setFillColor(new DeviceRgb(241, 245, 241)).rectangle(0, 0, W, 30).fill();
        cv.setFillColor(C_DARK_GREEN).rectangle(0, 28, W, 2).fill();
        try (Canvas fc = new Canvas(cv, new Rectangle(20, 4, W - 40, 22))) {
            fc.add(new Paragraph("Informe generado por SIMGAN  ·  " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                    .setFont(regular).setFontSize(8).setFontColor(C_TEXT_MUTED));
        }
    }

    private void drawPolygon(PdfCanvas cv, List<double[]> ring,
                              double minLng, double minLat,
                              float scale, float offX, float offY) {
        if (ring.isEmpty()) return;
        boolean first = true;
        for (double[] pt : ring) {
            float x = offX + (float)((pt[0] - minLng) * scale);
            float y = offY + (float)((pt[1] - minLat) * scale);
            if (first) { cv.moveTo(x, y); first = false; }
            else        { cv.lineTo(x, y); }
        }
        cv.closePath();
    }

    // ─── Cell / element factories ───────────────────────────────────────────

    private Paragraph sectionTitle(String text, PdfFont bold) {
        return new Paragraph(text)
                .setFont(bold).setFontSize(12).setFontColor(C_DARK_GREEN)
                .setMarginBottom(6).setMarginTop(12)
                .setBorderBottom(new SolidBorder(C_MID_GREEN, 1.5f))
                .setPaddingBottom(4);
    }

    private Cell headerCell(String text, PdfFont bold) {
        return new Cell()
                .add(new Paragraph(text).setFont(bold).setFontSize(8).setFontColor(C_WHITE))
                .setBackgroundColor(C_DARK_GREEN)
                .setPadding(5)
                .setBorder(new SolidBorder(C_MID_GREEN, 0.5f))
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private Cell dataCell(String text, PdfFont font, DeviceRgb bg) {
        return new Cell()
                .add(new Paragraph(text != null ? text : "—").setFont(font).setFontSize(9))
                .setBackgroundColor(bg)
                .setPadding(4)
                .setBorder(new SolidBorder(C_BORDER, 0.5f));
    }

    private Cell statusCell(Parcel.ParcelStatus status, PdfFont font, DeviceRgb bg) {
        String label = status != null ? statusLabel(status.name()) : "—";
        DeviceRgb color = switch (status != null ? status : Parcel.ParcelStatus.DISPONIBLE) {
            case DISPONIBLE  -> C_MID_GREEN;
            case EN_USO      -> C_AMBER;
            case EN_DESCANSO -> C_BLUE;
        };
        return new Cell()
                .add(new Paragraph(label).setFont(font).setFontSize(8).setFontColor(color))
                .setBackgroundColor(bg)
                .setPadding(4)
                .setBorder(new SolidBorder(C_BORDER, 0.5f))
                .setTextAlignment(TextAlignment.CENTER);
    }

    private Cell ndviCell(Double ndvi, PdfFont font, DeviceRgb bg) {
        String txt = ndvi != null ? String.format("%.3f", ndvi) : "—";
        DeviceRgb color = ndvi != null ? ndviColor(ndvi) : C_TEXT_MUTED;
        return new Cell()
                .add(new Paragraph(txt).setFont(font).setFontSize(9).setFontColor(color))
                .setBackgroundColor(bg)
                .setPadding(4)
                .setBorder(new SolidBorder(C_BORDER, 0.5f))
                .setTextAlignment(TextAlignment.CENTER);
    }

    private Cell healthCell(String health, PdfFont font, DeviceRgb bg) {
        DeviceRgb color = switch (health != null ? health : "") {
            case "EXCELENTE" -> new DeviceRgb(74, 222, 128);
            case "ÓPTIMO"    -> C_AMBER;
            case "CRÍTICO"   -> C_RED;
            default          -> C_TEXT_MUTED;
        };
        return new Cell()
                .add(new Paragraph(health != null ? health : "—").setFont(font).setFontSize(8).setFontColor(color))
                .setBackgroundColor(bg)
                .setPadding(4)
                .setBorder(new SolidBorder(C_BORDER, 0.5f))
                .setTextAlignment(TextAlignment.CENTER);
    }

    private Cell urgencyCell(String urgency, PdfFont font, DeviceRgb bg) {
        DeviceRgb color = switch (urgency != null ? urgency : "") {
            case "URGENTE" -> C_RED;
            case "ALTA"    -> C_ORANGE;
            case "MEDIA"   -> C_AMBER;
            default        -> C_MID_GREEN;
        };
        return new Cell()
                .add(new Paragraph(urgency != null ? urgency : "—").setFont(font).setFontSize(8).setFontColor(color))
                .setBackgroundColor(bg)
                .setPadding(4)
                .setBorder(new SolidBorder(C_BORDER, 0.5f))
                .setTextAlignment(TextAlignment.CENTER);
    }

    private void addMetricCell(Table t, String label, String value, DeviceRgb color,
                                PdfFont bold, PdfFont regular) {
        Cell c = new Cell()
                .add(new Paragraph(label).setFont(regular).setFontSize(9).setFontColor(C_TEXT_MUTED).setMarginBottom(4))
                .add(new Paragraph(value).setFont(bold).setFontSize(18).setFontColor(color))
                .setBackgroundColor(C_WHITE)
                .setPadding(14)
                .setBorder(new SolidBorder(C_BORDER, 1f))
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        t.addCell(c);
    }

    private void addInfoRow(Table t, String label, String value, PdfFont bold, PdfFont regular) {
        t.addCell(new Cell()
                .add(new Paragraph(label).setFont(bold).setFontSize(9).setFontColor(C_TEXT_MUTED))
                .setBackgroundColor(new DeviceRgb(245, 248, 245))
                .setPadding(6).setBorder(new SolidBorder(C_BORDER, 0.5f)));
        t.addCell(new Cell()
                .add(new Paragraph(value).setFont(regular).setFontSize(9))
                .setBackgroundColor(C_WHITE)
                .setPadding(6).setBorder(new SolidBorder(C_BORDER, 0.5f)));
    }

    private void addCoverField(Canvas canvas, String label, String value, PdfFont bold, PdfFont regular) {
        canvas.add(new Paragraph()
                .add(new com.itextpdf.layout.element.Text(label + ":  ").setFont(bold).setFontSize(11).setFontColor(C_TEXT_MUTED))
                .add(new com.itextpdf.layout.element.Text(value).setFont(regular).setFontSize(11).setFontColor(C_TEXT_DARK))
                .setMarginBottom(6));
    }  


    private Paragraph alertCard(Alert alert, PdfFont bold, PdfFont regular) {
        DeviceRgb color = switch (alert.getAlertType()) {
            case ESTADO_FORRAJE_BAJO_O_EN_UMBRAL -> C_RED;
            case POTRERO_ENCHARCADO              -> C_BLUE;
            case POTRERO_CON_ESTRES_HIDRICO      -> C_ORANGE;
        };
        String typeLabel = switch (alert.getAlertType()) {
            case ESTADO_FORRAJE_BAJO_O_EN_UMBRAL -> "FORRAJE BAJO";
            case POTRERO_ENCHARCADO              -> "ENCHARCADO";
            case POTRERO_CON_ESTRES_HIDRICO      -> "ESTRÉS HÍDRICO";
        };
        String parcelName = alert.getParcel() != null ? alert.getParcel().getName() : "—";
        String msg = nvl(alert.getMessage());
        String ts  = alert.getCreatedAt() != null
                ? alert.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "";
        return new Paragraph()
                .add(new com.itextpdf.layout.element.Text("■ " + typeLabel + "  |  " + parcelName + "\n")
                        .setFont(bold).setFontSize(9).setFontColor(color))
                .add(new com.itextpdf.layout.element.Text(msg + "\n")
                        .setFont(regular).setFontSize(9).setFontColor(C_TEXT_DARK))
                .add(new com.itextpdf.layout.element.Text(ts)
                        .setFont(regular).setFontSize(8).setFontColor(C_TEXT_MUTED))
                .setBorderLeft(new SolidBorder(color, 3f))
                .setPaddingLeft(8)
                .setMarginBottom(8);
    }

    private Paragraph biomassBarRow(String name, double value, float ratio, float maxW,
                                     DeviceRgb barColor, PdfFont bold, PdfFont regular) {
        String barStr = "█".repeat(Math.max(1, (int)(ratio * 40)));
        return new Paragraph()
                .add(new com.itextpdf.layout.element.Text(String.format("%-14s ", name))
                        .setFont(bold).setFontSize(9))
                .add(new com.itextpdf.layout.element.Text(barStr)
                        .setFont(bold).setFontSize(9).setFontColor(barColor))
                .add(new com.itextpdf.layout.element.Text(String.format("  %.0f kg/ha", value))
                        .setFont(regular).setFontSize(9).setFontColor(C_TEXT_MUTED))
                .setMarginBottom(3);
    }

    // ─── GeoJSON / geometry helpers ─────────────────────────────────────────

    private List<double[]> parseRing(String geoJson) {
        if (geoJson == null || geoJson.isBlank()) return List.of();
        try {
            JsonNode root = mapper.readTree(geoJson);
            JsonNode geom;
            if (root.has("features")) {
                geom = root.get("features").get(0).get("geometry");
            } else if (root.has("geometry")) {
                geom = root.get("geometry");
            } else {
                geom = root;
            }
            JsonNode ring = geom.get("coordinates").get(0);
            List<double[]> pts = new ArrayList<>();
            for (JsonNode c : ring) {
                pts.add(new double[]{c.get(0).asDouble(), c.get(1).asDouble()});
            }
            return pts;
        } catch (Exception e) {
            log.debug("Cannot parse GeoJSON ring: {}", e.getMessage());
            return List.of();
        }
    }

    private double[] computeBoundingBox(List<List<double[]>> rings) {
        double minLng = Double.MAX_VALUE, minLat = Double.MAX_VALUE;
        double maxLng = -Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        for (var ring : rings) {
            for (double[] pt : ring) {
                if (pt[0] < minLng) minLng = pt[0];
                if (pt[0] > maxLng) maxLng = pt[0];
                if (pt[1] < minLat) minLat = pt[1];
                if (pt[1] > maxLat) maxLat = pt[1];
            }
        }
        return new double[]{minLng, minLat, maxLng, maxLat};
    }

    private double[] centroid(List<double[]> ring) {
        double sx = 0, sy = 0;
        for (double[] pt : ring) { sx += pt[0]; sy += pt[1]; }
        return new double[]{sx / ring.size(), sy / ring.size()};
    }

    // ─── Colour helpers ──────────────────────────────────────────────────────

    private DeviceRgb ndviColor(Double ndvi) {
        // Generic color without parcel thresholds (used for biomass bars fallback)
        if (ndvi == null) return C_TEXT_MUTED;
        if (ndvi >= 0.60) return new DeviceRgb(74, 222, 128);
        if (ndvi >= 0.30) return C_AMBER;
        return C_RED;
    }

    /** Color based on calibrated thresholds: CRÍTICO / ÓPTIMO / EXCELENTE */
    private DeviceRgb ndviColorByThreshold(Double ndvi, double optim, double alert) {
        if (ndvi == null) return C_TEXT_MUTED;
        if (ndvi <= alert)  return C_RED;
        if (ndvi <= optim)  return C_AMBER;
        return new DeviceRgb(74, 222, 128);
    }

    /** Health label based on calibrated thresholds */
    private String classifyHealthLabel(Double ndvi, double optim, double alert) {
        if (ndvi == null) return "—";
        if (ndvi <= alert)  return "CRÍTICO";
        if (ndvi <= optim)  return "ÓPTIMO";
        return "EXCELENTE";
    }

    private DeviceRgb parcelHealthColor(NdviDto.ParcelComparison comp) {
        if (comp == null || comp.getLatestNdvi() == null) return new DeviceRgb(209, 213, 219);
        // Use generic scale for map (thresholds not available without parcel entity here)
        double ndvi = comp.getLatestNdvi();
        if (ndvi >= 0.60) return new DeviceRgb(134, 239, 172);
        if (ndvi >= 0.30) return new DeviceRgb(252, 211, 77);
        return new DeviceRgb(252, 165, 165);
    }

    private DeviceRgb hexColor(String hex) {
        hex = hex.replace("#", "");
        return new DeviceRgb(
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16));
    }

    // ─── String helpers ──────────────────────────────────────────────────────

    private String nvl(String s) { return (s != null && !s.isBlank()) ? s : "—"; }

    private String fmt(Double val, String pattern) {
        return val != null ? String.format(pattern, val) : "—";
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "DISPONIBLE"  -> "Disponible";
            case "EN_USO"      -> "En uso";
            case "EN_DESCANSO" -> "En descanso";
            default            -> status;
        };
    }
}
