package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "clasificaciones_sensor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClasificacionSensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double valorHumedad;

    private LocalDateTime timestamp;

    private String estado;

    private String consecuencia;

    // MQTT Configuration
    private String mqttTopic;
    private String mqttBrokerUrl;
    private String clientId;
    private Boolean connected;

    @ManyToOne
    @JoinColumn(name = "sensor_id")
    private Sensor sensor;


    

    public static ClasificacionSensor fromLectura(LecturaSensor lectura, String tipoSuelo) {

    ClasificacionSensor clasificacion = ClasificacionSensor.builder()
            .valorHumedad(lectura.getValorHumedad())
            .timestamp(lectura.getTimestamp())
            .build();

    double humedad = lectura.getValorHumedad();

    // Arenosa (A)
    if (tipoSuelo.equals("Arenosa")) {
        if (humedad < 20) {
            clasificacion.setEstado("SECO");
            clasificacion.setConsecuencia("REQUIERE RIEGO");
        } else if (humedad >= 25 && humedad <= 40) {
            clasificacion.setEstado("BUEN_ESTADO");
            clasificacion.setConsecuencia("CAPACIDAD DE CAMPO");
        } else if (humedad > 40) {
            clasificacion.setEstado("ENCHARCADO");
            clasificacion.setConsecuencia("EXCESO DE AGUA");
        }
    }

    // Arenosa franca (AF)
    else if (tipoSuelo.equals("ArenosaFranca")) {
        if (humedad < 20) {
            clasificacion.setEstado("SECO");
            clasificacion.setConsecuencia("REQUIERE RIEGO");
        } else if (humedad >= 25 && humedad <= 40) {
            clasificacion.setEstado("BUEN_ESTADO");
            clasificacion.setConsecuencia("CAPACIDAD DE CAMPO");
        } else if (humedad > 40) {
            clasificacion.setEstado("ENCHARCADO");
            clasificacion.setConsecuencia("EXCESO DE AGUA");
        }
    }

    // Franco arenosa (FA)
    else if (tipoSuelo.equals("FrancaArenosa")) {
        if (humedad < 20) {
            clasificacion.setEstado("SECO");
            clasificacion.setConsecuencia("REQUIERE RIEGO");
        } else if (humedad >= 25 && humedad <= 40) {
            clasificacion.setEstado("BUEN_ESTADO");
            clasificacion.setConsecuencia("CAPACIDAD DE CAMPO");
        } else if (humedad > 40) {
            clasificacion.setEstado("ENCHARCADO");
            clasificacion.setConsecuencia("EXCESO DE AGUA");
        }
    }

    // Franco (F)
    else if (tipoSuelo.equals("Franco")) {
        if (humedad < 20) {
            clasificacion.setEstado("SECO");
            clasificacion.setConsecuencia("REQUIERE RIEGO");
        } else if (humedad >= 25 && humedad <= 40) {
            clasificacion.setEstado("BUEN_ESTADO");
            clasificacion.setConsecuencia("CAPACIDAD DE CAMPO");
        } else if (humedad > 40) {
            clasificacion.setEstado("ENCHARCADO");
            clasificacion.setConsecuencia("EXCESO DE AGUA");
        }
    }

    // Francolimosa (FL)
    else if (tipoSuelo.equals("FrancoLimosa")) {
        if (humedad < 20) {
            clasificacion.setEstado("SECO");
            clasificacion.setConsecuencia("REQUIERE RIEGO");
        } else if (humedad >= 25 && humedad <= 40) {
            clasificacion.setEstado("BUEN_ESTADO");
            clasificacion.setConsecuencia("CAPACIDAD DE CAMPO");
        } else if (humedad > 40) {
            clasificacion.setEstado("ENCHARCADO");
            clasificacion.setConsecuencia("EXCESO DE AGUA");
        }
    }

    // Limosa (L)
    else if (tipoSuelo.equals("Limosa")) {
        if (humedad < 20) {
            clasificacion.setEstado("SECO");
            clasificacion.setConsecuencia("REQUIERE RIEGO");
        } else if (humedad >= 25 && humedad <= 40) {
            clasificacion.setEstado("BUEN_ESTADO");
            clasificacion.setConsecuencia("CAPACIDAD DE CAMPO");
        } else if (humedad > 40) {
            clasificacion.setEstado("ENCHARCADO");
            clasificacion.setConsecuencia("EXCESO DE AGUA");
        }
    }

    // Franco arcillosa (FAr)
    else if (tipoSuelo.equals("FrancoArcillosa")) {
        if (humedad < 20) {
            clasificacion.setEstado("SECO");
            clasificacion.setConsecuencia("REQUIERE RIEGO");
        } else if (humedad >= 25 && humedad <= 40) {
            clasificacion.setEstado("BUEN_ESTADO");
            clasificacion.setConsecuencia("CAPACIDAD DE CAMPO");
        } else if (humedad > 40) {
            clasificacion.setEstado("ENCHARCADO");
            clasificacion.setConsecuencia("EXCESO DE AGUA");
        }
    }

    // Franco arcillo arenosa (FArA)
    else if (tipoSuelo.equals("FrancoArcilloArenosa")) {
        if (humedad < 20) {
            clasificacion.setEstado("SECO");
            clasificacion.setConsecuencia("REQUIERE RIEGO");
        } else if (humedad >= 25 && humedad <= 40) {
            clasificacion.setEstado("BUEN_ESTADO");
            clasificacion.setConsecuencia("CAPACIDAD DE CAMPO");
        } else if (humedad > 40) {
            clasificacion.setEstado("ENCHARCADO");
            clasificacion.setConsecuencia("EXCESO DE AGUA");
        }
    }

    // Franco arcillo limosa (FArL)
    else if (tipoSuelo.equals("FrancoArcilloLimosa")) {
        if (humedad < 20) {
            clasificacion.setEstado("SECO");
            clasificacion.setConsecuencia("REQUIERE RIEGO");
        } else if (humedad >= 25 && humedad <= 40) {
            clasificacion.setEstado("BUEN_ESTADO");
            clasificacion.setConsecuencia("CAPACIDAD DE CAMPO");
        } else if (humedad > 40) {
            clasificacion.setEstado("ENCHARCADO");
            clasificacion.setConsecuencia("EXCESO DE AGUA");
        }
    }

    // Arcillo arenosa (ArA)
    else if (tipoSuelo.equals("ArcilloArenosa")) {
        if (humedad < 20) {
            clasificacion.setEstado("SECO");
            clasificacion.setConsecuencia("REQUIERE RIEGO");
        } else if (humedad >= 25 && humedad <= 40) {
            clasificacion.setEstado("BUEN_ESTADO");
            clasificacion.setConsecuencia("CAPACIDAD DE CAMPO");
        } else if (humedad > 40) {
            clasificacion.setEstado("ENCHARCADO");
            clasificacion.setConsecuencia("EXCESO DE AGUA");
        }
    }

    // Arcillo limosa (ArL)
    else if (tipoSuelo.equals("ArcilloLimosa")) {
        if (humedad < 20) {
            clasificacion.setEstado("SECO");
            clasificacion.setConsecuencia("REQUIERE RIEGO");
        } else if (humedad >= 25 && humedad <= 40) {
            clasificacion.setEstado("BUEN_ESTADO");
            clasificacion.setConsecuencia("CAPACIDAD DE CAMPO");
        } else if (humedad > 40) {
            clasificacion.setEstado("ENCHARCADO");
            clasificacion.setConsecuencia("EXCESO DE AGUA");
        }
    }

    // Arcillosa (Ar)
    else if (tipoSuelo.equals("Arcillosa")) {
        if (humedad < 20) {
            clasificacion.setEstado("SECO");
            clasificacion.setConsecuencia("REQUIERE RIEGO");
        } else if (humedad >= 25 && humedad <= 40) {
            clasificacion.setEstado("BUEN_ESTADO");
            clasificacion.setConsecuencia("CAPACIDAD DE CAMPO");
        } else if (humedad > 40) {
            clasificacion.setEstado("ENCHARCADO");
            clasificacion.setConsecuencia("EXCESO DE AGUA");
        }
    }

    return clasificacion;
}
}