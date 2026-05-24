package com.simgan.service;

import com.simgan.entity.Ganadero;
import com.simgan.entity.PasswordResetToken;
import com.simgan.repository.GanaderoRepository;
import com.simgan.repository.PasswordResetTokenRepository;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * Servicio para restablecimiento de contraseña (forgot/reset).
 *
 * Flujo:
 * - requestReset(email): crea un token temporal (TTL 30 min) y envía un enlace por correo.
 * - confirmReset(token, newPassword): valida token, actualiza contraseña y revoca sesiones.
 *
 * Valores:
 * - TOKEN_TTL_MINUTES: tiempo de vida del token en minutos.
 * - app.base-url: base URL del frontend, usada para construir el enlace /reset-password?token=...
 * - spring.mail.from: remitente del correo.
 */
public class PasswordResetService {

    private static final int TOKEN_TTL_MINUTES = 30;

    private final GanaderoRepository ganaderoRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.from:SIMGAN <no-reply@simgan.local>}")
    private String fromAddress;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    /**
     * Solicita un restablecimiento de contraseña. Devuelve un resultado silencioso en ambos casos, por lo que quien realiza la llamada no puede
     * enumerar las cuentas registradas. Si el correo electrónico coincide con una cuenta, se crea un token
     * y se envía por correo electrónico; de lo contrario, no sucede nada.
     */
    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) return;
        String normalized = email.trim().toLowerCase();

        Optional<Ganadero> match = ganaderoRepository.findByCorreo(normalized);
        if (match.isEmpty()) {
            // intenta con el casing original por si la BD lo guardó sin normalizar.
            match = ganaderoRepository.findByCorreo(email.trim());
        }
        if (match.isEmpty()) {
            log.info("Password reset requested for unknown email ({})", normalized);
            return;
        }
        Ganadero ganadero = match.get();

        tokenRepository.invalidateAllForGanadero(ganadero.getId());

        String token = generateSecureToken();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .ganadero(ganadero)
                .token(token)
                .expiresAt(LocalDateTime.now().plusMinutes(TOKEN_TTL_MINUTES))
                .build();
        tokenRepository.save(resetToken);

        sendResetEmail(ganadero, token);
    }

    /**
     * Confirma el restablecimiento validando el token y actualizando la contraseña.
     *
     * Regla de seguridad:
     * - Revoca todas las sesiones activas del ganadero para que el viejo dispositivo este logged out.
     */
    @Transactional
    public void confirmReset(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token inválido");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres");
        }

        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token inválido o expirado"));

        if (!resetToken.isValid()) {
            throw new IllegalArgumentException("Token inválido o expirado");
        }

        Ganadero ganadero = resetToken.getGanadero();
        ganadero.setContrasena(passwordEncoder.encode(newPassword));
        ganaderoRepository.save(ganadero);

        resetToken.setUsedAt(LocalDateTime.now());
        tokenRepository.save(resetToken);

        // Log out every active session so the old device is forced to re-auth
        try {
            authTokenService.revokeAllTokens(ganadero.getId());
        } catch (Exception e) {
            log.warn("Could not revoke existing sessions for ganadero {}: {}", ganadero.getId(), e.getMessage());
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void sendResetEmail(Ganadero ganadero, String token) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        String resetUrl = appBaseUrl + "/reset-password?token=" + token;

        if (mailSender == null) {
            log.warn("SMTP not configured. Password reset URL for {}: {}", ganadero.getCorreo(), resetUrl);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(ganadero.getCorreo());
            helper.setSubject("[SIMGAN] Restablece tu contraseña");
            helper.setText(buildHtmlBody(ganadero, resetUrl), true);
            mailSender.send(message);
            log.info("Password reset email sent to {}", ganadero.getCorreo());
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", ganadero.getCorreo(), e.getMessage());
        }
    }

    private String buildHtmlBody(Ganadero ganadero, String resetUrl) {
        String greeting = ganadero.getNombreCompleto() != null
                ? "Hola, " + ganadero.getNombreCompleto()
                : "Hola";
        return """
                <!DOCTYPE html>
                <html lang="es">
                <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="padding:24px 0;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:12px;overflow:hidden;
                                    box-shadow:0 2px 8px rgba(0,0,0,0.12);">
                        <tr>
                          <td style="background:#15532e;padding:28px 32px;">
                            <h1 style="margin:0;color:#ffffff;font-size:24px;font-weight:700;
                                       letter-spacing:1px;">SIMGAN</h1>
                            <p style="margin:4px 0 0;color:#86efac;font-size:13px;">
                              Restablecimiento de contraseña
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 32px;color:#374151;font-size:14px;line-height:1.6;">
                            <p style="margin:0 0 16px;">%s,</p>
                            <p style="margin:0 0 16px;">
                              Recibimos una solicitud para restablecer la contraseña de tu cuenta SIMGAN.
                              Haz clic en el botón para crear una nueva contraseña. Este enlace expira en 30 minutos.
                            </p>
                            <p style="margin:24px 0;text-align:center;">
                              <a href="%s" style="display:inline-block;background:#15532e;color:#ffffff;
                                      text-decoration:none;padding:12px 28px;border-radius:8px;
                                      font-size:14px;font-weight:600;">
                                Restablecer contraseña
                              </a>
                            </p>
                            <p style="margin:0 0 8px;font-size:12px;color:#6b7280;">
                              Si el botón no funciona, copia y pega este enlace en tu navegador:
                            </p>
                            <p style="margin:0 0 24px;font-size:12px;color:#15532e;word-break:break-all;">
                              %s
                            </p>
                            <p style="margin:0;font-size:12px;color:#9ca3af;">
                              Si no solicitaste este cambio, puedes ignorar este correo. Tu contraseña no se modificará.
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="background:#f9fafb;padding:16px 32px;border-top:1px solid #e5e7eb;">
                            <p style="margin:0;color:#9ca3af;font-size:11px;text-align:center;">
                              Este es un mensaje automático de SIMGAN. No responder a este correo.
                            </p>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(greeting, resetUrl, resetUrl);
    }
}
