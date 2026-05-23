#include <WiFi.h>
#include <PubSubClient.h>
#include <WiFiClientSecure.h>
#include <esp_sleep.h>

#define SENSOR_PIN 32

// Configuracion de energia y depuracion
const bool DEPURACION = false;
const uint64_t INTERVALO_ENVIO_US = 15ULL * 60ULL * 1000000ULL;  // 15 minutos

// WiFi
const char* ssid = "LOS JUANES";
const char* password = "Juanluiseduardo";

// MQTT
const char* mqtt_server = "q94824c1.ala.eu-central-1.emqxsl.com";
const int mqtt_port = 8883;
const char* mqtt_user = "SIMGAN";
const char* mqtt_password = "simgan12";
const char* mqtt_topic = "sensor/2/data";
const char* mqtt_client_id = "ESP32_HUMEDAD_2";

// Limites de reintento para evitar consumo alto cuando no hay red
const int INTENTOS_WIFI = 12;
const int INTENTOS_MQTT = 3;

// Cola circular en memoria RTC: sobrevive a deep sleep
const uint16_t MAX_MUESTRAS_PENDIENTES = 64;

struct MuestraPendiente {
  int humedad;
  uint32_t secuencia;
};

RTC_DATA_ATTR MuestraPendiente colaPendientes[MAX_MUESTRAS_PENDIENTES];
RTC_DATA_ATTR uint16_t indiceInicio = 0;
RTC_DATA_ATTR uint16_t indiceFin = 0;
RTC_DATA_ATTR uint16_t totalPendientes = 0;
RTC_DATA_ATTR uint32_t secuenciaGlobal = 0;

WiFiClientSecure espClient;
PubSubClient client(espClient);

void logMinimo(const char* texto) {
  if (DEPURACION) {
    Serial.println(texto);
  }
}

bool encolarMuestra(int humedad) {
  if (totalPendientes >= MAX_MUESTRAS_PENDIENTES) {
    // Si se llena la cola, descarta la mas antigua para priorizar datos recientes.
    indiceInicio = (indiceInicio + 1) % MAX_MUESTRAS_PENDIENTES;
    totalPendientes--;
  }

  colaPendientes[indiceFin].humedad = humedad;
  colaPendientes[indiceFin].secuencia = ++secuenciaGlobal;
  indiceFin = (indiceFin + 1) % MAX_MUESTRAS_PENDIENTES;
  totalPendientes++;
  return true;
}

bool obtenerPrimeraMuestra(MuestraPendiente& muestra) {
  if (totalPendientes == 0) {
    return false;
  }
  muestra = colaPendientes[indiceInicio];
  return true;
}

void confirmarEnvioPrimeraMuestra() {
  if (totalPendientes == 0) {
    return;
  }
  indiceInicio = (indiceInicio + 1) % MAX_MUESTRAS_PENDIENTES;
  totalPendientes--;
}

int leerHumedad() {
  int valorCrudo = analogRead(SENSOR_PIN);
  int humedad = map(valorCrudo, 2000, 150, 0, 100);
  if (humedad < 0) humedad = 0;
  if (humedad > 100) humedad = 100;
  return humedad;
}

bool conectarWifi() {
  if (WiFi.status() == WL_CONNECTED) {
    return true;
  }

  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);

  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < INTENTOS_WIFI) {
    delay(500);
    intentos++;
  }

  return WiFi.status() == WL_CONNECTED;
}

bool conectarMQTT() {
  if (client.connected()) {
    return true;
  }

  for (int i = 0; i < INTENTOS_MQTT; i++) {
    if (client.connect(mqtt_client_id, mqtt_user, mqtt_password)) {
      return true;
    }
    delay(400);
  }

  return false;
}

bool enviarMuestra(const MuestraPendiente& muestra) {
  char mensaje[96];
  snprintf(
    mensaje,
    sizeof(mensaje),
    "{\"valorHumedad\":%d,\"secuencia\":%lu}",
    muestra.humedad,
    (unsigned long)muestra.secuencia
  );

  bool publicado = client.publish(mqtt_topic, mensaje, true);
  client.loop();
  delay(20);
  return publicado;
}

void enviarPendientes() {
  while (totalPendientes > 0) {
    MuestraPendiente muestra;
    if (!obtenerPrimeraMuestra(muestra)) {
      return;
    }

    if (enviarMuestra(muestra)) {
      confirmarEnvioPrimeraMuestra();
    } else {
      // Si falla una publicacion, se conserva la cola para el siguiente ciclo.
      return;
    }
  }
}

void dormirHastaProximoCiclo() {
  if (client.connected()) {
    client.disconnect();
  }
  WiFi.disconnect(true);
  WiFi.mode(WIFI_OFF);

  esp_sleep_enable_timer_wakeup(INTERVALO_ENVIO_US);
  esp_deep_sleep_start();
}

void setup() {
  if (DEPURACION) {
    Serial.begin(115200);
    delay(300);
  }

  espClient.setInsecure();
  client.setServer(mqtt_server, mqtt_port);

  //  Siempre tomar lectura al despertar y almacenarla en memoria.
  int humedad = leerHumedad();
  encolarMuestra(humedad);

  // Si hay conectividad, vaciar backlog completo en orden FIFO.
  if (conectarWifi() && conectarMQTT()) {
    enviarPendientes();
    logMinimo("Envio completado o sin pendientes.");
  } else {
    logMinimo("Sin conectividad, datos guardados para el siguiente ciclo.");
  }

  // Entrar en deep sleep para maximizar autonomia.
  dormirHastaProximoCiclo();
}

void loop() {
  // No se usa: el flujo trabaja por ciclos con deep sleep desde setup().
}







