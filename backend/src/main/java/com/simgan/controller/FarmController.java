package com.simgan.controller;

import com.simgan.dto.FarmDto;
import com.simgan.service.FarmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/farms")
@RequiredArgsConstructor
/**
 * Endpoints REST para Fincas.
 *
 * Funcionalidades:
 * - Crear finca asociada a un ganadero.
 * - Listar fincas del ganadero autenticado.
 * - Obtener/eliminar finca verificando pertenencia (control de acceso).
 */
public class FarmController {

    private final FarmService farmService;

    @PostMapping
    /**
     * Crea una finca.
     *
     * Nota: el email del usuario autenticado se usa para validaciones de acceso en el service.
     */
    public ResponseEntity<FarmDto.Response> create(@Valid @RequestBody FarmDto.CreateRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(farmService.create(request, authentication.getName()));
    }

    @GetMapping
    /**
     * Lista todas las fincas del ganadero autenticado.
     */
    public ResponseEntity<List<FarmDto.Response>> findAll(Authentication authentication) {
        return ResponseEntity.ok(farmService.findAll(authentication.getName()));
    }

    @GetMapping("/{id}")
    /**
     * Obtiene una finca por id (sólo si pertenece al ganadero autenticado).
     */
    public ResponseEntity<FarmDto.Response> findById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(farmService.findById(id, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    /**
     * Elimina una finca por id (sólo si pertenece al ganadero autenticado).
     */
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        farmService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
