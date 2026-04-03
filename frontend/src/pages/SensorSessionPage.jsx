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

// Polling interval in milliseconds
const POLLING_INTERVAL = 3000 // 3 segundos

function FitBounds({ geoJson }) {
  const map = useMap()

  useEffect(() => {
    if (!geoJson) return
    try {
      const geo = typeof geoJson === 'string' ? JSON.parse(geoJson) : geoJson
      const layer = L.geoJSON(geo)
      map.fitBounds(layer.getBounds(), {
        padding: [50, 50],
        maxZoom: 17,
      })
    } catch {}
  }, [geoJson, map])

  return null
}

function FitBoundsWithSensor({ parcelGeoJson, sensorGeoJson }) {
  const map = useMap()

  useEffect(() => {
    if (!parcelGeoJson && !sensorGeoJson) return
    
    try {
      const bounds = L.latLngBounds([])
      
      // Agregar el parcel si existe
      if (parcelGeoJson) {
        const parcelGeo = typeof parcelGeoJson === 'string' ? JSON.parse(parcelGeoJson) : parcelGeoJson
        const parcelLayer = L.geoJSON(parcelGeo)
        bounds.extend(parcelLayer.getBounds())
      }
      
      // Agregar el sensor si existe
      if (sensorGeoJson?.type === 'Point') {
        const [lon, lat] = sensorGeoJson.coordinates
        bounds.extend([lat, lon])
      }
      
      if (bounds.isValid()) {
        map.fitBounds(bounds, {
          padding: [50, 50],
          maxZoom: 17,
        })
      }
    } catch {}
  }, [parcelGeoJson, sensorGeoJson, map])

  return null
}

