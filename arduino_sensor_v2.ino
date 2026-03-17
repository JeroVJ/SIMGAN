#include <WiFi.h>
#include <PubSubClient.h>

#define SENSOR_PIN 32

// wifi 
const char* ssid = "LOS JUANES";
const char* password = "Juanluiseduardo";

// mqtt - EMQX Cloud (TLS/SSL)
const char* mqtt_server = "q94824c1.ala.eu-central-1.emqxsl.com";
const int mqtt_port = 8883;
const char* mqtt_user = "SIMGAN";
const char* mqtt_password = "simgan12";
const char* mqtt_topic = "sensor/5/data";  
const char* mqtt_client_id = "ESP32_HUMEDAD";

WiFiClientSecure espClient;
PubSubClient client(espClient);

unsigned long lastPublish = 0;
const long publishInterval = 5000; // 5 segundos

void setup_wifi() {
  Serial.println("\n\n=== INICIANDO CONEXION WIFI ===");
  Serial.print("Conectando a: ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(500);
    Serial.print(".");
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n✓ WiFi CONECTADO");
    Serial.print("IP: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("\n✗ ERROR: No se pudo conectar a WiFi");
  }
}

void reconnect() {
  while (!client.connected()) {
    Serial.print("Intentando conexion MQTT a ");
    Serial.print(mqtt_server);
    Serial.print(":");
    Serial.println(mqtt_port);

    if (client.connect(mqtt_client_id, mqtt_user, mqtt_password)) {
      Serial.println("✓ MQTT CONECTADO");
      Serial.print("Cliente ID: ");
      Serial.println(mqtt_client_id);
    } else {
      Serial.print("✗ Fallo: rc=");
      Serial.println(client.state());
      Serial.println("Reintentando en 2 segundos...");
      delay(2000);
    }
  }
}

void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Mensaje recibido en topic: ");
  Serial.println(topic);
  for (int i = 0; i < length; i++) {
    Serial.print((char)payload[i]);
  }
  Serial.println();
}

void setup() {
  Serial.begin(115200);
  delay(2000);
  
  Serial.println("\n\n========== INICIO DEL SISTEMA ==========");
  
  setup_wifi();

  // Configurar SSL/TLS para EMQX
  espClient.setInsecure(); // Desactiva verificación de certificado (necesario para EMQX Cloud)
  
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);
  
  Serial.println("Sistema listo\n");
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("✗ WiFi desconectado, reconectando...");
    setup_wifi();
  }

  if (!client.connected()) {
    Serial.println("✗ MQTT desconectado, reconectando...");
    reconnect();
  }

  client.loop();

  // Publicar cada 5 segundos
  if (millis() - lastPublish >= publishInterval) {
    lastPublish = millis();

    int sensorValue = analogRead(SENSOR_PIN);
    int humedad = map(sensorValue, 4095, 0, 0, 100);

    Serial.print(" Leyendo sensor: ");
    Serial.print(sensorValue);
    Serial.print(" -> ");
    Serial.print(humedad);
    Serial.println("%");

    char mensaje[20];
    sprintf(mensaje, "{\"humidity\":%d}", humedad);

    Serial.print(" Publicando en topic: ");
    Serial.println(mqtt_topic);
    Serial.print("Mensaje: ");
    Serial.println(mensaje);

    if (client.publish(mqtt_topic, mensaje)) {
      Serial.println("✓ Publicado exitosamente\n");
    } else {
      Serial.println("✗ Error al publicar\n");
    }
  }
}
