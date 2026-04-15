package com.simgan.controller;

import com.simgan.entity.Ganadero;
import com.simgan.security.JwtUtil;
import com.simgan.service.AuthTokenService;
import com.simgan.service.GanaderoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final GanaderoService ganaderoService;
    private final AuthTokenService authTokenService;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil, 
                         GanaderoService ganaderoService, AuthTokenService authTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.ganaderoService = ganaderoService;
        this.authTokenService = authTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        String email = loginRequest.get("email");
        String password = loginRequest.get("password");

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            String token = jwtUtil.generateToken(email);

            // Get ganadero info
            Ganadero ganadero = ganaderoService.buscarPorCorreo(email).orElseThrow();
            
            // Save token to database
            authTokenService.saveToken(ganadero, token, jwtUtil.getExpirationTime());

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("ganadero", Map.of(
                "id", ganadero.getId(),
                "nombreCompleto", ganadero.getNombreCompleto(),
                "apellidoCompleto", ganadero.getApellidoCompleto(),
                "correo", ganadero.getCorreo()
            ));
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("message", "Credenciales incorrectas"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> registerRequest) {
        String firstName = registerRequest.get("firstName");
        String lastName = registerRequest.get("lastName");
        String email = registerRequest.get("email");
        String password = registerRequest.get("password");

        Optional<Ganadero> existing = ganaderoService.buscarPorCorreo(email);
        if (existing.isPresent()) {
            return ResponseEntity.status(400).body(Map.of("message", "El correo ya está registrado"));
        }

        Ganadero ganadero = Ganadero.builder()
                .nombreCompleto(firstName)
                .apellidoCompleto(lastName)
                .correo(email)
                .contrasena(password)
                .tipoDocumento("CC")
                .idDocumento(0)
                .build();

        ganaderoService.guardarGanadero(ganadero);

        // Auto-login after registration
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            String token = jwtUtil.generateToken(email);
            
            // Save token to database
            authTokenService.saveToken(ganadero, token, jwtUtil.getExpirationTime());

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("ganadero", Map.of(
                "id", ganadero.getId(),
                "nombreCompleto", ganadero.getNombreCompleto(),
                "apellidoCompleto", ganadero.getApellidoCompleto(),
                "correo", ganadero.getCorreo()
            ));
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(500).body(Map.of("message", "Error en el registro"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        String email = authentication.getName();
        Ganadero ganadero = ganaderoService.buscarPorCorreo(email).orElseThrow();
        return ResponseEntity.ok(Map.of(
            "id", ganadero.getId(),
            "nombreCompleto", ganadero.getNombreCompleto(),
            "apellidoCompleto", ganadero.getApellidoCompleto(),
            "correo", ganadero.getCorreo()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authTokenService.revokeToken(token);
            return ResponseEntity.ok(Map.of("message", "Sesión cerrada exitosamente"));
        }
        return ResponseEntity.status(400).body(Map.of("message", "Token inválido"));
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> getSessions(Authentication authentication) {
        String email = authentication.getName();
        Ganadero ganadero = ganaderoService.buscarPorCorreo(email).orElseThrow();
        var sessions = authTokenService.getActiveSessions(ganadero.getId())
                .stream()
                .map(token -> Map.of(
                    "id", token.getId(),
                    "createdAt", token.getCreatedAt().toString(),
                    "expiresAt", token.getExpiresAt().toString()
                ))
                .toList();
        return ResponseEntity.ok(Map.of("sessions", sessions));
    }

    @PostMapping("/revoke-all")
    public ResponseEntity<?> revokeAll(Authentication authentication) {
        String email = authentication.getName();
        Ganadero ganadero = ganaderoService.buscarPorCorreo(email).orElseThrow();
        authTokenService.revokeAllTokens(ganadero.getId());
        return ResponseEntity.ok(Map.of("message", "Todas las sesiones han sido cerradas"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("message", "El correo es requerido"));
        }

        Optional<Ganadero> ganadero = ganaderoService.buscarPorCorreo(email);
        if (ganadero.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No existe la cuenta asociada al correo electrónico ingresado"));
        }

        ganaderoService.initiatePasswordReset(email);
        return ResponseEntity.ok(Map.of("message", "Se ha enviado un correo con instrucciones para restablecer su contraseña"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String password = request.get("password");

        if (token == null || password == null) {
            return ResponseEntity.status(400).body(Map.of("message", "Token y contraseña son requeridos"));
        }

        boolean success = ganaderoService.resetPassword(token, password);
        if (success) {
            return ResponseEntity.ok(Map.of("message", "Contraseña actualizada exitosamente"));
        } else {
            return ResponseEntity.status(400).body(Map.of("message", "El enlace es inválido o ha expirado"));
        }
    }
}