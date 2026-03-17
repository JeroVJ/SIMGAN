package com.simgan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.simgan.entity.Sensor;
import com.simgan.repository.SensorRepository;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class MqttBrokerService {

    @Autowired
    private SensorRepository sensorRepository;

    // Cache de configuraciones guardadas
    private Map<Long, MqttSensorConfig> configCache = new HashMap<>();

    /**
     * Guardar y validar configuración MQTT para un sensor
     */
    public MqttSensorConfig saveSensorMqttConfig(Long sensorId, MqttConfigRequest request) {
        try {
            // Validar datos
            if (request.getBrokerUrl() == null || request.getBrokerUrl().isEmpty()) {
                throw new IllegalArgumentException("URL del broker requerida");
            }
            if (request.getTopic() == null || request.getTopic().isEmpty()) {
                throw new IllegalArgumentException("Topic MQTT requerido");
            }

            // Obtener sensor
            Sensor sensor = sensorRepository.findById(sensorId)
                    .orElseThrow(() -> new RuntimeException("Sensor no encontrado"));

            // Guardar configuración en la entidad Sensor
            sensor.setMqttTopic(request.getTopic());
            sensor.setMqttBrokerUrl(request.getBrokerUrl());
            sensor.setClientId(request.getClientId() != null ? request.getClientId() : "sensor-" + sensorId);
            sensor.setConnected(false); // Aún no conectado
            sensorRepository.save(sensor);

            log.info("✓ Configuración MQTT guardada para sensor {}", sensorId);

            // Crear respuesta
            MqttSensorConfig config = MqttSensorConfig.builder()
                    .sensorId(sensorId)
                    .brokerUrl(request.getBrokerUrl())
                    .username(request.getUsername())
                    .topic(request.getTopic())
                    .clientId(sensor.getClientId())
                    .state("Configuración guardada - listo para conectar")
                    .build();

            // Cachear
            configCache.put(sensorId, config);

            return config;
        } catch (Exception e) {
            log.error("Error guardando configuración MQTT: {}", e.getMessage());
            throw new RuntimeException("Error guardando configuración: " + e.getMessage());
        }
    }

    /**
     * Obtener configuración MQTT guardada para un sensor
     */
    public MqttSensorConfig getSensorMqttConfig(Long sensorId) {
        // Verificar cache primero
        if (configCache.containsKey(sensorId)) {
            return configCache.get(sensorId);
        }

        Sensor sensor = sensorRepository.findById(sensorId)
                .orElseThrow(() -> new RuntimeException("Sensor no encontrado"));

        if (sensor.getMqttBrokerUrl() == null) {
            throw new RuntimeException("Sensor no tiene configuración MQTT");
        }

        MqttSensorConfig config = MqttSensorConfig.builder()
                .sensorId(sensorId)
                .brokerUrl(sensor.getMqttBrokerUrl())
                .username("SIMGAN") // Mostrar usuario actual
                .topic(sensor.getMqttTopic() != null ? sensor.getMqttTopic() : "sensor/" + sensorId + "/data")
                .clientId(sensor.getClientId() != null ? sensor.getClientId() : "sensor-" + sensorId)
                .state(sensor.getConnected() ? "Conectado a MQTT" : "Esperando conexión")
                .build();

        configCache.put(sensorId, config);
        return config;
    }

    /**
     * DTO para recibir configuración del frontend
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class MqttConfigRequest {
        private String brokerUrl;      // ssl://host:8883
        private String username;        // SIMGAN
        private String password;        // simgan12
        private String topic;           // sensor/5/data
        private String clientId;        // sensor-client-xxx (opcional)
    }

    /**
     * DTO para enviar configuración al frontend
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class MqttSensorConfig {
        private Long sensorId;
        private String brokerUrl;
        private String username;
        private String topic;
        private String clientId;
        private String state;
    }
}
