package com.simgan.controller;

import com.simgan.dto.FarmDto;
import com.simgan.service.FarmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
<<<<<<< HEAD
import org.springframework.security.core.Authentication;
=======
>>>>>>> origin/procesamiento
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/farms")
@RequiredArgsConstructor
public class FarmController {

    private final FarmService farmService;

    @PostMapping
<<<<<<< HEAD
    public ResponseEntity<FarmDto.Response> create(@Valid @RequestBody FarmDto.CreateRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(farmService.create(request, authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<List<FarmDto.Response>> findAll(Authentication authentication) {
        return ResponseEntity.ok(farmService.findAll(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FarmDto.Response> findById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(farmService.findById(id, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        farmService.delete(id, authentication.getName());
=======
    public ResponseEntity<FarmDto.Response> create(@Valid @RequestBody FarmDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(farmService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<FarmDto.Response>> findAll() {
        return ResponseEntity.ok(farmService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FarmDto.Response> findById(@PathVariable Long id) {
        return ResponseEntity.ok(farmService.findById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        farmService.delete(id);
>>>>>>> origin/procesamiento
        return ResponseEntity.noContent().build();
    }
}
