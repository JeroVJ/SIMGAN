package com.simgan.service;

import com.simgan.entity.ClasificacionSensor;
import com.simgan.entity.LecturaSensor;
import com.simgan.entity.Sensor;
import com.simgan.repository.ClasificacionSensorRepository;
import com.simgan.repository.SensorRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SensorService {

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private ClasificacionSensorRepository clasificacionSensorRepository;

    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Procesar lectura MQTT desde el mensaje JSON
     */
    public void procesarLectura(Sensor sensor, String payload) {
        try {
            log.info("📊 Procesando lectura del sensor {}: {}", sensor.getId(), payload);

            // Parsear JSON
            LecturaSensor lectura = objectMapper.readValue(payload, LecturaSensor.class);

            // Crear clasificación
            ClasificacionSensor clasificacion = ClasificacionSensor.fromLectura(lectura, "Automático");
            clasificacion.setSensor(sensor);

            // Guardar en BD
            clasificacionSensorRepository.save(clasificacion);
            log.info("✓ Lectura guardada para sensor {}", sensor.getId());

        } catch (Exception e) {
            log.error("❌ Error procesando lectura: {}", e.getMessage());
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
            log.error("❌ Error en procesarLectura legacy: {}", e.getMessage());
        }
    }
}