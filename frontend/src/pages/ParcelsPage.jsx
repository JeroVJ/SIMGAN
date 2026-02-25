import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { MapContainer, TileLayer, FeatureGroup, GeoJSON, useMap } from 'react-leaflet'
import { EditControl } from 'react-leaflet-draw'
import * as turf from '@turf/turf'
import { terrainApi, parcelApi } from '../services/api'
import toast from 'react-hot-toast'
import L from 'leaflet'

delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png'
})

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

const PARCEL_COLORS = ['#4ade80', '#3b82f6', '#f59e0b', '#ef4444', '#a855f7', '#ec4899', '#14b8a6', '#f97316']

export default function ParcelsPage() {
  const { terrainId } = useParams()
  const navigate = useNavigate()

  const [terrain, setTerrain] = useState(null)
  const [parcels, setParcels] = useState([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [parcelName, setParcelName] = useState('')
  const [drawnParcel, setDrawnParcel] = useState(null)
  const [drawnArea, setDrawnArea] = useState({ sqm: 0, ha: 0 })
  const featureGroupRef = useRef(null)

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
      toast.error('Error cargando terreno')
      navigate('/farms')
    } finally {
      setLoading(false)
    }
  }

  function handleCreated(e) {
    const layer = e.layer
    const geoJson = layer.toGeoJSON()
    const areaM2 = turf.area(geoJson)

    // Validate parcel is inside terrain (optional nice-to-have)
    if (terrain) {
      try {
        const terrainGeo = JSON.parse(terrain.geoJson)
        const isInside = turf.booleanContains(terrainGeo, geoJson)
        if (!isInside) {
          toast('⚠️ La parcela no está completamente dentro del terreno', { icon: '⚠️' })
        }
      } catch {}
    }

    setDrawnParcel(geoJson)
    setDrawnArea({ sqm: areaM2, ha: areaM2 / 10000 })
  }

  function handleDeleted() {
    setDrawnParcel(null)
    setDrawnArea({ sqm: 0, ha: 0 })
  }

  async function handleSaveParcel() {
    if (!drawnParcel) {
      toast.error('Dibuja una parcela primero')
      return
    }

    setSaving(true)
    try {
      const payload = {
        name: parcelName.trim() || `Parcela ${parcels.length + 1}`,
        terrainId: parseInt(terrainId),
        geoJson: JSON.stringify(drawnParcel),
        areaSqMeters: drawnArea.sqm,
        areaHectares: drawnArea.ha
      }

      await parcelApi.create(payload)
      toast.success('Parcela guardada')

      // Reset drawing
      setDrawnParcel(null)
      setDrawnArea({ sqm: 0, ha: 0 })
      setParcelName('')

      // Clear drawn layers
      if (featureGroupRef.current) {
        featureGroupRef.current.clearLayers()
      }

      // Reload parcels
      const updated = await parcelApi.getByTerrain(terrainId)
      setParcels(updated)
    } catch {
      toast.error('Error guardando parcela')
    } finally {
      setSaving(false)
    }
  }

  async function handleStatusChange(parcelId, newStatus) {
    try {
      await parcelApi.updateStatus(parcelId, newStatus)
      const updated = await parcelApi.getByTerrain(terrainId)
      setParcels(updated)
      toast.success('Estado actualizado')
    } catch {
      toast.error('Error actualizando estado')
    }
  }

  async function handleDeleteParcel(id) {
    if (!window.confirm('¿Eliminar esta parcela?')) return
    try {
      await parcelApi.delete(id)
      const updated = await parcelApi.getByTerrain(terrainId)
      setParcels(updated)
      toast.success('Parcela eliminada')
    } catch {
      toast.error('Error eliminando parcela')
    }
  }

  function getStatusClass(status) {
    switch (status) {
      case 'DISPONIBLE': return 'disponible'
      case 'EN_USO': return 'en-uso'
      case 'EN_DESCANSO': return 'en-descanso'
      default: return 'disponible'
    }
  }

  function getStatusLabel(status) {
    switch (status) {
      case 'DISPONIBLE': return 'Disponible'
      case 'EN_USO': return 'En uso'
      case 'EN_DESCANSO': return 'En descanso'
      default: return status
    }
  }

  if (loading) {
    return (
      <div className="empty-state">
        <div className="spinner" />
        <p style={{ marginTop: 16 }}>Cargando terreno...</p>
      </div>
    )
  }

  const terrainGeoJson = terrain ? (() => {
    try { return JSON.parse(terrain.geoJson) } catch { return null }
  })() : null

  const totalParcelArea = parcels.reduce((sum, p) => sum + (p.areaHectares || 0), 0)
  const coveragePercent = terrain?.areaHectares ? (totalParcelArea / terrain.areaHectares * 100) : 0

  return (
    <div>
      <div className="page-header">
        <div className="breadcrumb">
          <a href="/farms">Fincas</a>
          <span>›</span>
          <a href={`/farms/${terrain?.farmId}/terrain/new`}>{terrain?.farmName}</a>
          <span>›</span>
          <span>{terrain?.name || 'Terreno'}</span>
          <span>›</span>
          <span>Parcelas</span>
        </div>
        <h2>Parcelas del Terreno</h2>
        <p>{terrain?.name} — {terrain?.areaHectares?.toFixed(2)} hectáreas</p>
      </div>

      <div className="two-col">
        {/* Map */}
        <div className="col-main">
          <div className="map-container">
            <MapContainer center={[4.6, -74.1]} zoom={15} style={{ height: '100%', width: '100%' }}>
              {terrainGeoJson && <FitBounds geoJson={terrainGeoJson} />}

              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                attribution="Tiles &copy; Esri"
                maxZoom={19}
              />
              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"
                maxZoom={19}
              />

              {/* Terrain boundary */}
              {terrainGeoJson && (
                <GeoJSON
                  data={terrainGeoJson}
                  style={{
                    color: '#ffffff',
                    weight: 3,
                    fillOpacity: 0.05,
                    dashArray: '8,4'
                  }}
                />
              )}

              {/* Existing parcels */}
              {parcels.map((p, i) => {
                try {
                  const geo = JSON.parse(p.geoJson)
                  return (
                    <GeoJSON
                      key={p.id}
                      data={geo}
                      style={{
                        color: PARCEL_COLORS[i % PARCEL_COLORS.length],
                        weight: 2,
                        fillOpacity: 0.25
                      }}
                    />
                  )
                } catch {
                  return null
                }
              })}

              {/* Drawing controls */}
              <FeatureGroup ref={featureGroupRef}>
                <EditControl
                  position="topright"
                  onCreated={handleCreated}
                  onDeleted={handleDeleted}
                  draw={{
                    polygon: {
                      allowIntersection: false,
                      shapeOptions: {
                        color: '#f59e0b',
                        weight: 3,
                        fillOpacity: 0.3
                      }
                    },
                    rectangle: false,
                    circle: false,
                    circlemarker: false,
                    marker: false,
                    polyline: false
                  }}
                />
              </FeatureGroup>
            </MapContainer>
          </div>

          {/* Drawn parcel area */}
          {drawnParcel && (
            <div className="area-display">
              <div className="area-stat">
                <span className="label">Nueva Parcela</span>
                <span className="value">
                  {drawnArea.ha.toFixed(2)}
                  <span className="unit">ha</span>
                </span>
              </div>
              <div style={{ flex: 1 }}>
                <input
                  value={parcelName}
                  onChange={(e) => setParcelName(e.target.value)}
                  placeholder="Nombre de la parcela..."
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    background: 'var(--color-bg)',
                    border: '1px solid var(--color-border)',
                    borderRadius: 'var(--radius-sm)',
                    color: 'var(--color-text)',
                    fontSize: 14,
                    fontFamily: 'var(--font-body)'
                  }}
                />
              </div>
              <button className="btn btn-primary" onClick={handleSaveParcel} disabled={saving}>
                {saving ? <span className="spinner" /> : '✓ Guardar'}
              </button>
            </div>
          )}
        </div>

        {/* Side Panel */}
        <div className="col-side">
          {/* Stats */}
          <div className="card">
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 12 }}>
              Resumen
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div style={{ padding: 12, background: 'var(--color-bg)', borderRadius: 'var(--radius-sm)' }}>
                <div style={{ fontSize: 11, color: 'var(--color-text-muted)', textTransform: 'uppercase' }}>Total Terreno</div>
                <div style={{ fontSize: 20, fontFamily: 'var(--font-display)', color: 'var(--color-text)' }}>
                  {terrain?.areaHectares?.toFixed(2)} <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>ha</span>
                </div>
              </div>
              <div style={{ padding: 12, background: 'var(--color-bg)', borderRadius: 'var(--radius-sm)' }}>
                <div style={{ fontSize: 11, color: 'var(--color-text-muted)', textTransform: 'uppercase' }}>Parcelado</div>
                <div style={{ fontSize: 20, fontFamily: 'var(--font-display)', color: 'var(--color-primary)' }}>
                  {totalParcelArea.toFixed(2)} <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>ha</span>
                </div>
              </div>
              <div style={{ padding: 12, background: 'var(--color-bg)', borderRadius: 'var(--radius-sm)', gridColumn: '1/-1' }}>
                <div style={{ fontSize: 11, color: 'var(--color-text-muted)', textTransform: 'uppercase' }}>Cobertura</div>
                <div style={{ marginTop: 6, height: 8, background: 'var(--color-surface)', borderRadius: 4, overflow: 'hidden' }}>
                  <div style={{
                    height: '100%',
                    width: `${Math.min(coveragePercent, 100)}%`,
                    background: 'var(--color-primary)',
                    borderRadius: 4,
                    transition: 'width 0.3s'
                  }} />
                </div>
                <div style={{ fontSize: 13, color: 'var(--color-text-secondary)', marginTop: 4 }}>
                  {coveragePercent.toFixed(1)}% del terreno
                </div>
              </div>
            </div>
          </div>

          {/* Parcels list */}
          <div className="card">
            <div className="flex justify-between items-center mb-16">
              <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18 }}>
                Parcelas ({parcels.length})
              </h3>
              <button
                className="btn btn-secondary btn-sm"
                onClick={() => navigate(`/terrains/${terrainId}/rotation`)}
                disabled={parcels.length === 0}
              >
                🔄 Rotación
              </button>
              <button
                className="btn btn-primary btn-sm"
                onClick={() => navigate(`/terrains/${terrainId}/ndvi`)}
                disabled={parcels.length === 0}
              >
                🛰️ NDVI
              </button>
            </div>

            {parcels.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '24px 0', color: 'var(--color-text-muted)', fontSize: 13 }}>
                Dibuja parcelas en el mapa usando el polígono
              </div>
            ) : (
              <div className="parcel-list">
                {parcels.map((p, i) => (
                  <div key={p.id} className="parcel-item">
                    <div className="info">
                      <span className="name" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span style={{
                          width: 10,
                          height: 10,
                          borderRadius: '50%',
                          background: PARCEL_COLORS[i % PARCEL_COLORS.length],
                          flexShrink: 0
                        }} />
                        {p.name}
                      </span>
                      <span className="area">{p.areaHectares?.toFixed(2)} ha</span>
                    </div>
                    <div className="actions">
                      <select
                        className="status-select"
                        value={p.status}
                        onChange={(e) => handleStatusChange(p.id, e.target.value)}
                      >
                        <option value="DISPONIBLE">Disponible</option>
                        <option value="EN_USO">En uso</option>
                        <option value="EN_DESCANSO">En descanso</option>
                      </select>
                      <button
                        className="btn btn-danger btn-sm btn-icon"
                        onClick={() => handleDeleteParcel(p.id)}
                        title="Eliminar"
                      >
                        ×
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <button
            className="btn btn-secondary"
            style={{ width: '100%' }}
            onClick={() => navigate(`/farms/${terrain?.farmId}/terrain/new`)}
          >
            ← Volver al Terreno
          </button>
        </div>
      </div>
    </div>
  )
}
