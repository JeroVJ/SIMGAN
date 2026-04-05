package com.simgan.controller;

import com.simgan.dto.MqttConfigUpdateDto;
import com.simgan.dto.SensorCreateDto;
import com.simgan.dto.SensorDto;
import com.simgan.dto.ClasificacionSensorDto;
import com.simgan.dto.MqttConfigResponseDto;
import com.simgan.entity.Sensor;
import com.simgan.entity.ClasificacionSensor;
import com.simgan.repository.SensorRepository;
import com.simgan.repository.ParcelRepository;
import com.simgan.repository.ClasificacionSensorRepository;
import com.simgan.entity.Parcel;
import com.simgan.service.MqttBrokerService;
import com.simgan.service.MqttClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/sensors")
@RequiredArgsConstructor
public class SensorController {

    private static final int DEFAULT_POLLING_INTERVAL_MS = 3_600_000;
    private static final int MIN_POLLING_INTERVAL_MS = 60_000;
    private static final double MIN_POLLING_INTERVAL_HOURS = 1.0 / 60.0;

    private final SensorRepository sensorRepository;
    private final ParcelRepository parcelRepository;
    private final ClasificacionSensorRepository clasificacionSensorRepository;
    private final MqttBrokerService mqttBrokerService;
    private final MqttClientService mqttClientService;

    private boolean isIotEnabled(Parcel parcel) {
        return parcel != null
                && parcel.getTerrain() != null
                && parcel.getTerrain().getFarm() != null
                && Boolean.TRUE.equals(parcel.getTerrain().getFarm().getIotEnabled());
    }

    private boolean isIotEnabled(Sensor sensor) {
        return sensor != null && isIotEnabled(sensor.getParcel());
    }

    private SensorDto toDto(Sensor sensor) {
        return SensorDto.builder()
                .id(sensor.getId())
                .name(sensor.getName())
                .ubicacionGeoJson(sensor.getUbicacionGeoJson())
                .mqttTopic(sensor.getMqttTopic())
                .mqttBrokerUrl(sensor.getMqttBrokerUrl())
                .clientId(sensor.getClientId())
                .connected(sensor.getConnected() != null && sensor.getConnected())
                .pollingIntervalMs(sensor.getPollingIntervalMs())
                .parcelId(sensor.getParcel() != null ? sensor.getParcel().getId() : null)
                .build();
    }

