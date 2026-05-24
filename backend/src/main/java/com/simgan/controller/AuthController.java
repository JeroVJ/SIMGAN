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
/**
 * Endpoints de autenticación y sesiones.
 *
 * Funcionalidades:
 * - Login: autentica con email/contraseña y emite un JWT.
 * - Registro: crea el ganadero y realiza auto-login.
 * - Sesión actual (me): devuelve los datos básicos del ganadero autenticado.
 * - Logout y revocación: invalida tokens persistidos para cerrar sesión.
 * - Recuperación de contraseña: inicia y confirma el reseteo vía token temporal.
 *
 * Convención:
 * - El JWT se envía en el header Authorization: Bearer <token>.
 */
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
    /**
     * Autentica credenciales y retorna:
     * - token: JWT firmado con expiración (24h).
     * - ganadero: datos mínimos del usuario.
     *
     * También persiste el token en BD para permitir revocación de sesiones.
     */
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
    /**
     * Registra un ganadero.
     *
     * Validaciones:
     * - firstName/lastName obligatorios
     * - email con formato básico
     * - password mínimo 8 caracteres
     *
     * Comportamiento:
     * - Normaliza el email a minúsculas.
     * - Luego realiza auto-login y entrega un JWT.
     */
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
    /**
     * Retorna la información básica del ganadero autenticado (extraído del SecurityContext).
     */
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
    /**
     * Revoca el token actual (cierre de sesión).
     */
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authTokenService.revokeToken(token);
            return ResponseEntity.ok(Map.of("message", "Sesión cerrada exitosamente"));
        }
        return ResponseEntity.status(400).body(Map.of("message", "Token inválido"));
    }

    @GetMapping("/sessions")
    /**
     * Devuelve las sesiones activas (tokens no revocados) del ganadero autenticado.
     */
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
    /**
     * Revoca todos los tokens del ganadero autenticado (cierre de sesión en todos los dispositivos).
     */
    public ResponseEntity<?> revokeAll(Authentication authentication) {
        String email = authentication.getName();
        Ganadero ganadero = ganaderoService.buscarPorCorreo(email).orElseThrow();
        authTokenService.revokeAllTokens(ganadero.getId());
        return ResponseEntity.ok(Map.of("message", "Todas las sesiones han sido cerradas"));
    }

    /**
    * Inicia el proceso de restablecimiento de contraseña. Siempre devuelve 200 para que los atacantes no puedan
    * enumerar qué correos electrónicos están registrados. Si el correo electrónico existe, se envía un enlace de restablecimiento
    * por correo electrónico de forma asíncrona (el token es válido durante 30 minutos).
    */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = safeTrim(body.get("email"));
        if (email != null && !email.isBlank()) {
            try {
                passwordResetService.requestReset(email);
            } catch (Exception e) {
                // Se omite el error para mantener una respuesta neutral.
            }
        }
        return ResponseEntity.ok(Map.of(
                "message", "Si el correo existe, recibirás un enlace para restablecer tu contraseña."
        ));
    }

    /**
     * Confirma el restablecimiento: valida el token y actualiza la contraseña.
     */
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
