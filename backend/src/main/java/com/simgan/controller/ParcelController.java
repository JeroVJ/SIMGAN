package com.simgan.controller;

import com.simgan.dto.ParcelDto;
import com.simgan.service.ParcelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parcels")
@RequiredArgsConstructor
public class ParcelController {

    private final ParcelService parcelService;

    @PostMapping
    public ResponseEntity<ParcelDto.Response> create(@Valid @RequestBody ParcelDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parcelService.create(request));
    }

    @GetMapping("/terrain/{terrainId}")
    public ResponseEntity<List<ParcelDto.Response>> findByTerrainId(@PathVariable Long terrainId) {
        return ResponseEntity.ok(parcelService.findByTerrainId(terrainId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ParcelDto.Response> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ParcelDto.StatusUpdate statusUpdate) {
        return ResponseEntity.ok(parcelService.updateStatus(id, statusUpdate.getStatus()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        parcelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
