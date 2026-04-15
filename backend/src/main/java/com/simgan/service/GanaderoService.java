package com.simgan.service;



import com.simgan.entity.Ganadero;
import com.simgan.repository.GanaderoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GanaderoService {

    @Autowired
    private GanaderoRepository ganaderoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.simgan.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailAlertService emailAlertService;

    public Ganadero guardarGanadero(Ganadero ganadero) {
        ganadero.setContrasena(passwordEncoder.encode(ganadero.getContrasena()));
        return ganaderoRepository.save(ganadero);
    }

    @Transactional
    public void initiatePasswordReset(String email) {
        Optional<Ganadero> ganaderoOpt = ganaderoRepository.findByCorreo(email);
        if (ganaderoOpt.isPresent()) {
            Ganadero ganadero = ganaderoOpt.get();
            
            // Delete existing tokens for this user
            passwordResetTokenRepository.deleteByGanaderoId(ganadero.getId());
            
            // Create new token
            String token = UUID.randomUUID().toString();
            com.simgan.entity.PasswordResetToken resetToken = com.simgan.entity.PasswordResetToken.builder()
                    .ganadero(ganadero)
                    .token(token)
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .build();
            
            passwordResetTokenRepository.save(resetToken);
            
            // Send email
            emailAlertService.sendPasswordResetEmail(ganadero, token);
        }
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<com.simgan.entity.PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(token);
        
        if (tokenOpt.isPresent() && tokenOpt.get().isValid()) {
            com.simgan.entity.PasswordResetToken resetToken = tokenOpt.get();
            Ganadero ganadero = resetToken.getGanadero();
            
            ganadero.setContrasena(passwordEncoder.encode(newPassword));
            ganaderoRepository.save(ganadero);
            
            resetToken.setUsedAt(LocalDateTime.now());
            passwordResetTokenRepository.save(resetToken);
            
            return true;
        }
        
        return false;
    }

    public List<Ganadero> listarGanaderos() {
        return ganaderoRepository.findAll();
    }

    public Optional<Ganadero> buscarPorId(Long id) {
        return ganaderoRepository.findById(id);
    }

    public void eliminarGanadero(Long id) {
        ganaderoRepository.deleteById(id);
    }

    public Optional<Ganadero> buscarPorCorreo(String correo) {
        return ganaderoRepository.findByCorreo(correo);
    }

    public Optional<Ganadero> buscarPorIdDocumento(int idDocumento) {
        return ganaderoRepository.findByIdDocumento(idDocumento);
    }
}