    @PostMapping
    public ResponseEntity<SensorDto> create(@RequestBody SensorCreateDto sensorCreateDto) {
        // Crear nueva entidad Sensor
        Sensor sensor = new Sensor();
        sensor.setName(sensorCreateDto.getName());
        sensor.setUbicacionGeoJson(sensorCreateDto.getUbicacionGeoJson());
        sensor.setMqttTopic(sensorCreateDto.getMqttTopic());
        sensor.setMqttBrokerUrl(sensorCreateDto.getMqttBrokerUrl());
        sensor.setClientId(sensorCreateDto.getClientId());
        sensor.setPollingIntervalMs(sensorCreateDto.getPollingIntervalMs() != null ? sensorCreateDto.getPollingIntervalMs() : DEFAULT_POLLING_INTERVAL_MS);
        
        // Validar y asignar el parcel
        if (sensorCreateDto.getParcelId() != null) {
            Optional<Parcel> parcel = parcelRepository.findById(sensorCreateDto.getParcelId());
            if (parcel.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            if (!isIotEnabled(parcel.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            sensor.setParcel(parcel.get());
        }
        
        // Inicializar connected como false
        sensor.setConnected(false);
        
        Sensor created = sensorRepository.save(sensor);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @GetMapping("/parcel/{parcelId}")
    public ResponseEntity<List<SensorDto>> findByParcelId(@PathVariable Long parcelId) {
        Optional<Parcel> parcel = parcelRepository.findById(parcelId);
        if (parcel.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!isIotEnabled(parcel.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Sensor> sensors = sensorRepository.findByParcelId(parcelId);
        List<SensorDto> sensorDtos = sensors.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(sensorDtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SensorDto> findById(@PathVariable Long id) {
        return sensorRepository.findById(id)
                .map(sensor -> {
                    if (!isIotEnabled(sensor)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<SensorDto>build();
                    }
                    return ResponseEntity.ok(toDto(sensor));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/classifications")
    public ResponseEntity<List<ClasificacionSensorDto>> getClassifications(@PathVariable Long id) {
        Optional<Sensor> sensor = sensorRepository.findById(id);
        if (sensor.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!isIotEnabled(sensor.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<ClasificacionSensor> clasificaciones = sensor.get().getClasificaciones();
        List<ClasificacionSensorDto> dtos = clasificaciones.stream()
                .map(c -> ClasificacionSensorDto.builder()
                        .id(c.getId())
                        .valorHumedad(c.getValorHumedad())
                        .timestamp(c.getTimestamp())
                        .estado(c.getEstado())
                        .consecuencia(c.getConsecuencia())
                        .mqttTopic(c.getMqttTopic() != null ? c.getMqttTopic() : sensor.get().getMqttTopic())
                        .mqttBrokerUrl(c.getMqttBrokerUrl() != null ? c.getMqttBrokerUrl() : sensor.get().getMqttBrokerUrl())
                        .clientId(c.getClientId() != null ? c.getClientId() : sensor.get().getClientId())
                        .connected(c.getConnected() != null ? c.getConnected() : sensor.get().getConnected())
                        .sensorId(id)
                        .build())
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    public ResponseEntity<SensorDto> update(@PathVariable Long id, @RequestBody SensorDto sensorUpdate) {
        return sensorRepository.findById(id)
                .map(sensor -> {
                    if (!isIotEnabled(sensor)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<SensorDto>build();
                    }
                    if (sensorUpdate.getName() != null) {
                        sensor.setName(sensorUpdate.getName());
                    }
                    if (sensorUpdate.getUbicacionGeoJson() != null) {
                        sensor.setUbicacionGeoJson(sensorUpdate.getUbicacionGeoJson());
                    }
                    Sensor updated = sensorRepository.save(sensor);
                    return ResponseEntity.ok(toDto(updated));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return sensorRepository.findById(id)
                .map(sensor -> {
                    if (!isIotEnabled(sensor)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<Void>build();
                    }
                    sensorRepository.deleteById(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/connect")
    public ResponseEntity<SensorDto> connect(@PathVariable Long id) {
        return sensorRepository.findById(id)
                .map(sensor -> {
                    if (!isIotEnabled(sensor)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<SensorDto>build();
                    }
                    sensor.setConnected(true);
                    Sensor updated = sensorRepository.save(sensor);
                    return ResponseEntity.ok(toDto(updated));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/disconnect")
    public ResponseEntity<SensorDto> disconnect(@PathVariable Long id) {
        return sensorRepository.findById(id)
                .map(sensor -> {
                    if (!isIotEnabled(sensor)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<SensorDto>build();
                    }
                    sensor.setConnected(false);
                    Sensor updated = sensorRepository.save(sensor);
                    return ResponseEntity.ok(toDto(updated));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<MqttConfigResponseDto> getStatus(@PathVariable Long id) {
        return sensorRepository.findById(id)
                .map(sensor -> {
                    if (!isIotEnabled(sensor)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<MqttConfigResponseDto>build();
                    }
                    MqttConfigResponseDto config = MqttConfigResponseDto.builder()
                            .sensorId(sensor.getId())
                            .brokerUrl(sensor.getMqttBrokerUrl())
                            .topic(sensor.getMqttTopic())
                            .clientId(sensor.getClientId())
                            .pollingIntervalHours((sensor.getPollingIntervalMs() != null ? sensor.getPollingIntervalMs() : DEFAULT_POLLING_INTERVAL_MS) / 3_600_000.0)
                            .pollingIntervalMs(sensor.getPollingIntervalMs() != null ? sensor.getPollingIntervalMs() : DEFAULT_POLLING_INTERVAL_MS)
                            .username("SIMGAN")
                            .state(sensor.getConnected() != null && sensor.getConnected() ? "Conectado" : "Desconectado")
                            .build();
                    return ResponseEntity.ok(config);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/mqtt-config")
    public ResponseEntity<MqttConfigResponseDto> getMqttConfig(@PathVariable Long id) {
        try {
            Optional<Sensor> sensor = sensorRepository.findById(id);
            if (sensor.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            if (!isIotEnabled(sensor.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            MqttConfigResponseDto config = mqttBrokerService.getSensorMqttConfig(id);
            return ResponseEntity.ok(config);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/mqtt-config")
    public ResponseEntity<MqttConfigResponseDto> saveMqttConfig(
            @PathVariable Long id,
            @RequestBody MqttConfigUpdateDto request) {
        try {
            Optional<Sensor> sensor = sensorRepository.findById(id);
            if (sensor.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            if (!isIotEnabled(sensor.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            MqttConfigResponseDto config = mqttBrokerService.saveSensorMqttConfig(id, request);
            
            // Solo intentar conexión MQTT si se proporcionó contraseña
            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                try {
                    mqttClientService.connectSensor(id, request.getBrokerUrl(), request.getUsername(), request.getPassword());
                    config.setState("Conectado a MQTT - Escuchando datos");
                } catch (Exception e) {
                    config.setState("Config guardada - Error en conexión: " + e.getMessage());
                }
            } else {
                config.setState("✓ Configuración actualizada");
            }
            
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/{id}/mqtt-connect")
    public ResponseEntity<String> connectMqtt(@PathVariable Long id) {
        try {
            Sensor sensor = sensorRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Sensor no encontrado"));

            if (!isIotEnabled(sensor)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("IoT deshabilitado para esta finca");
            }

            if (sensor.getMqttBrokerUrl() == null) {
                return ResponseEntity.badRequest().body("Sensor sin configuración MQTT");
            }

            // Conectar con credenciales guardadas (usuario/contraseña por defecto para EMQX)
            mqttClientService.connectSensor(id, sensor.getMqttBrokerUrl(), "SIMGAN", "simgan12");
            return ResponseEntity.ok("✓ Conectado a MQTT");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/mqtt-disconnect")
    public ResponseEntity<String> disconnectMqtt(@PathVariable Long id) {
        try {
            Optional<Sensor> sensor = sensorRepository.findById(id);
            if (sensor.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            if (!isIotEnabled(sensor.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("IoT deshabilitado para esta finca");
            }

            mqttClientService.disconnectSensor(id);
            return ResponseEntity.ok("✓ Desconectado de MQTT");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/parcel/{parcelId}/last-classification")
    public ResponseEntity<Map<String, Object>> getLastClassificationForParcel(@PathVariable Long parcelId) {
        List<Sensor> sensors = sensorRepository.findByParcelId(parcelId);
        Map<String, Object> result = new HashMap<>();
        if (sensors.isEmpty()) {
            result.put("hasSensor", false);
            result.put("estado", null);
        } else {
            result.put("hasSensor", true);
            Optional<ClasificacionSensor> latest = clasificacionSensorRepository
                    .findFirstBySensorParcelIdOrderByTimestampDesc(parcelId);
            result.put("estado", latest.map(ClasificacionSensor::getEstado).orElse(null));
        }
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/config")
    public ResponseEntity<MqttConfigResponseDto> updateConfig(@PathVariable Long id, @RequestBody MqttConfigUpdateDto.UpdateRequest request) {
        return sensorRepository.findById(id)
                .map(sensor -> {
                    if (!isIotEnabled(sensor)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<MqttConfigResponseDto>build();
                    }
                    if (request.getMqttTopic() != null) {
                        sensor.setMqttTopic(request.getMqttTopic());
                    }
                    if (request.getMqttBrokerUrl() != null) {
                        sensor.setMqttBrokerUrl(request.getMqttBrokerUrl());
                    }
                    if (request.getClientId() != null) {
                        sensor.setClientId(request.getClientId());
                    }
                    if (request.getPollingIntervalHours() != null) {
                        sensor.setPollingIntervalMs(Math.max(MIN_POLLING_INTERVAL_MS,
                                (int) Math.round(Math.max(MIN_POLLING_INTERVAL_HOURS, request.getPollingIntervalHours()) * 3_600_000d)));
                    } else if (request.getPollingIntervalMs() != null) {
                        sensor.setPollingIntervalMs(Math.max(MIN_POLLING_INTERVAL_MS, request.getPollingIntervalMs()));
                    }
                    if (request.getConnected() != null) {
                        sensor.setConnected(request.getConnected());
                    }
                    Sensor updated = sensorRepository.save(sensor);
                    MqttConfigResponseDto config = MqttConfigResponseDto.builder()
                            .sensorId(updated.getId())
                            .topic(updated.getMqttTopic())
                            .brokerUrl(updated.getMqttBrokerUrl())
                            .clientId(updated.getClientId())
                            .pollingIntervalHours((updated.getPollingIntervalMs() != null ? updated.getPollingIntervalMs() : DEFAULT_POLLING_INTERVAL_MS) / 3_600_000.0)
                            .pollingIntervalMs(updated.getPollingIntervalMs() != null ? updated.getPollingIntervalMs() : DEFAULT_POLLING_INTERVAL_MS)
                            .username("SIMGAN")
                            .state(updated.getConnected() != null && updated.getConnected() ? "Conectado" : "Desconectado")
                            .build();
                    return ResponseEntity.ok(config);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

