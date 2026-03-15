# 📡 Instalación y Configuración del Sistema de Sensores MQTT

## Requisitos Previos

### Frontend - Instalación de MQTT.js

Para que la sesión de sensores pueda conectarse a Mosquitto, necesitas instalar el cliente MQTT:

```bash
cd c:\Users\marce\Desktop\Simgan\SIMGAN\frontend
npm install mqtt
```

**Nota:** Si npm install falla, intenta con versión específica:
```bash
npm install mqtt@4.3.7
```

### Backend - Migraciones de Base de Datos

Se han agregado nuevos campos a la tabla de sensores. **Ejecuta esta migración SQL:**

```sql
-- Agregar campos MQTT a la tabla sensores
ALTER TABLE sensores ADD COLUMN mqtt_topic VARCHAR(255);
ALTER TABLE sensores ADD COLUMN mqtt_broker_url VARCHAR(255);
ALTER TABLE sensores ADD COLUMN client_id VARCHAR(255);
ALTER TABLE sensores ADD COLUMN connected BIT(1) DEFAULT b'0';
```

O si usas Spring Data JPA con Hibernate, la migración se ejecutará automáticamente.

## Estructura del Flujo

### 1. Crear Sensores (SensorPage)
- URL: `/parcels/:parcelId/sensors`
- Acciones:
  - ✅ Ver mapa del potrero
  - ✅ Hacer click para marcar ubicación del sensor
  - ✅ Ingresar nombre del sensor
  - ✅ Guardar sensor
  - ✅ Ver lista de sensores

### 2. Sesión de Monitoreo (SensorSessionPage)
- URL: `/sensors/:sensorId/session`
- Acciones:
  - ✅ Configurar conexión MQTT
  - ✅ Conectar a Mosquitto broker
  - ✅ Recibir datos en tiempo real
  - ✅ Visualizar datos en historial
  - ✅ Desconectar

## Configuración de Mosquitto

### Ubicación del archivo de configuración:
```
c:\Users\marce\Desktop\Simgan\SIMGAN\mosquitto\mosquitto.conf
```

### Configuración recomendada para WebSocket:

```conf
# Puerto MQTT estándar
listener 1883

# Puerto WebSocket (necesario para conectarse desde el navegador)
listener 8083
protocol websockets

# Permitir conexiones anónimas (desarrollo)
allow_anonymous true

# Archivo de persistencia
persistence true
persistence_location C:\Users\marce\Desktop\Simgan\SIMGAN\mosquitto\data\

# Log
log_dest file C:\Users\marce\Desktop\Simgan\SIMGAN\mosquitto\mosquitto.log
```

## Iniciar Mosquitto

### Windows:
```bash
cd c:\Program Files\mosquitto
mosquitto -c c:\Users\marce\Desktop\Simgan\SIMGAN\mosquitto\mosquitto.conf
```

### Docker (recomendado):
```bash
docker run -it -p 1883:1883 -p 8083:8083 -v mosquitto.conf:/mosquitto/config/mosquitto.conf eclipse-mosquitto
```

## Usar el Sistema

### 1. Crear Sensor
```
1. Ir a Fincas → Finca → Terreno → Potreros
2. Hacer click en "📡 Sensores" en un potrero
3. Hacer click en el mapa para marcar ubicación
4. Ingresar nombre (ej: "Sensor 1")
5. Click en "✓ Guardar Sensor"
```

### 2. Monitorear Sensor
```
1. En la lista de sensores, hacer click en "Sesión"
2. Configurar MQTT:
   - URL del Broker: ws://localhost:8083/mqtt
   - Topic: sensor/123/data (auto-generado)
3. Click en "🔌 Conectar a Mosquitto"
4. Esperar datos...
```

### 3. Enviar Datos de Prueba

Usando MQTT CLI:
```bash
# Instalar mqtt-cli
npm install -g mqtt-cli

# Enviar mensaje de prueba
mqtt pub -h 127.0.0.1 -p 1883 -t sensor/123/data -m '{"temperature": 25.5, "humidity": 60}'
```

O con mosquitto_pub:
```bash
mosquitto_pub -h localhost -p 1883 -t sensor/123/data -m '{"temperature": 25.5, "humidity": 60}'
```

## Estructura de Datos MQTT

Los mensajes deben ser JSON válidos:

```json
{
  "temperature": 25.5,
  "humidity": 60,
  "location": "A",
  "timestamp": "2026-03-15T10:30:00Z",
  "batteryLevel": 85
}
```

## APIs Backend Nuevas

### Crear Sensor
```http
POST /api/sensors
Content-Type: application/json

{
  "name": "Sensor 1",
  "ubicacionGeoJson": "{\"type\":\"Point\",\"coordinates\":[-74.1,4.6]}",
  "parcel": {
    "id": 123
  }
}
```

### Obtener Sensores de un Potrero
```http
GET /api/sensors/parcel/{parcelId}
```

### Actualizar Configuración MQTT
```http
PATCH /api/sensors/{sensorId}/config
Content-Type: application/json

{
  "mqttTopic": "sensor/123/data",
  "mqttBrokerUrl": "ws://localhost:8083/mqtt",
  "clientId": "sensor-client-xyz",
  "connected": true
}
```

### Conectar/Desconectar
```http
POST /api/sensors/{sensorId}/connect
POST /api/sensors/{sensorId}/disconnect
GET /api/sensors/{sensorId}/status
```

## Troubleshooting

### Error: "MQTT.js not found"
→ Ejecuta: `npm install mqtt`

### No se conecta a Mosquitto
→ Verifica que Mosquitto está corriendo en puerto 1883/8083
→ Prueba: `docker run -p 1883:1883 -p 8083:8083 eclipse-mosquitto`

### Los sensores no aparecen en la lista
→ Verifica el parcelId en la URL
→ Revisa la consola del navegador (F12)
→ Asegúrate que el backend está retornando datos en `/api/sensors/parcel/{parcelId}`

### No recibe datos del sensor
→ Verifica que el topic es correcto
→ Envía un mensaje de prueba: `mosquitto_pub -h localhost -t sensor/123/data -m '{"test":1}'`
→ Revisa que el cliente MQTT está conectado (debe mostrar "✓ Conectado")

## Archivos Modificados

### Frontend
- ✅ `SensorPage.jsx` - Crear sensores, mostrar lista
- ✅ `SensorSessionPage.jsx` - Sesión MQTT, monitoreo en tiempo real
- ✅ `useSensor.js` - Hook para gestionar sensores
- ✅ `App.jsx` - Nuevas rutas
- ✅ `api.js` - Nuevos endpoints

### Backend
- ✅ `Sensor.java` - Nuevos campos MQTT
- ✅ `SensorController.java` - Endpoints CRUD y MQTT
- ✅ `SensorRepository.java` - Método findByParcelId
- ✅ `SensorConfigDto.java` - DTO para configuración
- ✅ `ParcelController.java` - Endpoint GET /parcels/{id}
- ✅ `ParcelDto.java` - Agregar información de finca
- ✅ `ParcelService.java` - Agregar método findById

## Próximas Mejoras

- [ ] Persistencia de datos de sensores en base de datos
- [ ] Gráficos de datos en tiempo real (Chart.js)
- [ ] Alertas cuando sensor se desconecta
- [ ] Almacenamiento histórico de lecturas
- [ ] Estadísticas y reportes de sensores
- [ ] Integración con NDVI para correlacionar datos
