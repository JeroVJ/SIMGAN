package com.simgan.service;

import com.simgan.entity.Alert;
import com.simgan.repository.AlertRepository;
import com.simgan.repository.GanaderoRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailAlertService {

  private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final GanaderoRepository ganaderoRepository;
  private final AlertRepository alertRepository;

    @Value("${spring.mail.from:SIMGAN <no-reply@simgan.local>}")
    private String fromAddress;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    /**
     * Sends an HTML alert email to the farm owner.
     * Called asynchronously so it never blocks the alert-creation request.
     */
    @Async
    public void sendAlertEmail(Alert alert) {
      JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
      if (mailSender == null) {
        log.warn("SMTP not configured (JavaMailSender unavailable). Skipping alert email for alertId={}",
            alert != null ? alert.getId() : null);
        return;
      }

      if (isDuplicateTypeForToday(alert)) {
        log.info("Duplicate alert email skipped for parcel {} and type {} (alertId={})",
            alert.getParcel().getId(), alert.getAlertType(), alert.getId());
        return;
      }

        String recipientEmail = resolveRecipientEmail(alert);

        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("No email found for parcel {}. Skipping alert email.", alert.getParcel().getId());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(recipientEmail);
            helper.setSubject(buildSubject(alert));
            helper.setText(buildHtmlBody(alert), true);

            mailSender.send(message);
            log.info("Alert email sent to {} for alert {} (parcel: {})",
                    recipientEmail, alert.getId(), alert.getParcel().getName());

        } catch (Exception e) {
            log.error("Failed to send alert email to {}: {}", recipientEmail, e.getMessage());
        }
    }

    private String resolveRecipientEmail(Alert alert) {
      if (alert == null || alert.getParcel() == null) {
        return null;
      }

      if (alert.getParcel().getTerrain() != null
          && alert.getParcel().getTerrain().getFarm() != null
          && alert.getParcel().getTerrain().getFarm().getGanadero() != null) {
        String directEmail = alert.getParcel().getTerrain().getFarm().getGanadero().getCorreo();
        if (StringUtils.hasText(directEmail)) {
          return directEmail.trim();
        }
      }

      return ganaderoRepository
          .findEmailByParcelId(alert.getParcel().getId())
          .map(String::trim)
          .filter(StringUtils::hasText)
          .orElse(null);
    }

      private boolean isDuplicateTypeForToday(Alert alert) {
        if (alert == null || alert.getParcel() == null || alert.getAlertType() == null) {
          return false;
        }

        List<Alert> sameDayAlerts = alertRepository.findByParcelIdAndDate(
            alert.getParcel().getId(),
            LocalDate.now()
        );

        return sameDayAlerts.stream().anyMatch(existing ->
            existing.getAlertType() == alert.getAlertType()
                && (alert.getId() == null || !alert.getId().equals(existing.getId()))
        );
      }

        private String buildSubject(Alert alert) {
          String typeEmoji = switch (alert.getAlertType()) {
            case ESTADO_FORRAJE_BAJO_O_EN_UMBRAL -> "🔴";
            case POTRERO_ENCHARCADO -> "🔵";
            case POTRERO_CON_ESTRES_HIDRICO -> "🟠";
        };
          return String.format("[SIMGAN] %s Alerta %s - Potrero %s",
              typeEmoji, alertTypeLabel(alert), alert.getParcel().getName());
    }

        private String buildHtmlBody(Alert alert) {
        String terrainName = alert.getParcel().getTerrain() != null
                ? alert.getParcel().getTerrain().getName() : "—";
        String farmName = (alert.getParcel().getTerrain() != null
                && alert.getParcel().getTerrain().getFarm() != null)
                ? alert.getParcel().getTerrain().getFarm().getName() : "—";

          String typeColor = switch (alert.getAlertType()) {
            case ESTADO_FORRAJE_BAJO_O_EN_UMBRAL -> "#ef4444";
            case POTRERO_ENCHARCADO -> "#3b82f6";
            case POTRERO_CON_ESTRES_HIDRICO -> "#f97316";
        };

        String createdAt = alert.getCreatedAt() != null
                ? alert.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "—";

        String ndviDashboardUrl = String.format("%s/terrains/%d/ndvi",
                appBaseUrl,
                alert.getParcel().getTerrain() != null ? alert.getParcel().getTerrain().getId() : 0);
        String alertTypeLabel = alertTypeLabel(alert);

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>Alerta SIMGAN</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:24px 0;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:12px;overflow:hidden;
                                    box-shadow:0 2px 8px rgba(0,0,0,0.12);">

                        <!-- Header -->
                        <tr>
                          <td style="background:#15532e;padding:28px 32px;">
                            <h1 style="margin:0;color:#ffffff;font-size:24px;font-weight:700;
                                       letter-spacing:1px;">SIMGAN</h1>
                            <p style="margin:4px 0 0;color:#86efac;font-size:13px;">
                              Sistema Inteligente de Monitoreo Ganadero
                            </p>
                          </td>
                        </tr>

                        <!-- Severity banner -->
                        <tr>
                          <td style="background:%s;padding:12px 32px;">
                            <p style="margin:0;color:#ffffff;font-weight:700;font-size:14px;
                                      text-transform:uppercase;letter-spacing:1px;">
                              ⚠ Alerta %s - %s
                            </p>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:28px 32px;">

                            <h2 style="margin:0 0 6px;color:#111827;font-size:20px;">
                              %s
                            </h2>
                            <p style="margin:0 0 20px;color:#6b7280;font-size:14px;">
                              Finca: <strong>%s</strong> &nbsp;·&nbsp; Terreno: <strong>%s</strong>
                            </p>

                            <!-- Metrics row -->
                            <table width="100%%" cellpadding="0" cellspacing="0"
                                   style="background:#f9fafb;border-radius:8px;padding:16px;
                                          margin-bottom:24px;">
                              <tr>
                                <td style="padding:8px 16px;text-align:center;">
                                  <p style="margin:0;color:#6b7280;font-size:12px;">Tipo</p>
                                  <p style="margin:4px 0 0;font-size:13px;font-weight:600;color:#374151;">
                                    %s
                                  </p>
                                </td>
                              </tr>
                            </table>

                            <!-- Message -->
                            <div style="border-left:4px solid %s;padding:12px 16px;
                                        background:#fefce8;border-radius:0 8px 8px 0;
                                        margin-bottom:24px;">
                              <p style="margin:0;color:#374151;font-size:14px;line-height:1.6;">
                                %s
                              </p>
                            </div>

                            <!-- Timestamp -->
                            <p style="color:#9ca3af;font-size:12px;margin-bottom:24px;">
                              Generada el %s &nbsp;·&nbsp; Potrero: <strong>%s</strong>
                            </p>

                            <!-- CTA button -->
                            <a href="%s"
                               style="display:inline-block;background:#15532e;color:#ffffff;
                                      text-decoration:none;padding:12px 28px;border-radius:8px;
                                      font-size:14px;font-weight:600;">
                              Ver Dashboard NDVI →
                            </a>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="background:#f9fafb;padding:16px 32px;
                                     border-top:1px solid #e5e7eb;">
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
                """.formatted(
                    typeColor,
                    alertTypeLabel,
                        alert.getParcel().getName(),
                        alert.getParcel().getName(),
                        farmName,
                        terrainName,
                        alertTypeLabel,
                    typeColor,
                        alert.getMessage(),
                        createdAt,
                        alert.getParcel().getName(),
                        ndviDashboardUrl
                );
    }

          private String alertTypeLabel(Alert alert) {
            return switch (alert.getAlertType()) {
              case ESTADO_FORRAJE_BAJO_O_EN_UMBRAL -> "Estado de forraje bajo o en umbral";
              case POTRERO_ENCHARCADO -> "Potrero encharcado";
              case POTRERO_CON_ESTRES_HIDRICO -> "Potrero con estrés hídrico";
            };
          }
}