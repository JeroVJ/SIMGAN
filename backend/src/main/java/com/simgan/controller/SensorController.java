package com.simgan.controller;

import com.simgan.dto.SensorConfigDto;
import com.simgan.dto.SensorCreateDto;
import com.simgan.dto.SensorDto;
import com.simgan.entity.Sensor;
import com.simgan.repository.SensorRepository;
import com.simgan.repository.ParcelRepository;
import com.simgan.entity.Parcel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/sensors")
@RequiredArgsConstructor
public class SensorController {

    private final SensorRepository sensorRepository;
    private final ParcelRepository parcelRepository;

    private SensorDto toDto(Sensor sensor) {
        return SensorDto.builder()
                .id(sensor.getId())
                .name(sensor.getName())
                .ubicacionGeoJson(sensor.getUbicacionGeoJson())
                .mqttTopic(sensor.getMqttTopic())
                .mqttBrokerUrl(sensor.getMqttBrokerUrl())
                .clientId(sensor.getClientId())
                .connected(sensor.getConnected() != null && sensor.getConnected())
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
        
        // Validar y asignar el parcel
        if (sensorCreateDto.getParcelId() != null) {
            Optional<Parcel> parcel = parcelRepository.findById(sensorCreateDto.getParcelId());
            if (parcel.isEmpty()) {
                return ResponseEntity.badRequest().build();
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
        List<Sensor> sensors = sensorRepository.findByParcelId(parcelId);
        List<SensorDto> sensorDtos = sensors.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(sensorDtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SensorDto> findById(@PathVariable Long id) {
        return sensorRepository.findById(id)
                .map(sensor -> ResponseEntity.ok(toDto(sensor)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SensorDto> update(@PathVariable Long id, @RequestBody SensorDto sensorUpdate) {
        return sensorRepository.findById(id)
                .map(sensor -> {
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
        if (sensorRepository.existsById(id)) {
            sensorRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/connect")
    public ResponseEntity<SensorDto> connect(@PathVariable Long id) {
        return sensorRepository.findById(id)
                .map(sensor -> {
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
                    sensor.setConnected(false);
                    Sensor updated = sensorRepository.save(sensor);
                    return ResponseEntity.ok(toDto(updated));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<SensorConfigDto> getStatus(@PathVariable Long id) {
        return sensorRepository.findById(id)
                .map(sensor -> {
                    SensorConfigDto config = SensorConfigDto.builder()
                            .sensorId(sensor.getId())
                            .mqttTopic(sensor.getMqttTopic())
                            .mqttBrokerUrl(sensor.getMqttBrokerUrl())
                            .clientId(sensor.getClientId())
                            .connected(sensor.getConnected() != null && sensor.getConnected())
                            .build();
                    return ResponseEntity.ok(config);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/config")
    public ResponseEntity<SensorConfigDto> updateConfig(@PathVariable Long id, @RequestBody SensorConfigDto.UpdateRequest request) {
        return sensorRepository.findById(id)
                .map(sensor -> {
                    if (request.getMqttTopic() != null) {
                        sensor.setMqttTopic(request.getMqttTopic());
                    }
                    if (request.getMqttBrokerUrl() != null) {
                        sensor.setMqttBrokerUrl(request.getMqttBrokerUrl());
                    }
                    if (request.getClientId() != null) {
                        sensor.setClientId(request.getClientId());
                    }
                    if (request.getConnected() != null) {
                        sensor.setConnected(request.getConnected());
                    }
                    Sensor updated = sensorRepository.save(sensor);
                    SensorConfigDto config = SensorConfigDto.builder()
                            .sensorId(updated.getId())
                            .mqttTopic(updated.getMqttTopic())
                            .mqttBrokerUrl(updated.getMqttBrokerUrl())
                            .clientId(updated.getClientId())
                            .connected(updated.getConnected() != null && updated.getConnected())
                            .build();
                    return ResponseEntity.ok(config);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

