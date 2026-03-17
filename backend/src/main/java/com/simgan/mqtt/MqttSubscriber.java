package com.simgan.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simgan.entity.LecturaSensor;
import com.simgan.entity.Sensor;
import com.simgan.service.SensorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para procesar mensajes MQTT
 * Llamado por MqttClientService cuando llegan mensajes
 */
@Service
@Slf4j
public class MqttSubscriber {

    @Autowired
    private SensorService sensorService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Procesar mensaje MQTT recibido
     */
    public void procesarMensajeMqtt(Sensor sensor, String payload) {
        try {
            log.info("📨 Procesando mensaje MQTT para sensor {}: {}", sensor.getId(), payload);
            
            // Procesar a través del servicio
            sensorService.procesarLectura(sensor, payload);
            
        } catch (Exception e) {
            log.error("❌ Error procesando mensaje MQTT: {}", e.getMessage());
        }
    }

    /**
     * Convertir JSON a LecturaSensor (legacy)
     */
    public LecturaSensor convertir(String mensaje) throws Exception {
        return objectMapper.readValue(mensaje, LecturaSensor.class);
    }
}