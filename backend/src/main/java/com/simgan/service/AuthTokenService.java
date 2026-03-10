package com.simgan.service;

import com.simgan.entity.AuthToken;
import com.simgan.entity.Ganadero;
import com.simgan.repository.AuthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final AuthTokenRepository authTokenRepository;

    public AuthToken saveToken(Ganadero ganadero, String token, LocalDateTime expiresAt) {
        AuthToken authToken = AuthToken.builder()
                .ganadero(ganadero)
                .token(token)
                .expiresAt(expiresAt)
                .isRevoked(false)
                .build();
        return authTokenRepository.save(authToken);
    }

    public Optional<AuthToken> findByToken(String token) {
        return authTokenRepository.findByToken(token);
    }

    public boolean isTokenValid(String token) {
        Optional<AuthToken> authToken = authTokenRepository.findByToken(token);
        if (authToken.isEmpty()) {
            return false;
        }
        return authToken.get().isValid();
    }

    public List<AuthToken> getActiveSessions(Long ganaderoId) {
        return authTokenRepository.findByGanaderoIdAndIsRevokedFalse(ganaderoId);
    }

    public void revokeToken(String token) {
        Optional<AuthToken> authToken = authTokenRepository.findByToken(token);
        if (authToken.isPresent()) {
            AuthToken at = authToken.get();
            at.setIsRevoked(true);
            at.setRevokedAt(LocalDateTime.now());
            authTokenRepository.save(at);
        }
    }

    public void revokeAllTokens(Long ganaderoId) {
        List<AuthToken> tokens = authTokenRepository.findByGanaderoId(ganaderoId);
        for (AuthToken token : tokens) {
            token.setIsRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
            authTokenRepository.save(token);
        }
    }
}
