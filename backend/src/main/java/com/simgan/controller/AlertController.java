package com.simgan.controller;

import com.simgan.entity.Alert;
import com.simgan.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
@Slf4j
public class AlertController {

    private final AlertRepository alertRepository;

    /**
     * GET /api/alerts/terrain/{terrainId}
     * Returns all alerts for a terrain, ordered by date descending.
     */
    @GetMapping("/terrain/{terrainId}")
    public ResponseEntity<List<Map<String, Object>>> getAlertsByTerrain(@PathVariable Long terrainId) {
        log.info("Fetching alerts for terrain {}", terrainId);

        List<Map<String, Object>> result = alertRepository
                .findByTerrainIdOrderByCreatedAtDesc(terrainId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    private Map<String, Object> toDto(Alert alert) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", alert.getId());
        dto.put("alertType", alert.getAlertType() != null ? alert.getAlertType().name() : null);
        dto.put("message", alert.getMessage());
        dto.put("createdAt", alert.getCreatedAt() != null
                ? alert.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null);

        if (alert.getParcel() != null) {
            Map<String, Object> parcel = new LinkedHashMap<>();
            parcel.put("id", alert.getParcel().getId());
            parcel.put("name", alert.getParcel().getName());
            dto.put("parcel", parcel);
        }

        return dto;
    }
}
