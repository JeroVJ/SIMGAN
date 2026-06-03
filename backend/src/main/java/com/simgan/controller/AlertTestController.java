package com.simgan.controller;

import com.simgan.entity.Alert;
import com.simgan.repository.AlertRepository;
import com.simgan.repository.ParcelRepository;
import com.simgan.service.EmailAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint temporal para probar el envío de correo de alertas.
 * ELIMINAR antes de producción.
 */
@RestController
@RequestMapping("/alerts/test")
@RequiredArgsConstructor
@Slf4j
@Profile("!prod")
public class AlertTestController {

    private final ParcelRepository parcelRepository;
    private final AlertRepository alertRepository;
    private final EmailAlertService emailAlertService;

    /**
     * POST /api/alerts/test/send-email?parcelId=1&type=POTRERO_ENCHARCADO
     *
     * Crea una alerta para la parcela indicada y envía el correo.
     * Tipos válidos: ESTADO_FORRAJE_BAJO_O_EN_UMBRAL | POTRERO_ENCHARCADO | POTRERO_CON_ESTRES_HIDRICO
     */
    @PostMapping("/send-email")
    public ResponseEntity<Map<String, Object>> sendTestEmail(
            @RequestParam Long parcelId,
            @RequestParam(defaultValue = "POTRERO_ENCHARCADO") String type) {

        var parcel = parcelRepository.findById(parcelId).orElse(null);
        if (parcel == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Parcela no encontrada: " + parcelId));
        }

        Alert.AlertType alertType;
        try {
            alertType = Alert.AlertType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Tipo inválido. Usa: ESTADO_FORRAJE_BAJO_O_EN_UMBRAL, POTRERO_ENCHARCADO, POTRERO_CON_ESTRES_HIDRICO o POTRERO_RECUPERADO"));
        }

        Alert alert = alertRepository.save(Alert.builder()
                .parcel(parcel)
                .alertType(alertType)
                .message("Alerta de prueba generada manualmente desde /alerts/test/send-email")
                .build());

        emailAlertService.sendAlertEmail(alert);

        log.info("[TEST] Alerta {} creada para parcela '{}' (id={}). Correo enviado de forma asíncrona.",
                alertType, parcel.getName(), parcelId);

        return ResponseEntity.ok(Map.of(
                "alertId", alert.getId(),
                "parcel", parcel.getName(),
                "type", alertType.name(),
                "status", "Correo enviado de forma asíncrona. Revisa los logs del backend."
        ));
    }
}
