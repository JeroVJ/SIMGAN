package com.simgan.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Component
/**
 * Utilidad para generar y validar JWT (JSON Web Token).
 *
 * Valores relevantes:
 * - jwt.secret: clave secreta usada para firmar tokens con HS256.
 * - EXPIRATION_TIME: duración del token en milisegundos (86400000 = 24h).
 *
 * Uso:
 * - generateToken(username): crea un token con subject=username.
 * - extractUsername(token): lee el subject del token.
 * - isTokenValid(token, username): valida firma, expiración y coincidencia de subject.
 */
public class JwtUtil {

    @Value("${jwt.secret:mySecretKeyForJwtTokenGenerationThatIsLongEnough}")
    private String secretKey;

    private static final long EXPIRATION_TIME = 86400000; // 24 hours

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean isTokenValid(String token, String username) {
        try {
            String extractedUsername = extractUsername(token);
            return extractedUsername.equals(username) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration()
                .before(new Date());
    }

    public LocalDateTime getExpirationTime() {
        return LocalDateTime.now().plusHours(24);
    }
}
