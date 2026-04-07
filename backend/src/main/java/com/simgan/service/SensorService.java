package com.simgan.service;

import com.simgan.entity.ClasificacionSensor;
import com.simgan.entity.Alert;
import com.simgan.entity.LecturaSensor;
import com.simgan.entity.Sensor;
import com.simgan.repository.AlertRepository;
import com.simgan.repository.ClasificacionSensorRepository;
import com.simgan.repository.SensorRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Slf4j
public class SensorService {

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private ClasificacionSensorRepository clasificacionSensorRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private EmailAlertService emailAlertService;

    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Procesar lectura MQTT desde el mensaje JSON
     */
    public void procesarLectura(Sensor sensor, String payload) {
        try {
            log.info(" Procesando lectura del sensor {}: {}", sensor.getId(), payload);

            // Parsear JSON
            LecturaSensor lectura = objectMapper.readValue(payload, LecturaSensor.class);

            if (lectura.getTimestamp() == null) {
                lectura.setTimestamp(LocalDateTime.now());
            }

            String tipoSuelo = sensor.getParcel() != null && sensor.getParcel().getSoilType() != null
                    ? sensor.getParcel().getSoilType()
                    : "Franco";

            // Crear clasificación
                ClasificacionSensor clasificacion = clasificarLectura(lectura, tipoSuelo);
            clasificacion.setSensor(sensor);
            clasificacion.setMqttTopic(sensor.getMqttTopic());
            clasificacion.setMqttBrokerUrl(sensor.getMqttBrokerUrl());
            clasificacion.setClientId(sensor.getClientId());
            clasificacion.setConnected(sensor.getConnected());

            if (clasificacion.getEstado() == null) {
                clasificacion.setEstado("SIN_CLASIFICAR");
            }
            if (clasificacion.getConsecuencia() == null) {
                clasificacion.setConsecuencia("SIN_CONSECUENCIA");
            }

            // Guardar en BD
            clasificacionSensorRepository.save(clasificacion);
            createAlertFromSensorState(sensor, clasificacion);
            log.info("✓ Lectura guardada para sensor {} en topic {}", sensor.getId(), sensor.getMqttTopic());

        } catch (Exception e) {
            log.error(" Error procesando lectura para sensor {} con payload {}", sensor.getId(), payload, e);
        }
    }

    /**
     * Procesar lectura desde LecturaSensor (legacy)
     */
    public void procesarLectura(LecturaSensor lectura) {
        try {
            // Buscar sensor (ejemplo con id fijo - debe mejorarse)
            Sensor sensor = sensorRepository.findById(1L)
                    .orElseThrow(() -> new RuntimeException("Sensor no encontrado"));

            procesarLectura(sensor, objectMapper.writeValueAsString(lectura));
        } catch (Exception e) {
            log.error(" Error en procesarLectura legacy: {}", e.getMessage());
        }
    }

    private void createAlertFromSensorState(Sensor sensor, ClasificacionSensor clasificacion) {
        if (sensor == null || sensor.getParcel() == null || clasificacion == null || clasificacion.getEstado() == null) {
            return;
        }

        Alert.AlertType alertType = null;
        String estado = clasificacion.getEstado();
        String message = clasificacion.getConsecuencia();

        if ("SECO".equalsIgnoreCase(estado)) {
            alertType = Alert.AlertType.POTRERO_CON_ESTRES_HIDRICO;
        } else if ("ENCHARCADO".equalsIgnoreCase(estado)) {
            alertType = Alert.AlertType.POTRERO_ENCHARCADO;
        }

        if (alertType != null) {
            if (existsAlertTypeToday(sensor.getParcel().getId(), alertType)) {
                return;
            }

            Alert savedAlert = alertRepository.save(Alert.builder()
                    .parcel(sensor.getParcel())
                    .alertType(alertType)
                    .message(message)
                    .build());
            emailAlertService.sendAlertEmail(savedAlert);
        }
    }

    private boolean existsAlertTypeToday(Long parcelId, Alert.AlertType alertType) {
        return alertRepository.findByParcelIdAndDate(parcelId, LocalDate.now())
                .stream()
                .anyMatch(a -> a.getAlertType() == alertType);
    }

    private ClasificacionSensor clasificarLectura(LecturaSensor lectura, String tipoSuelo) {
        ClasificacionSensor clasificacion = ClasificacionSensor.builder()
                .valorHumedad(lectura.getValorHumedad())
                .timestamp(lectura.getTimestamp())
                .build();

        double humedad = lectura.getValorHumedad();

        // Única regla de clasificación: suelo Franco-arcilloso
        if ("FrancoArcillosa".equals(tipoSuelo) || "Franco-arcilloso".equalsIgnoreCase(tipoSuelo)) {
            if(humedad <0 ){ 
                 clasificacion.setEstado("SENSOR FUERA DE TIERRA");
                clasificacion.setConsecuencia("EL SENSOR ESTA FUERA DE TIERRA. POR FAVOR COLOCARLO EN EL SUELO");
  
            }else if(humedad < 30) {
                clasificacion.setEstado("SECO");
                clasificacion.setConsecuencia("REQUIERE REPOSO EL POTRERO. RECUPERACION BAJA. ESTRES HIDRICO");
            } else if (humedad >= 30 && humedad <= 40) {
                clasificacion.setEstado("BUEN_ESTADO");
                clasificacion.setConsecuencia("CAPACIDAD DE CAMPO");
            } else if (humedad > 40) {
                clasificacion.setEstado("ENCHARCADO");
                clasificacion.setConsecuencia("EXCESO DE AGUA");
            }
        }

        return clasificacion;
    }
}