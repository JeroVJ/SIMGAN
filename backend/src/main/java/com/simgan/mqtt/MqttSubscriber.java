package com.simgan.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simgan.entity.LecturaSensor;
import com.simgan.service.SensorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

@Service
public class MqttSubscriber {

    @Autowired
    private SensorService sensorService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void recibirMensaje(Message<String> message) {

        try {

            String payload = message.getPayload();

            LecturaSensor lectura = convertir(payload);

            sensorService.procesarLectura(lectura);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private LecturaSensor convertir(String mensaje) throws Exception {
        return objectMapper.readValue(mensaje, LecturaSensor.class);
    }
}