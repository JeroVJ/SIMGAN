package com.simgan.controller;

import com.simgan.dto.TerrainDto;
import com.simgan.service.TerrainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/terrains")
@RequiredArgsConstructor
public class TerrainController {

    private final TerrainService terrainService;

    @PostMapping
    public ResponseEntity<TerrainDto.Response> create(@Valid @RequestBody TerrainDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(terrainService.create(request));
    }

    @GetMapping("/farm/{farmId}")
    public ResponseEntity<List<TerrainDto.Response>> findByFarmId(@PathVariable Long farmId) {
        return ResponseEntity.ok(terrainService.findByFarmId(farmId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TerrainDto.Response> findById(@PathVariable Long id) {
        return ResponseEntity.ok(terrainService.findById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        terrainService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
