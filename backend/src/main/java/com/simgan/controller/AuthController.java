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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final GanaderoService ganaderoService;
    private final AuthTokenService authTokenService;

    // A simple map to store reset tokens (for now, in memory or we can use the AuthToken table)
    private static final Map<String, String> resetTokens = new HashMap<>();
    private static final Map<String, LocalDateTime> resetTokenExpirations = new HashMap<>();

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil, 
                         GanaderoService ganaderoService, AuthTokenService authTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.ganaderoService = ganaderoService;
        this.authTokenService = authTokenService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        Optional<Ganadero> ganaderoOpt = ganaderoService.buscarPorCorreo(email);

        if (ganaderoOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No existe la cuenta asociada al correo electrónico ingresado"));
        }

        // Generate token
        String token = UUID.randomUUID().toString();
        resetTokens.put(token, email);
        resetTokenExpirations.put(token, LocalDateTime.now().plusHours(1));

        // En un entorno real, aquí se enviaría el correo.
        // Por ahora simulamos que se envió.
        System.out.println("Enlace de recuperación para " + email + ": http://localhost:5173/reset-password/" + token);

        return ResponseEntity.ok(Map.of("message", "Instrucciones enviadas al correo"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String newPassword = request.get("newPassword");

        String email = resetTokens.get(token);
        LocalDateTime expiration = resetTokenExpirations.get(token);

        if (email == null || expiration == null || expiration.isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(400).body(Map.of("message", "Token inválido o expirado"));
        }

        Ganadero ganadero = ganaderoService.buscarPorCorreo(email).orElseThrow();
        ganaderoService.actualizarContrasena(ganadero, newPassword);

        // Remove used token
        resetTokens.remove(token);
        resetTokenExpirations.remove(token);

        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada exitosamente"));
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
}