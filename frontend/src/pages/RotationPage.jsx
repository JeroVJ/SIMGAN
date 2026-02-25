import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { MapContainer, TileLayer, GeoJSON, useMap } from 'react-leaflet'
import { terrainApi, parcelApi } from '../services/api'
import toast from 'react-hot-toast'
import L from 'leaflet'

function FitBounds({ geoJson }) {
  const map = useMap()
  useEffect(() => {
    if (geoJson) {
      try {
        const geo = typeof geoJson === 'string' ? JSON.parse(geoJson) : geoJson
        const layer = L.geoJSON(geo)
        map.fitBounds(layer.getBounds(), { padding: [30, 30] })
      } catch {}
    }
  }, [geoJson, map])
  return null
}

const STATUS_CONFIG = {
  DISPONIBLE: { label: 'Disponible', color: '#4ade80', bgAlpha: 0.35, icon: '🌿' },
  EN_USO: { label: 'En uso', color: '#f59e0b', bgAlpha: 0.45, icon: '🐄' },
  EN_DESCANSO: { label: 'En descanso', color: '#3b82f6', bgAlpha: 0.3, icon: '💤' }
}

export default function RotationPage() {
  const { terrainId } = useParams()
  const navigate = useNavigate()

  const [terrain, setTerrain] = useState(null)
  const [parcels, setParcels] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadData()
  }, [terrainId])

  async function loadData() {
    try {
      const [terrainData, parcelsData] = await Promise.all([
        terrainApi.getById(terrainId),
        parcelApi.getByTerrain(terrainId)
      ])
      setTerrain(terrainData)
      setParcels(parcelsData)
    } catch {
      toast.error('Error cargando datos')
    } finally {
      setLoading(false)
    }
  }

  async function cycleStatus(parcelId, currentStatus) {
    const order = ['DISPONIBLE', 'EN_USO', 'EN_DESCANSO']
    const currentIdx = order.indexOf(currentStatus)
    const nextStatus = order[(currentIdx + 1) % order.length]

    try {
      await parcelApi.updateStatus(parcelId, nextStatus)
      const updated = await parcelApi.getByTerrain(terrainId)
      setParcels(updated)
    } catch {
      toast.error('Error actualizando estado')
    }
  }

  if (loading) {
    return (
      <div className="empty-state">
        <div className="spinner" />
        <p style={{ marginTop: 16 }}>Cargando rotación...</p>
      </div>
    )
  }

  const terrainGeoJson = terrain ? (() => {
    try { return JSON.parse(terrain.geoJson) } catch { return null }
  })() : null

  const statusCounts = {
    DISPONIBLE: parcels.filter(p => p.status === 'DISPONIBLE').length,
    EN_USO: parcels.filter(p => p.status === 'EN_USO').length,
    EN_DESCANSO: parcels.filter(p => p.status === 'EN_DESCANSO').length
  }

  return (
    <div>
      <div className="page-header">
        <div className="breadcrumb">
          <a href="/farms">Fincas</a>
          <span>›</span>
          <a href={`/terrains/${terrainId}/parcels`}>{terrain?.name || 'Terreno'}</a>
          <span>›</span>
          <span>Rotación</span>
        </div>
        <h2>Rotación de Pastoreo</h2>
        <p>Gestiona el estado de cada parcela para la rotación del ganado</p>
      </div>

      {/* Status summary */}
      <div style={{ display: 'flex', gap: 16, marginBottom: 24 }}>
        {Object.entries(STATUS_CONFIG).map(([key, config]) => (
          <div
            key={key}
            style={{
              flex: 1,
              padding: '16px 20px',
              background: 'var(--color-surface)',
              border: `1px solid ${config.color}33`,
              borderRadius: 'var(--radius)',
              display: 'flex',
              alignItems: 'center',
              gap: 12
            }}
          >
            <span style={{ fontSize: 28 }}>{config.icon}</span>
            <div>
              <div style={{ fontSize: 11, color: 'var(--color-text-muted)', textTransform: 'uppercase', letterSpacing: 1 }}>
                {config.label}
              </div>
              <div style={{ fontSize: 28, fontFamily: 'var(--font-display)', color: config.color }}>
                {statusCounts[key]}
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="two-col">
        {/* Map with colored parcels */}
        <div className="col-main">
          <div className="map-container" style={{ height: 450 }}>
            <MapContainer center={[4.6, -74.1]} zoom={15} style={{ height: '100%', width: '100%' }}>
              {terrainGeoJson && <FitBounds geoJson={terrainGeoJson} />}

              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                attribution="Tiles &copy; Esri"
                maxZoom={19}
              />

              {terrainGeoJson && (
                <GeoJSON
                  data={terrainGeoJson}
                  style={{ color: '#ffffff', weight: 2, fillOpacity: 0.05, dashArray: '6,3' }}
                />
              )}

              {parcels.map((p) => {
                try {
                  const geo = JSON.parse(p.geoJson)
                  const config = STATUS_CONFIG[p.status] || STATUS_CONFIG.DISPONIBLE
                  return (
                    <GeoJSON
                      key={`${p.id}-${p.status}`}
                      data={geo}
                      style={{
                        color: config.color,
                        weight: 3,
                        fillColor: config.color,
                        fillOpacity: config.bgAlpha
                      }}
                    />
                  )
                } catch {
                  return null
                }
              })}
            </MapContainer>
          </div>

          {/* Legend */}
          <div style={{ display: 'flex', gap: 24, marginTop: 12 }}>
            {Object.entries(STATUS_CONFIG).map(([key, config]) => (
              <div key={key} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--color-text-secondary)' }}>
                <span style={{ width: 14, height: 14, borderRadius: 3, background: config.color, opacity: 0.8 }} />
                {config.label}
              </div>
            ))}
          </div>
        </div>

        {/* Rotation controls */}
        <div className="col-side">
          <div className="card">
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 16 }}>
              Control de Parcelas
            </h3>
            <p style={{ fontSize: 13, color: 'var(--color-text-secondary)', marginBottom: 16 }}>
              Haz clic en una parcela para cambiar su estado de rotación.
            </p>

            <div className="rotation-grid" style={{ gridTemplateColumns: '1fr' }}>
              {parcels.map((p) => {
                const config = STATUS_CONFIG[p.status] || STATUS_CONFIG.DISPONIBLE
                return (
                  <div
                    key={p.id}
                    className="rotation-card"
                    style={{
                      cursor: 'pointer',
                      borderColor: `${config.color}44`,
                      textAlign: 'left',
                      transition: 'all 0.2s'
                    }}
                    onClick={() => cycleStatus(p.id, p.status)}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <h4 style={{ fontFamily: 'var(--font-display)', fontSize: 16 }}>{p.name}</h4>
                        <div className="area-info" style={{ margin: 0 }}>{p.areaHectares?.toFixed(2)} ha</div>
                      </div>
                      <div className={`status-badge ${getStatusClass(p.status)}`}>
                        {config.icon} {config.label}
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          <div className="card" style={{ fontSize: 13, color: 'var(--color-text-secondary)', lineHeight: 1.7 }}>
            <strong style={{ color: 'var(--color-text)' }}>🔮 Próximamente:</strong><br />
            Integración con <span style={{ color: 'var(--color-primary)' }}>Sentinel-2 (ESA)</span> para análisis NDVI automático y recomendaciones de rotación basadas en salud vegetacional del pastizal.
          </div>

          <button
            className="btn btn-secondary"
            style={{ width: '100%' }}
            onClick={() => navigate(`/terrains/${terrainId}/parcels`)}
          >
            ← Volver a Parcelas
          </button>
          <button
            className="btn btn-primary"
            style={{ width: '100%' }}
            onClick={() => navigate(`/terrains/${terrainId}/ndvi`)}
          >
            🛰️ Dashboard NDVI
          </button>
        </div>
      </div>
    </div>
  )
}

function getStatusClass(status) {
  switch (status) {
    case 'DISPONIBLE': return 'disponible'
    case 'EN_USO': return 'en-uso'
    case 'EN_DESCANSO': return 'en-descanso'
    default: return 'disponible'
  }
}
