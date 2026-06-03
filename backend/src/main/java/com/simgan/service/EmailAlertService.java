package com.simgan.service;

import com.simgan.dto.NdviDto;
import com.simgan.entity.Alert;
import com.simgan.entity.NdviCalibrationJob;
import com.simgan.entity.Parcel;
import com.simgan.entity.Terrain;
import com.simgan.repository.AlertRepository;
import com.simgan.repository.GanaderoRepository;
import com.simgan.repository.NdviCalibrationJobRepository;
import com.simgan.repository.TerrainRepository;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailAlertService {

  private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final BrevoMailClient brevoMailClient;
    private final GanaderoRepository ganaderoRepository;
  private final AlertRepository alertRepository;
    private final NdviCalibrationJobRepository calibrationJobRepository;
    private final TerrainRepository terrainRepository;
    private final NdviRecommendationService recommendationService;

    @Value("${spring.mail.from:SIMGAN <no-reply@simgan.local>}")
    private String fromAddress;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    /** When false, the same-day duplicate-alert guard is bypassed (handy for testing). */
    @Value("${app.alerts.dedup-enabled:true}")
    private boolean dedupEnabled;

    /**
     * Sends an HTML alert email to the farm owner.
     * Called asynchronously so it never blocks the alert-creation request.
     *
     * IMPORTANT: this method runs in a separate thread (no open JPA session).
     * All lazy associations (Parcel → Terrain → Farm → Ganadero) are re-fetched
     * via a single JOIN FETCH query before accessing any field on them.
     */
    @Async
    public void sendAlertEmail(Alert alert) {
        if (!isDeliveryConfigured()) {
            log.warn("No email transport configured (no BREVO_API_KEY and no SMTP). Skipping alert email for alertId={}",
                    alert != null ? alert.getId() : null);
            return;
        }

        if (alert == null || alert.getId() == null) {
            log.warn("sendAlertEmail received a null alert or one without an ID. Skipping.");
            return;
        }

        // Re-fetch with all lazy associations loaded so this async thread can navigate them safely.
        Alert fullAlert = alertRepository.findByIdWithFullGraph(alert.getId()).orElse(null);
        if (fullAlert == null) {
            log.warn("Alert {} not found after re-fetch. Skipping email.", alert.getId());
            return;
        }

        if (dedupEnabled && isDuplicateTypeForToday(fullAlert)) {
            log.info("Duplicate alert email skipped for parcel {} and type {} (alertId={})",
                    fullAlert.getParcel().getId(), fullAlert.getAlertType(), fullAlert.getId());
            return;
        }

        String recipientEmail = resolveRecipientEmail(fullAlert);
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("No email found for parcel {}. Skipping alert email.", fullAlert.getParcel().getId());
            return;
        }

        boolean sent = deliver(recipientEmail, buildSubject(fullAlert), buildHtmlBody(fullAlert));
        if (sent) {
            log.info("Alert email sent to {} for alert {} (parcel: {})",
                    recipientEmail, fullAlert.getId(), fullAlert.getParcel().getName());
        } else {
            log.error("Failed to send alert email to {} for alert {}", recipientEmail, fullAlert.getId());
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
            case POTRERO_RECUPERADO -> "🟢";
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
            case POTRERO_RECUPERADO -> "#16a34a";
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
              case POTRERO_RECUPERADO -> "Potrero recuperado";
            };
          }

    // ===========================================================================
    // Notificaciones operativas (auto-calibración, monitoreo NDVI semanal)
    // ===========================================================================

    /**
     * Notifies the farm owner when a 12-month auto-calibration job finishes
     * (success or failure). Re-fetches the job with the full Terrain → Farm →
     * Ganadero graph so this @Async thread can navigate lazy associations.
     */
    @Async
    public void sendAutoCalibrationCompletedEmail(Long jobId) {
        if (jobId == null) return;
        if (!isDeliveryConfigured()) {
            log.warn("No email transport configured. Skipping auto-calibration email for jobId={}", jobId);
            return;
        }

        NdviCalibrationJob job = calibrationJobRepository.findByIdWithFullGraph(jobId).orElse(null);
        if (job == null) {
            log.warn("Auto-calibration job {} not found. Skipping email.", jobId);
            return;
        }

        Terrain terrain = job.getTerrain();
        String recipient = (terrain != null && terrain.getFarm() != null && terrain.getFarm().getGanadero() != null)
                ? terrain.getFarm().getGanadero().getCorreo() : null;
        if (!StringUtils.hasText(recipient)) {
            log.warn("Auto-calibration job {} has no recipient email. Skipping.", jobId);
            return;
        }

        String subject = job.getStatus() == NdviCalibrationJob.Status.COMPLETED
                ? String.format("[SIMGAN] ✅ Auto-calibración NDVI completada — %s", terrain.getName())
                : String.format("[SIMGAN] ⚠ Auto-calibración NDVI falló — %s", terrain.getName());

        deliver(recipient.trim(), subject, buildAutoCalibrationHtml(job));
    }

    private String buildAutoCalibrationHtml(NdviCalibrationJob job) {
        Terrain terrain = job.getTerrain();
        String farmName = terrain.getFarm() != null ? terrain.getFarm().getName() : "—";
        boolean ok = job.getStatus() == NdviCalibrationJob.Status.COMPLETED;

        String headerColor = ok ? "#16a34a" : "#dc2626";
        String headerLabel = ok ? "Auto-calibración completada" : "Auto-calibración fallida";
        String range = (job.getRangeStart() != null && job.getRangeEnd() != null)
                ? job.getRangeStart() + " a " + job.getRangeEnd() : "—";

        String thresholds = ok && job.getThresholdLow() != null && job.getThresholdHigh() != null
                ? String.format("Umbral ALERTA (p25): <strong>%.4f</strong> &nbsp;·&nbsp; Umbral ÓPTIMO (p75): <strong>%.4f</strong>",
                        job.getThresholdLow(), job.getThresholdHigh())
                : "—";

        String details = ok
                ? String.format("Se procesaron %d escenas Sentinel-2 en %d semanas. Los nuevos umbrales se aplicarán automáticamente a todos los potreros del terreno.",
                        job.getScenesProcessed() == null ? 0 : job.getScenesProcessed(),
                        job.getWeeksCompleted() == null ? 0 : job.getWeeksCompleted())
                : ("No fue posible completar la calibración. Detalle: "
                        + (job.getErrorMessage() != null ? job.getErrorMessage() : "error desconocido"));

        String dashboardUrl = appBaseUrl + "/terrains/" + terrain.getId() + "/ndvi/calibration-auto";

        return """
                <!DOCTYPE html>
                <html lang="es"><body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:24px 0;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.12);">
                        <tr>
                          <td style="background:#15532e;padding:28px 32px;">
                            <h1 style="margin:0;color:#ffffff;font-size:24px;font-weight:700;letter-spacing:1px;">SIMGAN</h1>
                            <p style="margin:4px 0 0;color:#86efac;font-size:13px;">Calibración automática NDVI</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="background:%s;padding:12px 32px;">
                            <p style="margin:0;color:#ffffff;font-weight:700;font-size:14px;text-transform:uppercase;letter-spacing:1px;">%s</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 32px;color:#374151;font-size:14px;line-height:1.6;">
                            <h2 style="margin:0 0 6px;color:#111827;font-size:20px;">Terreno: %s</h2>
                            <p style="margin:0 0 16px;color:#6b7280;font-size:13px;">Finca: <strong>%s</strong> &nbsp;·&nbsp; Rango analizado: %s</p>
                            <div style="background:#f9fafb;border-radius:8px;padding:16px;margin-bottom:20px;">
                              <p style="margin:0 0 6px;color:#6b7280;font-size:12px;text-transform:uppercase;letter-spacing:0.5px;">Resumen</p>
                              <p style="margin:0;color:#374151;font-size:14px;">%s</p>
                            </div>
                            <p style="margin:0 0 24px;color:#374151;font-size:14px;">%s</p>
                            <p style="margin:24px 0 0;text-align:center;">
                              <a href="%s" style="display:inline-block;background:#15532e;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:8px;font-size:14px;font-weight:600;">Ver detalles de la calibración →</a>
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="background:#f9fafb;padding:16px 32px;border-top:1px solid #e5e7eb;">
                            <p style="margin:0;color:#9ca3af;font-size:11px;text-align:center;">Mensaje automático de SIMGAN. No responder a este correo.</p>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(headerColor, headerLabel, terrain.getName(), farmName, range, thresholds, details, dashboardUrl);
    }

    /**
     * Notifies the farm owner that a weekly NDVI capture finished for one
     * terrain. Skips silently if the run produced no records (so the owner
     * isn't spammed when there were no usable Sentinel scenes that week).
     */
    @Async
    public void sendWeeklyNdviSummaryEmail(Long terrainId, Map<String, Object> result) {
        if (terrainId == null || result == null) return;

        Object recordsObj = result.get("recordsProcessed");
        int records = recordsObj instanceof Number n ? n.intValue() : 0;
        if (records <= 0) {
            log.info("Skipping weekly NDVI email for terrain {} — no records processed.", terrainId);
            return;
        }

        if (!isDeliveryConfigured()) {
            log.warn("No email transport configured. Skipping weekly NDVI email for terrainId={}", terrainId);
            return;
        }

        Terrain terrain = terrainRepository.findByIdWithFullGraph(terrainId).orElse(null);
        if (terrain == null) {
            log.warn("Terrain {} not found. Skipping weekly NDVI email.", terrainId);
            return;
        }

        String recipient = (terrain.getFarm() != null && terrain.getFarm().getGanadero() != null)
                ? terrain.getFarm().getGanadero().getCorreo() : null;
        if (!StringUtils.hasText(recipient)) {
            log.warn("Terrain {} has no recipient email. Skipping weekly NDVI email.", terrainId);
            return;
        }

        // Rotation recommendations are computed from the freshly stored NDVI records
        // and included in this same email so the owner gets the full weekly picture.
        List<NdviDto.RotationRecommendation> recommendations;
        try {
            recommendations = recommendationService.getRotationRecommendations(terrainId);
        } catch (Exception e) {
            log.warn("Could not load rotation recommendations for terrain {}: {}", terrainId, e.getMessage());
            recommendations = List.of();
        }

        String subject = String.format("[SIMGAN] 🛰 Análisis NDVI semanal — %s", terrain.getName());
        deliver(recipient.trim(), subject, buildWeeklyNdviHtml(terrain, result, recommendations));
    }

    private String buildWeeklyNdviHtml(Terrain terrain, Map<String, Object> result,
                                       List<NdviDto.RotationRecommendation> recommendations) {
        String farmName = terrain.getFarm() != null ? terrain.getFarm().getName() : "—";
        String source = String.valueOf(result.getOrDefault("source", "—"));
        int records = ((Number) result.getOrDefault("recordsProcessed", 0)).intValue();
        int scenes = ((Number) result.getOrDefault("scenesProcessed", 0)).intValue();
        int dates = ((Number) result.getOrDefault("datesProcessed", 0)).intValue();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String dashboardUrl = appBaseUrl + "/terrains/" + terrain.getId() + "/ndvi";
        String rotationHtml = buildRotationSectionHtml(recommendations);

        return """
                <!DOCTYPE html>
                <html lang="es"><body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:24px 0;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.12);">
                        <tr>
                          <td style="background:#15532e;padding:28px 32px;">
                            <h1 style="margin:0;color:#ffffff;font-size:24px;font-weight:700;letter-spacing:1px;">SIMGAN</h1>
                            <p style="margin:4px 0 0;color:#86efac;font-size:13px;">Análisis NDVI semanal</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="background:#2563eb;padding:12px 32px;">
                            <p style="margin:0;color:#ffffff;font-weight:700;font-size:14px;text-transform:uppercase;letter-spacing:1px;">🛰 Nueva imagen procesada</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 32px;color:#374151;font-size:14px;line-height:1.6;">
                            <h2 style="margin:0 0 6px;color:#111827;font-size:20px;">Terreno: %s</h2>
                            <p style="margin:0 0 20px;color:#6b7280;font-size:13px;">Finca: <strong>%s</strong> &nbsp;·&nbsp; Captura: %s</p>
                            <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f9fafb;border-radius:8px;padding:16px;margin-bottom:20px;">
                              <tr>
                                <td style="padding:8px 12px;text-align:center;">
                                  <p style="margin:0;color:#6b7280;font-size:11px;">Potreros con NDVI</p>
                                  <p style="margin:4px 0 0;font-size:18px;font-weight:700;color:#15532e;">%d</p>
                                </td>
                                <td style="padding:8px 12px;text-align:center;">
                                  <p style="margin:0;color:#6b7280;font-size:11px;">Escenas usadas</p>
                                  <p style="margin:4px 0 0;font-size:18px;font-weight:700;color:#15532e;">%d</p>
                                </td>
                                <td style="padding:8px 12px;text-align:center;">
                                  <p style="margin:0;color:#6b7280;font-size:11px;">Fechas</p>
                                  <p style="margin:4px 0 0;font-size:18px;font-weight:700;color:#15532e;">%d</p>
                                </td>
                                <td style="padding:8px 12px;text-align:center;">
                                  <p style="margin:0;color:#6b7280;font-size:11px;">Fuente</p>
                                  <p style="margin:4px 0 0;font-size:13px;font-weight:700;color:#15532e;">%s</p>
                                </td>
                              </tr>
                            </table>
                            <p style="margin:0 0 24px;color:#374151;font-size:14px;">
                              Si algún potrero queda por debajo del umbral de alerta, recibirás un correo aparte. Revisa el dashboard para ver el NDVI y la biomasa por potrero.
                            </p>
                            %s
                            <p style="margin:24px 0 0;text-align:center;">
                              <a href="%s" style="display:inline-block;background:#15532e;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:8px;font-size:14px;font-weight:600;">Ver dashboard NDVI →</a>
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="background:#f9fafb;padding:16px 32px;border-top:1px solid #e5e7eb;">
                            <p style="margin:0;color:#9ca3af;font-size:11px;text-align:center;">Mensaje automático de SIMGAN. No responder a este correo.</p>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(terrain.getName(), farmName, today, records, scenes, dates, source, rotationHtml, dashboardUrl);
    }

    /**
     * Builds the "Recomendaciones de rotación" block for the weekly NDVI email.
     * Returns a friendly note when there are no changes to recommend.
     */
    private String buildRotationSectionHtml(List<NdviDto.RotationRecommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return """
                    <div style="background:#f0fdf4;border:1px solid #bbf7d0;border-radius:8px;padding:14px 16px;margin-bottom:8px;">
                      <p style="margin:0;color:#15803d;font-size:13px;">✓ Sin cambios de rotación recomendados esta semana. Los potreros están dentro de sus umbrales.</p>
                    </div>
                    """;
        }

        StringBuilder rows = new StringBuilder();
        for (NdviDto.RotationRecommendation r : recommendations) {
            String urgencyColor = switch (r.getUrgency() == null ? "" : r.getUrgency()) {
                case "URGENTE" -> "#dc2626";
                case "ALTA"    -> "#f97316";
                case "MEDIA"   -> "#2563eb";
                default         -> "#6b7280";
            };
            String ndvi = r.getCurrentNdvi() != null ? String.format("%.2f", r.getCurrentNdvi()) : "—";
            rows.append(String.format("""
                    <tr>
                      <td style="padding:8px 10px;border-bottom:1px solid #e5e7eb;font-size:13px;color:#111827;font-weight:600;">%s</td>
                      <td style="padding:8px 10px;border-bottom:1px solid #e5e7eb;font-size:12px;color:#374151;">%s → <strong>%s</strong></td>
                      <td style="padding:8px 10px;border-bottom:1px solid #e5e7eb;font-size:12px;text-align:center;">
                        <span style="background:%s;color:#ffffff;border-radius:6px;padding:2px 8px;font-size:11px;font-weight:700;">%s</span>
                      </td>
                      <td style="padding:8px 10px;border-bottom:1px solid #e5e7eb;font-size:12px;color:#6b7280;">NDVI %s · %s</td>
                    </tr>
                    """,
                    r.getParcelName(),
                    statusLabel(r.getCurrentStatus()),
                    statusLabel(r.getRecommendedStatus()),
                    urgencyColor,
                    r.getUrgency() == null ? "—" : r.getUrgency(),
                    ndvi,
                    r.getReason() == null ? "" : r.getReason()));
        }

        return """
                <div style="margin-bottom:20px;">
                  <p style="margin:0 0 8px;color:#111827;font-size:14px;font-weight:700;">🔄 Recomendaciones de rotación</p>
                  <table width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
                    <tr style="background:#f9fafb;">
                      <td style="padding:8px 10px;font-size:11px;color:#6b7280;text-transform:uppercase;">Potrero</td>
                      <td style="padding:8px 10px;font-size:11px;color:#6b7280;text-transform:uppercase;">Acción</td>
                      <td style="padding:8px 10px;font-size:11px;color:#6b7280;text-transform:uppercase;text-align:center;">Urgencia</td>
                      <td style="padding:8px 10px;font-size:11px;color:#6b7280;text-transform:uppercase;">Motivo</td>
                    </tr>
                    %s
                  </table>
                </div>
                """.formatted(rows.toString());
    }

    private String statusLabel(Parcel.ParcelStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case DISPONIBLE  -> "Disponible";
            case EN_USO      -> "En uso";
            case EN_DESCANSO -> "En descanso";
        };
    }

    // ===========================================================================
    // Email delivery (Brevo HTTP API preferred, SMTP fallback for local dev)
    // ===========================================================================

    /** True when at least one email transport is available. */
    private boolean isDeliveryConfigured() {
        return brevoMailClient.isConfigured() || mailSenderProvider.getIfAvailable() != null;
    }

    /**
     * Delivers one HTML email. Uses the Brevo HTTP API when configured
     * (works on Railway, which blocks SMTP); otherwise falls back to SMTP
     * for local development. Never throws — returns false on failure.
     */
    private boolean deliver(String to, String subject, String htmlBody) {
        String[] from = parseFrom(fromAddress);

        if (brevoMailClient.isConfigured()) {
            return brevoMailClient.send(from[0], from[1], to, subject, htmlBody);
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("No email transport configured. Skipping email to {} subject='{}'", to, subject);
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("SMTP email sent to {} subject='{}'", to, subject);
            return true;
        } catch (Exception e) {
            log.error("SMTP email to {} failed: {}", to, e.getMessage());
            return false;
        }
    }

    /** Splits a "Name &lt;email&gt;" string into [name, email]; name is null when absent. */
    private String[] parseFrom(String raw) {
        if (raw == null) {
            return new String[]{ null, "no-reply@simgan.local" };
        }
        int lt = raw.indexOf('<');
        int gt = raw.indexOf('>');
        if (lt >= 0 && gt > lt) {
            String name = raw.substring(0, lt).trim();
            String email = raw.substring(lt + 1, gt).trim();
            return new String[]{ name.isEmpty() ? null : name, email };
        }
        return new String[]{ null, raw.trim() };
    }
}