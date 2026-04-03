package com.simgan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.simgan.entity.Sensor;
import com.simgan.repository.SensorRepository;
import com.simgan.dto.MqttConfigUpdateDto;
import com.simgan.dto.MqttConfigResponseDto;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class MqttBrokerService {

    @Autowired
    private SensorRepository sensorRepository;

    // Cache de configuraciones guardadas
    private Map<Long, MqttConfigResponseDto> configCache = new HashMap<>();

    /**
     * Guardar y validar configuración MQTT para un sensor
     */
    public MqttConfigResponseDto saveSensorMqttConfig(Long sensorId, MqttConfigUpdateDto request) {
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
            MqttConfigResponseDto config = MqttConfigResponseDto.builder()
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
    public MqttConfigResponseDto getSensorMqttConfig(Long sensorId) {
        // Verificar cache primero
        if (configCache.containsKey(sensorId)) {
            return configCache.get(sensorId);
        }

        Sensor sensor = sensorRepository.findById(sensorId)
                .orElseThrow(() -> new RuntimeException("Sensor no encontrado"));

        if (sensor.getMqttBrokerUrl() == null) {
            throw new RuntimeException("Sensor no tiene configuración MQTT");
        }

        MqttConfigResponseDto config = MqttConfigResponseDto.builder()
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
}
