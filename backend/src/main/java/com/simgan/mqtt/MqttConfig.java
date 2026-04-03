package com.simgan.mqtt;

import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

/**
 * MqttConfig heredado - DESHABILITADO
 * 
 * Ahora usamos MqttClientService para conexión dinámica
 * Las configuraciones se cargan desde cada sensor en BD
 */
@Configuration
@Slf4j
public class MqttConfig {
    
    public MqttConfig() {
        log.info("ℹ️ MqttConfig deshabilitado - usando MqttClientService para conexión dinámica");
    }
}
