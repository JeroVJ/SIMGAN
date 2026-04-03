package com.simgan.controller;

import com.simgan.service.PdfReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final PdfReportService pdfReportService;

    /**
     * GET /api/reports/terrain/{terrainId}
     * Generates and downloads a complete PDF report for the given terrain.
     */
    @GetMapping(value = "/terrain/{terrainId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getTerrainReport(@PathVariable Long terrainId) {
        log.info("PDF report requested for terrain {}", terrainId);

        byte[] pdf = pdfReportService.generateTerrainReport(terrainId);

        String filename = String.format("SIMGAN-Informe-Terreno-%d-%s.pdf",
                terrainId,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }
}
