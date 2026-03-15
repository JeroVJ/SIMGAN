package com.simgan.service;

import com.simgan.entity.ClasificacionSensor;
import com.simgan.entity.LecturaSensor;
import com.simgan.entity.Sensor;
import com.simgan.repository.ClasificacionSensorRepository;
import com.simgan.repository.SensorRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SensorService {

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private ClasificacionSensorRepository clasificacionSensorRepository;


    public void procesarLectura(LecturaSensor lectura) {

        // Buscar sensor (ejemplo con id fijo)
        Sensor sensor = sensorRepository.findById(1L)
                .orElseThrow(() -> new RuntimeException("Sensor no encontrado"));

        // Crear clasificacion desde la lectura
        ClasificacionSensor clasificacion =
                ClasificacionSensor.fromLectura(lectura, "F");

        // Asociar sensor
        clasificacion.setSensor(sensor);

        // Guardar en base de datos
        clasificacionSensorRepository.save(clasificacion);
    }
}