export default function SensorSessionPage() {
  const { sensorId } = useParams()
  const navigate = useNavigate()

  const [sensor, setSensor] = useState(null)
  const [parcel, setParcel] = useState(null)
  const [loading, setLoading] = useState(true)
  const [mqttConfig, setMqttConfig] = useState(null)
  const [sensorData, setSensorData] = useState([])
  const [isMonitoring, setIsMonitoring] = useState(false)
  const [isSavingConfig, setIsSavingConfig] = useState(false)
  const [showConfigForm, setShowConfigForm] = useState(false)
  const pollingIntervalRef = useRef(null)

  // Formulario de configuración MQTT
  const [formConfig, setFormConfig] = useState({
    brokerUrl: '',
    username: '',
    password: '',
    topic: '',
    clientId: ''
  })

  // Cargar sensor y parcel
  useEffect(() => {
    if (!sensorId) return

    const loadSensorData = async () => {
      try {
        setLoading(true)
        
        // Cargar sensor básico
        const sensorRes = await sensorApi.getById(sensorId)
        setSensor(sensorRes)
        console.log('✓ Sensor cargado:', sensorRes)

        // Cargar parcel si existe
        if (sensorRes.parcelId) {
          const parcelRes = await api.get(`/parcels/${sensorRes.parcelId}`)
          const parcelData = parcelRes.data
          setParcel(parcelData)

          if (parcelData?.farmId) {
            const farmRes = await api.get(`/farms/${parcelData.farmId}`)
            if (farmRes?.data?.iotEnabled === false) {
              toast.error('IoT deshabilitado para esta finca')
              navigate(`/terrains/${parcelData.terrainId}/parcels`, { replace: true })
              return
            }
          }
        }

        // Intentar cargar configuración MQTT guardada
        try {
          const configRes = await api.get(`/sensors/${sensorId}/mqtt-config`)
          setMqttConfig(configRes.data)
          setShowConfigForm(false) // Ocultar formulario si ya existe config
          console.log('✓ Configuración MQTT cargada:', configRes.data)
        } catch (configErr) {
          // No hay configuración guardada aún
          console.log('Sin configuración MQTT guardada')
          setShowConfigForm(true) // Mostrar formulario
        }
      } catch (err) {
        console.error('Error cargando sensor:', err)
        toast.error('Error cargando sensor')
      } finally {
        setLoading(false)
      }
    }

    loadSensorData()
  }, [sensorId])

  // Cargar datos del sensor desde el backend
  const fetchSensorData = async () => {
    try {
      const classificationsRes = await api.get(`/sensors/${sensorId}/classifications`)
      if (classificationsRes.data && classificationsRes.data.length > 0) {
        console.log('Datos cargados:', classificationsRes.data.length)
        setSensorData(classificationsRes.data)
      }
    } catch (err) {
      console.error('Error cargando datos del sensor:', err)
    }
  }

  // Polling: cargar datos cada POLLING_INTERVAL
  useEffect(() => {
    if (!isMonitoring || !sensorId) return

    // Cargar inmediatamente
    fetchSensorData()

    // Configurar polling
    pollingIntervalRef.current = setInterval(() => {
      fetchSensorData()
    }, POLLING_INTERVAL)

    return () => {
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current)
      }
    }
  }, [isMonitoring, sensorId])

  // Guardar configuración MQTT desde el formulario
  const handleSaveConfig = async () => {
    try {
      if (!formConfig.brokerUrl || !formConfig.topic) {
        toast.error('ℹ URL del broker y topic son requeridos')
        return
      }

      setIsSavingConfig(true)
      const saveRes = await api.post(`/sensors/${sensorId}/mqtt-config`, {
        brokerUrl: formConfig.brokerUrl,
        username: formConfig.username,
        password: formConfig.password,
        topic: formConfig.topic,
        clientId: formConfig.clientId || `sensor-${sensorId}`
      })

      setMqttConfig(saveRes.data)
      setShowConfigForm(false)
      setFormConfig({ brokerUrl: '', username: '', password: '', topic: '', clientId: '' })
      toast.success('✓ Configuración MQTT guardada')
      console.log('✓ Config guardada:', saveRes.data)
    } catch (err) {
      console.error('Error guardando configuración:', err)
      toast.error('Error al guardar configuración')
    } finally {
      setIsSavingConfig(false)
    }
  }

  // Iniciar monitoreo
  const handleStartMonitoring = async () => {
    try {
      console.log('▶Iniciando monitoreo del sensor...')
      setIsMonitoring(true)
      toast.success('🟢 Monitoreo iniciado')
    } catch (err) {
      console.error('Error iniciando monitoreo:', err)
      toast.error('Error al iniciar monitoreo')
    }
  }

  // Detener monitoreo
  const handleStopMonitoring = async () => {
    try {
      console.log('⏹ Deteniendo monitoreo...')
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current)
      }
      setIsMonitoring(false)
      toast.success(' Monitoreo detenido')
    } catch (err) {
      console.error('Error deteniendo monitoreo:', err)
      toast.error('Error al detener monitoreo')
    }
  }

  // Limpiar datos locales del frontend
  const handleClearData = () => {
    setSensorData([])
    toast.success('Datos locales limpiados')
  }

  // Cleanup: detener polling al salir
  useEffect(() => {
    return () => {
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current)
      }
    }
  }, [])

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
          {isMonitoring && ' 🟢 En línea'}
        </p>
      </div>

      <div className="two-col">
        <div className="col-main">
          <div className="map-container">
            <MapContainer center={[4.6, -74.1]} zoom={15} style={{ height: '100%', width: '100%' }}>
              <FitBoundsWithSensor parcelGeoJson={parcelGeoJson} sensorGeoJson={sensorGeoJson} />

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
                      Estado: {isMonitoring ? '🟢 Monitoreando' : '🔴 Inactivo'}
                    </div>
                  </Popup>
                </Marker>
              )}
            </MapContainer>
          </div>
        </div>

        <div className="col-side">
          {/* Formulario de configuración MQTT o Panel de monitoreo */}
          <div className="card">
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 20, marginBottom: 16 }}>
              {showConfigForm ? ' Configurar MQTT' : (mqttConfig ? '✓ Configuración' : '⚠️ Sin configurar')}
            </h3>

            {/* Mostrar formulario si no hay configuración */}
            {showConfigForm && (
              <>
                <div className="form-group mb-16">
                  <label>URL del Broker MQTT</label>
                  <input
                    type="text"
                    value={formConfig.brokerUrl}
                    onChange={(e) => setFormConfig({ ...formConfig, brokerUrl: e.target.value })}
                    placeholder="ssl://q94824c1.ala.eu-central-1.emqxsl.com:8883"
                  />
                </div>

                <div className="form-group mb-16">
                  <label>Usuario</label>
                  <input
                    type="text"
                    value={formConfig.username}
                    onChange={(e) => setFormConfig({ ...formConfig, username: e.target.value })}
                    placeholder="SIMGAN"
                  />
                </div>

                <div className="form-group mb-16">
                  <label>Contraseña</label>
                  <input
                    type="password"
                    value={formConfig.password}
                    onChange={(e) => setFormConfig({ ...formConfig, password: e.target.value })}
                    placeholder="••••••••••"
                  />
                </div>

                <div className="form-group mb-16">
                  <label>Topic MQTT</label>
                  <input
                    type="text"
                    value={formConfig.topic}
                    onChange={(e) => setFormConfig({ ...formConfig, topic: e.target.value })}
                    placeholder={`sensor/${sensorId}/data`}
                  />
                </div>

                <div className="form-group mb-16">
                  <label>Client ID (opcional)</label>
                  <input
                    type="text"
                    value={formConfig.clientId}
                    onChange={(e) => setFormConfig({ ...formConfig, clientId: e.target.value })}
                    placeholder={`sensor-${sensorId}`}
                  />
                </div>

                <button
                  className="action-btn action-btn--primary"
                  style={{ width: '100%' }}
                  onClick={handleSaveConfig}
                  disabled={isSavingConfig}
                >
                  {isSavingConfig ? (
                    <><span className="spinner" /> Guardando...</>
                  ) : (
                    ' Guardar Configuración'
                  )}
                </button>
              </>
            )}

            {/* Mostrar configuración guardada y panel de monitoreo */}
            {mqttConfig && !showConfigForm && (
              <>
                <div style={{ padding: '12px 16px', background: 'var(--color-bg)', borderRadius: 'var(--radius-sm)', marginBottom: 16, fontSize: 11, color: 'var(--color-text-secondary)' }}>
                  <strong style={{ color: 'var(--color-text)', display: 'block', marginBottom: 8 }}> Configuración MQTT:</strong>
                  <div style={{ fontFamily: 'monospace', fontSize: 10, lineHeight: 1.6 }}>
                     Broker: {mqttConfig.brokerUrl}<br />
                     Usuario: {mqttConfig.username}<br />
                     Topic: {mqttConfig.topic}<br />
                     Client ID: {mqttConfig.clientId}<br />
                    <span style={{ marginTop: 8, display: 'block', color: '#10b981' }}>
                      ✓ {mqttConfig.state}
                    </span>
                  </div>
                </div>

                <button
                  className="action-btn"
                  style={{ width: '100%', marginBottom: 8, background: 'var(--color-bg)', fontSize: 12 }}
                  onClick={() => setShowConfigForm(true)}
                >
                   Cambiar Configuración
                </button>

                {!isMonitoring ? (
                  <>
                    

                    <button
                      className="action-btn action-btn--primary"
                      style={{ width: '100%' }}
                      onClick={handleStartMonitoring}
                    >
                       Iniciar Monitoreo
                    </button>
                  </>
                ) : (
                  <>
                    <div style={{ padding: '12px 16px', background: '#d1fae5', borderRadius: 'var(--radius-sm)', marginBottom: 16, fontSize: 12, color: '#065f46', borderLeft: '4px solid #10b981' }}>
                      <div style={{ fontWeight: 600, marginBottom: 8 }}>✓ Monitoreo Activo</div>
                      <div style={{ fontSize: 11 }}>
                       Sensor: {sensor?.name}<br />
                       Polling: {POLLING_INTERVAL / 1000}s<br />
                       Datos recibidos: {sensorData.length}
                      </div>
                    </div>

                    <button
                      className="action-btn"
                      style={{ width: '100%', background: '#ef4444', color: 'white' }}
                      onClick={handleStopMonitoring}
                    >
                       Detener Monitoreo
                    </button>

                    <button
                      className="action-btn"
                      style={{ width: '100%', marginTop: 8, background: 'var(--color-bg)' }}
                      onClick={handleClearData}
                    >
                       Limpiar Datos Locales ({sensorData.length})
                    </button>
                  </>
                )}
              </>
            )}
          </div>

          {/* Datos del sensor almacenados en backend */}
          {isMonitoring && sensorData.length > 0 && (
            <div className="card">
              <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 12 }}>
                 Datos Almacenados ({sensorData.length})
              </h3>

              <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
                {sensorData.map((data, idx) => (
                  <div
                    key={data.id || idx}
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
                        {data.timestamp ? new Date(data.timestamp).toLocaleTimeString() : 'N/A'}
                      </span>
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--color-text-secondary)' }}>
                      <strong>Humedad:</strong> {data.valorHumedad}%<br />
                      <strong>Estado:</strong> {data.estado}<br />
                      <strong>Consecuencia:</strong> {data.consecuencia}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {isMonitoring && sensorData.length === 0 && (
            <div className="card">
              <div style={{ padding: '24px', textAlign: 'center', color: 'var(--color-text-secondary)' }}>
                <div style={{ fontSize: 32, marginBottom: 8 }}>⏳</div>
                <div style={{ fontWeight: 600, marginBottom: 8 }}>Esperando datos...</div>
                <div style={{ fontSize: 12 }}>
                  El backend se está conectando a EMQX<br />
                  y procesando la información del sensor
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
