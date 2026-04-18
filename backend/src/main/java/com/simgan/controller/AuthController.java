package com.simgan.controller;

import com.simgan.entity.Ganadero;
import com.simgan.security.JwtUtil;
import com.simgan.service.AuthTokenService;
import com.simgan.service.GanaderoService;
import com.simgan.service.PasswordResetService;
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
    private final PasswordResetService passwordResetService;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                         GanaderoService ganaderoService, AuthTokenService authTokenService,
                         PasswordResetService passwordResetService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.ganaderoService = ganaderoService;
        this.authTokenService = authTokenService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        String email = safeTrim(loginRequest.get("email"));
        if (email != null) email = email.toLowerCase();
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
        String firstName = safeTrim(registerRequest.get("firstName"));
        String lastName = safeTrim(registerRequest.get("lastName"));
        String email = safeTrim(registerRequest.get("email"));
        String password = registerRequest.get("password");

        if (firstName == null || firstName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El nombre es obligatorio"));
        }
        if (lastName == null || lastName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El apellido es obligatorio"));
        }
        if (email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Correo inválido"));
        }
        if (password == null || password.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("message", "La contraseña debe tener al menos 8 caracteres"));
        }

        String normalizedEmail = email.toLowerCase();
        Optional<Ganadero> existing = ganaderoService.buscarPorCorreo(normalizedEmail);
        if (existing.isPresent()) {
            return ResponseEntity.status(400).body(Map.of("message", "El correo ya está registrado"));
        }

        Ganadero ganadero = Ganadero.builder()
                .nombreCompleto(firstName)
                .apellidoCompleto(lastName)
                .correo(normalizedEmail)
                .contrasena(password)
                .tipoDocumento("CC")
                .idDocumento(0)
                .build();

        ganaderoService.guardarGanadero(ganadero);
        email = normalizedEmail;

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

    /**
     * Starts the password reset flow. Always returns 200 so attackers cannot
     * enumerate which emails are registered. If the email exists, a reset link
     * is emailed asynchronously (token valid 30 minutes).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = safeTrim(body.get("email"));
        if (email != null && !email.isBlank()) {
            try {
                passwordResetService.requestReset(email);
            } catch (Exception e) {
                // Swallow any error so we always return the same neutral response
            }
        }
        return ResponseEntity.ok(Map.of(
                "message", "Si el correo existe, recibirás un enlace para restablecer tu contraseña."
        ));
    }

    /** Confirms the reset by validating the token and updating the password. */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("password");
        try {
            passwordResetService.confirmReset(token, newPassword);
            return ResponseEntity.ok(Map.of("message", "Contraseña actualizada"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private static String safeTrim(String value) {
        return value == null ? null : value.trim();
    }
}