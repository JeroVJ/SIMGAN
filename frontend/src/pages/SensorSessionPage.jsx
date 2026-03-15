import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { MapContainer, TileLayer, GeoJSON, useMap, Marker, Popup } from 'react-leaflet'
import L from 'leaflet'
import toast from 'react-hot-toast'

import Spinner from '../components/Spinner'
import api, { sensorApi } from '../services/api'

delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
})

function FitBounds({ geoJson }) {
  const map = useMap()

  useEffect(() => {
    if (!geoJson) return
    try {
      const geo = typeof geoJson === 'string' ? JSON.parse(geoJson) : geoJson
      const layer = L.geoJSON(geo)
      map.fitBounds(layer.getBounds(), { padding: [30, 30] })
    } catch {}
  }, [geoJson, map])

  return null
}

export default function SensorSessionPage() {
  const { sensorId } = useParams()
  const navigate = useNavigate()

  const [sensor, setSensor] = useState(null)
  const [parcel, setParcel] = useState(null)
  const [loading, setLoading] = useState(true)
  const [connecting, setConnecting] = useState(false)
  const [connected, setConnected] = useState(false)
  const [sensorData, setSensorData] = useState([])

  // Configuración MQTT
  const [mqttConfig, setMqttConfig] = useState({
    brokerUrl: localStorage.getItem('mqttBrokerUrl') || 'ws://localhost:8083/mqtt',
    topic: '',
    clientId: `sensor-client-${Math.random().toString(36).substr(2, 9)}`
  })
  const [showConfig, setShowConfig] = useState(false)
  const clientRef = useRef(null)

  // Cargar sensor
  useEffect(() => {
    if (!sensorId) return

    const loadSensor = async () => {
      try {
        setLoading(true)
        const sensorRes = await sensorApi.getById(sensorId)
        setSensor(sensorRes)

        // Cargar parcel si existe
        if (sensorRes.parcel?.id) {
          const parcelRes = await api.get(`/parcels/${sensorRes.parcel.id}`)
          setParcel(parcelRes.data)
        }

        // Cargar configuración MQTT guardada del sensor
        if (sensorRes.mqttTopic) {
          setMqttConfig(prev => ({
            ...prev,
            topic: sensorRes.mqttTopic,
            brokerUrl: sensorRes.mqttBrokerUrl || prev.brokerUrl,
            clientId: sensorRes.clientId || prev.clientId
          }))
        } else {
          // Generar topic por defecto
          setMqttConfig(prev => ({
            ...prev,
            topic: `sensor/${sensorRes.id}/data`
          }))
        }

        // Si el sensor estaba conectado, mantener estado
        if (sensorRes.connected) {
          setConnected(true)
        }
      } catch (err) {
        console.error('Error cargando sensor:', err)
        toast.error('Error cargando sensor')
      } finally {
        setLoading(false)
      }
    }

    loadSensor()
  }, [sensorId])

  // Conectar a MQTT
  const handleConnectMQTT = async () => {
    setConnecting(true)
    try {
      // Guardar configuración en el backend
      await sensorApi.updateSensorConfig(sensorId, {
        mqttTopic: mqttConfig.topic,
        mqttBrokerUrl: mqttConfig.brokerUrl,
        clientId: mqttConfig.clientId,
        connected: true
      })

      // Importar MQTT dinámicamente
      const mqtt = (await import('mqtt')).default

      // Crear cliente
      const client = mqtt.connect(mqttConfig.brokerUrl, {
        clientId: mqttConfig.clientId,
        clean: true,
        reconnectPeriod: 1000,
      })

      client.on('connect', () => {
        toast.success('🟢 Conectado a Mosquitto')
        setConnected(true)
        
        // Suscribirse al topic
        client.subscribe(mqttConfig.topic, (err) => {
          if (err) {
            toast.error('Error al suscribirse')
          } else {
            toast.success(`📡 Escuchando: ${mqttConfig.topic}`)
          }
        })
      })

      client.on('message', (topic, message) => {
        try {
          const data = JSON.parse(message.toString())
          setSensorData(prev => [
            {
              ...data,
              timestamp: new Date().toISOString(),
              id: Math.random()
            },
            ...prev.slice(0, 99) // Mantener últimos 100 datos
          ])
        } catch (e) {
          // Si no es JSON válido, mostrar como texto
          setSensorData(prev => [
            {
              value: message.toString(),
              timestamp: new Date().toISOString(),
              id: Math.random()
            },
            ...prev.slice(0, 99)
          ])
        }
      })

      client.on('error', (err) => {
        console.error('MQTT error:', err)
        toast.error('❌ Error de conexión MQTT')
        setConnected(false)
      })

      client.on('disconnect', () => {
        setConnected(false)
        toast.info('Desconectado de Mosquitto')
      })

      clientRef.current = client

      // Guardar configuración en localStorage
      localStorage.setItem('mqttBrokerUrl', mqttConfig.brokerUrl)
    } catch (err) {
      console.error('Error connecting to MQTT:', err)
      toast.error('⚠️ Error: Asegúrate de que MQTT.js está instalado en el frontend')
    } finally {
      setConnecting(false)
    }
  }

  const handleDisconnect = async () => {
    if (clientRef.current) {
      clientRef.current.end()
      clientRef.current = null
      
      // Actualizar estado en backend
      try {
        await sensorApi.updateSensorConfig(sensorId, {
          connected: false
        })
      } catch (err) {
        console.error('Error actualizando estado:', err)
      }
      
      setConnected(false)
      setSensorData([])
      toast.info('🔌 Desconectado')
    }
  }

  const handleClearData = () => {
    setSensorData([])
  }

  if (loading) {
    return <Spinner page label="Cargando sensor..." />
  }

  const sensorGeoJson = (() => {
    try {
      return JSON.parse(sensor?.ubicacionGeoJson)
    } catch {
      return null
    }
  })()

  const parcelGeoJson = (() => {
    try {
      return JSON.parse(parcel?.geoJson)
    } catch {
      return null
    }
  })()

  return (
    <div className="page-container">
      <div className="page-header">
        <div className="breadcrumb">
          <Link to="/farms">Fincas</Link>
          <span>›</span>
          <Link to={`/farms/${parcel?.farmId || '#'}`}>
            {parcel?.farmName || 'Finca'}
          </Link>
          <span>›</span>
          <Link to={`/terrains/${parcel?.terrainId || '#'}/parcels`}>
            Potreros
          </Link>
          <span>›</span>
          <Link to={`/parcels/${parcel?.id || '#'}/sensors`}>
            Sensores
          </Link>
          <span>›</span>
          <span>Sesión - {sensor?.name}</span>
        </div>

        <h2>Monitoreo de Sensor</h2>
        <p>
          {sensor?.name} en {parcel?.name}
          {connected && ' 🟢 En línea'}
        </p>
      </div>

      <div className="two-col">
        <div className="col-main">
          <div className="map-container">
            <MapContainer center={[4.6, -74.1]} zoom={15} style={{ height: '100%', width: '100%' }}>
              {parcelGeoJson && <FitBounds geoJson={parcelGeoJson} />}

              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                attribution="Tiles © Esri"
                maxZoom={19}
              />

              {parcelGeoJson && (
                <GeoJSON
                  data={parcelGeoJson}
                  style={{
                    color: '#3b82f6',
                    weight: 3,
                    fillOpacity: 0.1,
                  }}
                />
              )}

              {sensorGeoJson?.type === 'Point' && (
                <Marker position={[sensorGeoJson.coordinates[1], sensorGeoJson.coordinates[0]]}>
                  <Popup permanent={true}>
                    <div>
                      <strong>{sensor?.name}</strong><br />
                      Estado: {connected ? '🟢 Conectado' : '🔴 Desconectado'}
                    </div>
                  </Popup>
                </Marker>
              )}
            </MapContainer>
          </div>
        </div>

        <div className="col-side">
          {/* Configuración MQTT */}
          <div className="card">
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 20, marginBottom: 16 }}>
              {connected ? '✓ Configuración' : '⚙️ Configuración MQTT'}
            </h3>

            {!connected ? (
              <>
                <div className="form-group mb-16">
                  <label>URL del Broker</label>
                  <input
                    value={mqttConfig.brokerUrl}
                    onChange={(e) => setMqttConfig({ ...mqttConfig, brokerUrl: e.target.value })}
                    placeholder="ws://localhost:8083/mqtt"
                  />
                </div>

                <div className="form-group mb-16">
                  <label>Topic MQTT</label>
                  <input
                    value={mqttConfig.topic}
                    onChange={(e) => setMqttConfig({ ...mqttConfig, topic: e.target.value })}
                    placeholder="sensor/123/data"
                  />
                </div>

                <div className="form-group mb-16">
                  <label>Client ID</label>
                  <input
                    value={mqttConfig.clientId}
                    onChange={(e) => setMqttConfig({ ...mqttConfig, clientId: e.target.value })}
                    placeholder="sensor-client-xxx"
                  />
                </div>

                <div style={{ padding: '12px 16px', background: 'var(--color-bg)', borderRadius: 'var(--radius-sm)', marginBottom: 16, fontSize: 12, color: 'var(--color-text-secondary)' }}>
                  <strong style={{ color: 'var(--color-text)' }}>Notas:</strong><br />
                  • Broker: localhost:8083 por defecto<br />
                  • Topic: se genera automáticamente
                </div>

                <button
                  className="action-btn action-btn--primary"
                  style={{ width: '100%' }}
                  onClick={handleConnectMQTT}
                  disabled={connecting || !mqttConfig.topic}
                >
                  {connecting ? (
                    <><span className="spinner" /> Conectando...</>
                  ) : (
                    '🔌 Conectar a Mosquitto'
                  )}
                </button>
              </>
            ) : (
              <>
                <div style={{ padding: '12px 16px', background: '#d1fae5', borderRadius: 'var(--radius-sm)', marginBottom: 16, fontSize: 12, color: '#065f46', borderLeft: '4px solid #10b981' }}>
                  <div style={{ fontWeight: 600, marginBottom: 8 }}>✓ Conectado a Mosquitto</div>
                  <div style={{ fontSize: 11 }}>
                    📡 Topic: {mqttConfig.topic}<br />
                    🌐 Broker: {mqttConfig.brokerUrl}
                  </div>
                </div>

                <button
                  className="action-btn"
                  style={{ width: '100%', background: '#ef4444', color: 'white' }}
                  onClick={handleDisconnect}
                >
                  🔌 Desconectar
                </button>

                <button
                  className="action-btn"
                  style={{ width: '100%', marginTop: 8, background: 'var(--color-bg)' }}
                  onClick={handleClearData}
                >
                  Limpiar Datos ({sensorData.length})
                </button>
              </>
            )}
          </div>

          {/* Datos en tiempo real */}
          {sensorData.length > 0 && (
            <div className="card">
              <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 12 }}>
                📊 Datos en Tiempo Real ({sensorData.length})
              </h3>

              <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
                {sensorData.map((data) => (
                  <div
                    key={data.id}
                    style={{
                      padding: '12px',
                      marginBottom: '8px',
                      background: 'var(--color-bg)',
                      borderRadius: '6px',
                      fontSize: 12,
                      borderLeft: '4px solid #10b981'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                      <span style={{ fontWeight: 600, color: '#10b981' }}>
                        {new Date(data.timestamp).toLocaleTimeString()}
                      </span>
                    </div>
                    <pre style={{ margin: 0, fontSize: 11, overflow: 'auto', maxWidth: '100%' }}>
                      {JSON.stringify(data, null, 2)}
                    </pre>
                  </div>
                ))}
              </div>
            </div>
          )}

          {connected && sensorData.length === 0 && (
            <div className="card">
              <div style={{ padding: '24px', textAlign: 'center', color: 'var(--color-text-secondary)' }}>
                <div style={{ fontSize: 32, marginBottom: 8 }}>📡</div>
                <div style={{ fontWeight: 600, marginBottom: 8 }}>Esperando datos...</div>
                <div style={{ fontSize: 12 }}>
                  Envía mensajes JSON al topic<br />
                  <code style={{ background: 'var(--color-bg)', padding: '4px 8px', borderRadius: '4px' }}>
                    {mqttConfig.topic}
                  </code>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
