import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { MapContainer, TileLayer, FeatureGroup, GeoJSON, useMap, useMapEvents, Marker, Popup } from 'react-leaflet'
import { EditControl } from 'react-leaflet-draw'
import L from 'leaflet'
import toast from 'react-hot-toast'

import Spinner from '../components/Spinner'
import ConfirmDialog from '../components/ConfirmDialog'

import { useSensor } from '../hooks'
import api from '../services/api'

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
      map.fitBounds(layer.getBounds(), {
        padding: [50, 50],
        maxZoom: 17,
      })
    } catch {}
  }, [geoJson, map])

  return null
}

function MapClickHandler({ onMapClick }) {
  useMapEvents({
    click: (e) => {
      onMapClick(e)
    }
  })
  return null
}

export default function SensorPage() {
  const { parcelId } = useParams()
  const navigate = useNavigate()

  const [parcel, setParcel] = useState(null)
  const [loadingParcel, setLoadingParcel] = useState(true)
  const [iotEnabled, setIotEnabled] = useState(null)
  const {
    sensors,
    loading,
    addSensor,
    deleteSensor
  } = useSensor(parcelId, { enabled: iotEnabled === true })

  const [saving, setSaving] = useState(false)
  const [sensorName, setSensorName] = useState('')
  const [selectedMarker, setSelectedMarker] = useState(null)
  const [markerLocation, setMarkerLocation] = useState(null)
  const [confirm, setConfirm] = useState(null)

  const featureGroupRef = useRef(null)
  const mapRef = useRef(null)

  // Cargar parcel
  useEffect(() => {
    if (!parcelId) return
    
    const loadParcel = async () => {
      try {
        setLoadingParcel(true)
        const res = await api.get(`/parcels/${parcelId}`)
        const parcelData = res.data
        setParcel(parcelData)

        if (parcelData?.farmId) {
          const farmRes = await api.get(`/farms/${parcelData.farmId}`)
          const enabled = farmRes?.data?.iotEnabled !== false
          setIotEnabled(enabled)

          if (!enabled) {
            toast.error('IoT deshabilitado para esta finca')
            navigate(`/terrains/${parcelData.terrainId}/parcels`, { replace: true })
          }
        } else {
          setIotEnabled(true)
        }
      } catch (err) {
        console.error('Error cargando potrero:', err)
        toast.error('Error cargando potrero')
        setIotEnabled(false)
      } finally {
        setLoadingParcel(false)
      }
    }
    
    loadParcel()
  }, [parcelId])

  function handleMapClick(e) {
    const { lat, lng } = e.latlng
    setSelectedMarker({ lat, lng })
    setMarkerLocation({
      type: 'Point',
      coordinates: [lng, lat]
    })
    toast.success('Ubicación marcada en el mapa')
  }

  function handleClearMarker() {
    setSelectedMarker(null)
    setMarkerLocation(null)
  }

  async function handleSaveSensor() {
    if (!sensorName.trim()) {
      toast.error('Ingresa un nombre para el sensor')
      return
    }

    if (!markerLocation) {
      toast.error('Marca la ubicación del sensor en el mapa')
      return
    }

    setSaving(true)

    try {
      await addSensor({
        name: sensorName.trim(),
        ubicacionGeoJson: JSON.stringify(markerLocation)
      })

      setSensorName('')
      setSelectedMarker(null)
      setMarkerLocation(null)

      toast.success('Sensor guardado')
    } catch (err) {
      console.error('Error guardando sensor:', err)
      toast.error(err?.message || 'Error guardando sensor')
    } finally {
      setSaving(false)
    }
  }

 

  function handleDeleteSensor(id) {
    setConfirm({
      id,
      message: '¿Eliminar este sensor? Esta acción no se puede deshacer.'
    })
  }

  if (loading || loadingParcel || iotEnabled === null) {
    return <Spinner page label="Cargando sensores..." />
  }

  if (iotEnabled === false) {
    return <Spinner page label="IoT deshabilitado para esta finca..." />
  }

  const parcelGeoJson = (() => {
    try {
      return JSON.parse(parcel?.geoJson)
    } catch {
      return null
    }
  })()

  return (
    <div className="page-container">

      <ConfirmDialog
        open={!!confirm}
        title="Eliminar sensor"
        message={confirm?.message}
        confirmLabel="Eliminar"
        variant="danger"
        onConfirm={async () => {
          try {
            await deleteSensor(confirm.id)
            toast.success('Sensor eliminado')
          } catch (err) {
            console.error('Error eliminando sensor:', err)
            toast.error('Error eliminando sensor')
          }
          setConfirm(null)
        }}
        onCancel={() => setConfirm(null)}
      />

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

          <span>{parcel?.name || 'Potrero'}</span>

          <span>›</span>

          <span>Sensores</span>

        </div>

        <h2>Gestión de Sensores</h2>

        <p>
          {parcel?.name} — {parcel?.areaHectares?.toFixed(2)} ha
        </p>

      </div>

      <div className="two-col">

        <div className="col-main">

          <div className="map-container">

            <MapContainer
              center={[4.6, -74.1]}
              zoom={15}
              style={{ height: '100%', width: '100%' }}
              ref={mapRef}
            >

              {parcelGeoJson && <FitBounds geoJson={parcelGeoJson} />}

              <MapClickHandler onMapClick={handleMapClick} />

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

              {/* Sensores existentes */}
              {sensors.map(sensor => {
                let sensorGeo = null
                try {
                  sensorGeo = JSON.parse(sensor.ubicacionGeoJson)
                } catch {
                  return null
                }

                if (sensorGeo?.type === 'Point') {
                  const [lng, lat] = sensorGeo.coordinates
                  return (
                    <Marker key={`sensor-${sensor.id}`} position={[lat, lng]}>
                      <Popup permanent={true}>
                        <div style={{ minWidth: 150 }}>
                          <div style={{ fontWeight: 700, marginBottom: 8 }}>
                            {sensor.name || `Sensor ${sensor.id}`}
                          </div>
                          <button
                            onClick={() => handleDeleteSensor(sensor.id)}
                            style={{
                              background: '#ef4444',
                              color: 'white',
                              border: 'none',
                              padding: '4px 8px',
                              borderRadius: '4px',
                              cursor: 'pointer',
                              fontSize: 12
                            }}
                          >
                            Eliminar
                          </button>
                        </div>
                      </Popup>
                    </Marker>
                  )
                }

                return null
              })}

              {/* Marker temporal siendo colocado */}
              {selectedMarker && (
                <Marker position={[selectedMarker.lat, selectedMarker.lng]}>
                  <Popup permanent={true}>
                    <div style={{ minWidth: 150 }}>
                      <strong>Ubicación temporal</strong><br />
                      Haz clic afuera para cancelar
                    </div>
                  </Popup>
                </Marker>
              )}

            </MapContainer>

          </div>

        </div>

        {/* Side Panel */}
        <div className="col-side">
          <div className="card">
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 20, marginBottom: 16 }}>
              Agregar Sensor
            </h3>

            <div className="form-group mb-16">
              <label>Nombre del Sensor</label>
              <input
                value={sensorName}
                onChange={(e) => setSensorName(e.target.value)}
                placeholder="Ej: Sensor 1"
              />
            </div>

            {markerLocation && (
              <>
                <div style={{ padding: '12px 16px', background: 'var(--color-bg)', borderRadius: 'var(--radius-sm)', marginBottom: 16 }}>
                  <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
                    <strong>Ubicación marcada:</strong>
                  </div>
                  <div style={{ fontSize: 13, fontFamily: 'monospace', marginBottom: 8 }}>
                    {markerLocation.coordinates[1].toFixed(6)}, {markerLocation.coordinates[0].toFixed(6)}
                  </div>
                  <button
                    onClick={handleClearMarker}
                    style={{
                      background: 'var(--color-bg-secondary)',
                      border: '1px solid var(--color-border)',
                      padding: '6px 12px',
                      borderRadius: 'var(--radius-sm)',
                      cursor: 'pointer',
                      fontSize: 12,
                      marginBottom: 12
                    }}
                  >
                    Limpiar
                  </button>
                </div>
                <button
                  className="action-btn action-btn--primary"
                  style={{ width: '100%' }}
                  onClick={handleSaveSensor}
                  disabled={saving}
                >
                  {saving ? <><span className="spinner" /> Guardando...</> : '✓ Guardar Sensor'}
                </button>
              </>
            )}

            {!markerLocation && (
              <div style={{ padding: '12px 16px', background: 'var(--color-bg)', borderRadius: 'var(--radius-sm)', fontSize: 13, color: 'var(--color-text-secondary)' }}>
                <strong style={{ color: 'var(--color-text)' }}>Instrucciones:</strong><br />
                1. Haz clic en el mapa para marcar la ubicación<br />
                2. Ingresa el nombre del sensor<br />
                3. Haz clic en guardar
              </div>
            )}
          </div>

          {sensors.length > 0 && (
            <div className="card">
              <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 12 }}>
                Sensores Existentes ({sensors.length})
              </h3>

              {sensors.map(sensor => (
                <div
                  key={sensor.id}
                  className="parcel-item"
                  style={{
                    padding: '12px',
                    marginBottom: '8px',
                    background: 'var(--color-bg)',
                    borderRadius: '6px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between'
                  }}
                >
                  <div className="info">
                    <span className="name" style={{ color: '#3b82f6', fontWeight: 600 }}>
                      {sensor.name || `Sensor ${sensor.id}`}
                    </span>
                  </div>

                  <div style={{ display: 'flex', gap: 6 }}>
                    <button
                      onClick={() => navigate(`/sensors/${sensor.id}/session`)}
                      style={{
                        background: '#3b82f6',
                        color: 'white',
                        border: 'none',
                        padding: '4px 8px',
                        borderRadius: '4px',
                        cursor: 'pointer',
                        fontSize: 11,
                        fontWeight: 500
                      }}
                    >
                      Sesión
                    </button>
                    <button
                      onClick={() => handleDeleteSensor(sensor.id)}
                      style={{
                        background: 'none',
                        border: 'none',
                        color: '#ef4444',
                        cursor: 'pointer',
                        fontSize: 16
                      }}
                    >
                      🗑️
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {sensors.length === 0 && !loading && (
            <div className="card">
              <div style={{ padding: '24px', textAlign: 'center', color: 'var(--color-text-secondary)' }}>
                <div style={{ fontSize: 32, marginBottom: 8 }}></div>
                <div style={{ fontWeight: 600, marginBottom: 8 }}>Sin sensores</div>
                <div style={{ fontSize: 12 }}>
                  Crea el primer sensor haciendo click en el mapa →
                </div>
              </div>
            </div>
          )}
        </div>

      </div>

    </div>
  )
}
