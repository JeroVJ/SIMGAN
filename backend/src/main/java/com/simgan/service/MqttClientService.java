package com.simgan.service;

import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.simgan.entity.Sensor;
import com.simgan.repository.SensorRepository;

import jakarta.annotation.PostConstruct;

import com.simgan.mqtt.MqttSubscriber;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Service
@Slf4j
public class MqttClientService {

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private MqttSubscriber mqttSubscriber;

    @Value("${mqtt.default.username:SIMGAN}")
    private String defaultMqttUsername;

    @Value("${mqtt.default.password:simgan12}")
    private String defaultMqttPassword;

    private Map<Long, MqttClient> clientMap = new HashMap<>();

    @PostConstruct
    public void reconnectActiveSensorsOnStartup() {
        List<Sensor> activeSensors = sensorRepository.findByConnectedTrue();

        if (activeSensors.isEmpty()) {
            log.info(" No hay sensores MQTT activos para reconectar al iniciar");
            return;
        }

        log.info(" Reconectando {} sensores MQTT activos al iniciar", activeSensors.size());

        for (Sensor sensor : activeSensors) {
            try {
                if (sensor.getMqttBrokerUrl() == null || sensor.getMqttBrokerUrl().isBlank()) {
                    log.warn(" Sensor {} marcado como conectado pero sin broker URL. Se marca desconectado.", sensor.getId());
                    sensor.setConnected(false);
                    sensorRepository.save(sensor);
                    continue;
                }

                connectSensor(
                        sensor.getId(),
                        sensor.getMqttBrokerUrl(),
                        defaultMqttUsername,
                        defaultMqttPassword);
            } catch (Exception e) {
                log.error(" No se pudo reconectar sensor {} al iniciar: {}", sensor.getId(), e.getMessage());
            }
        }
    }

    /**
     * Conectar sensor a MQTT broker
     */
    public void connectSensor(Long sensorId, String brokerUrl, String username, String password) {
        try {
            log.info("🔌 Conectando sensor {} a broker: {}", sensorId, brokerUrl);

            MqttClient existingClient = clientMap.get(sensorId);
            if (existingClient != null) {
                if (existingClient.isConnected()) {
                    existingClient.disconnect();
                }
                existingClient.close();
                clientMap.remove(sensorId);
            }

            // Obtener sensor
            Sensor sensor = sensorRepository.findById(sensorId)
                    .orElseThrow(() -> new RuntimeException("Sensor no encontrado"));

            String clientId = sensor.getClientId();
            String topic = sensor.getMqttTopic();

            if (clientId == null || clientId.isEmpty()) {
                clientId = "sensor-" + sensorId;
            }
            if (topic == null || topic.isEmpty()) {
                topic = "sensor/" + sensorId + "/data";
            }

            // Crear cliente MQTT
            String brokerClean = brokerUrl.startsWith("ssl://") ? brokerUrl : "ssl://" + brokerUrl;
            MqttClient client = new MqttClient(brokerClean, clientId);

            log.info(" Broker: {}", brokerClean);
            log.info(" Client ID: {}", clientId);
            log.info("Topic: {}", topic);

            // Opciones de conexión
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(20);

            // Callback de conexión
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn(" Conexión perdida con MQTT para sensor {}: {}", sensorId, cause.getMessage());
                    sensor.setConnected(false);
                    sensorRepository.save(sensor);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    log.info("📨 Mensaje recibido en topic {}", topic);
                    try {
                        String payload = new String(message.getPayload());
                        log.info("   Payload: {}", payload);
                        
                        // Procesar mensaje usando MqttSubscriber
                        mqttSubscriber.procesarMensajeMqtt(sensor, payload);
                    } catch (Exception e) {
                        log.error(" Error procesando mensaje MQTT: {}", e.getMessage());
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // No se usa para suscriptor
                }
            });

            // Conectar
            client.connect(options);
            log.info("✓ Conectado a MQTT para sensor {}", sensorId);

            // Suscribirse al topic
            client.subscribe(topic, 1);
            log.info("✓ Suscrito al topic: {}", topic);

            // Guardar cliente en cache
            clientMap.put(sensorId, client);

            // Actualizar estado en BD
            sensor.setConnected(true);
            sensorRepository.save(sensor);
            
            log.info("✓ Sensor {} ahora está monitoreando MQTT", sensorId);

        } catch (MqttException e) {
            log.error(" Error conectando a MQTT: {}", e.getMessage());
            throw new RuntimeException("Error conectando a MQTT: " + e.getMessage());
        }
    }

    /**
     * Desconectar sensor de MQTT broker
     */
    public void disconnectSensor(Long sensorId) {
        try {
            MqttClient client = clientMap.get(sensorId);
            if (client != null && client.isConnected()) {
                client.disconnect();
                client.close();
                clientMap.remove(sensorId);
                log.info("✓ Sensor {} desconectado de MQTT", sensorId);

                // Actualizar estado en BD
                Sensor sensor = sensorRepository.findById(sensorId).orElse(null);
                if (sensor != null) {
                    sensor.setConnected(false);
                    sensorRepository.save(sensor);
                }
            }
        } catch (MqttException e) {
            log.error(" Error desconectando MQTT: {}", e.getMessage());
        }
    }  

   

    /**
     * Verificar si sensor está conectado
     */
    public boolean isSensorConnected(Long sensorId) {
        MqttClient client = clientMap.get(sensorId);
        return client != null && client.isConnected();
    }
}
