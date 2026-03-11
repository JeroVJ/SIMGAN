import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import Spinner from '../components/Spinner'
import ConfirmDialog from '../components/ConfirmDialog'
import { MapContainer, TileLayer, FeatureGroup, GeoJSON, useMap, Tooltip } from 'react-leaflet'
import { EditControl } from 'react-leaflet-draw'
import * as turf from '@turf/turf'
import L from 'leaflet'
import toast from 'react-hot-toast'
import { useTerrain } from '../hooks'
import { getBiomassColor, getBiomassLabel } from '../utils/grazing'

delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
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

const STATUS_COLORS = { DISPONIBLE: '#4ade80', EN_USO: '#f59e0b', EN_DESCANSO: '#3b82f6' }
const STATUS_LABELS = { DISPONIBLE: 'Disponible', EN_USO: 'En Uso', EN_DESCANSO: 'En Descanso' }

export default function ParcelsPage() {
  const { terrainId } = useParams()
  const navigate = useNavigate()
  const { terrain, parcels, parcelInfo, loading, addParcel, updateParcelStatus, deleteParcel } = useTerrain(terrainId)

  const [saving, setSaving]           = useState(false)
  const [parcelName, setParcelName]   = useState('')
  const [drawnParcel, setDrawnParcel] = useState(null)
  const [drawnArea, setDrawnArea]     = useState({ sqm: 0, ha: 0 })
  const [confirm, setConfirm]         = useState(null)
  const featureGroupRef               = useRef(null)

  function handleCreated(e) {
    const layer = e.layer
    const geoJson = layer.toGeoJSON()
    const areaM2 = turf.area(geoJson)

    if (terrain) {
      try {
        const terrainGeo = JSON.parse(terrain.geoJson)
        const isInside = turf.booleanContains(terrainGeo, geoJson)
        if (!isInside) toast('La parcela no esta completamente dentro del terreno', { icon: '⚠️' })
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
    if (!drawnParcel) { toast.error('Dibuja una parcela primero'); return }
    setSaving(true)
    try {
      await addParcel({
        name: parcelName.trim() || `Parcela ${parcels.length + 1}`,
        terrainId: parseInt(terrainId),
        geoJson: JSON.stringify(drawnParcel),
        areaSqMeters: drawnArea.sqm,
        areaHectares: drawnArea.ha,
      })
      setDrawnParcel(null)
      setDrawnArea({ sqm: 0, ha: 0 })
      setParcelName('')
      if (featureGroupRef.current) featureGroupRef.current.clearLayers()
    } catch { /* toast shown in hook */ }
    finally { setSaving(false) }
  }

  async function handleStatusChange(parcelId, newStatus) {
    try { await updateParcelStatus(parcelId, newStatus) }
    catch { /* toast shown in hook */ }
  }

  function handleDeleteParcel(id) {
    setConfirm({
      id,
      message: '¿Eliminar esta parcela? Esta acción no se puede deshacer.',
    })
  }

  if (loading) return <Spinner page label="Cargando terreno..." />

  const terrainGeoJson = terrain ? (() => {
    try { return JSON.parse(terrain.geoJson) } catch { return null }
  })() : null

  const totalParcelArea = parcels.reduce((sum, p) => sum + (p.areaHectares || 0), 0)
  const coveragePercent = terrain?.areaHectares ? (totalParcelArea / terrain.areaHectares * 100) : 0

  return (
    <div className="page-container">
      <ConfirmDialog
        open={!!confirm}
        title="Eliminar parcela"
        message={confirm?.message}
        confirmLabel="Eliminar"
        variant="danger"
        onConfirm={async () => {
          try { await deleteParcel(confirm.id) } catch { /* toast shown in hook */ }
          setConfirm(null)
        }}
        onCancel={() => setConfirm(null)}
      />
      <div className="page-header">
        <div className="breadcrumb">
          <Link to="/farms">Fincas</Link>
          <span>›</span>
          <Link to={`/farms/${terrain?.farmId}/terrain/new`}>{terrain?.farmName}</Link>
          <span>›</span>
          <span>{terrain?.name || 'Terreno'}</span>
          <span>›</span>
          <span>Parcelas</span>
        </div>
        <h2>Parcelas del Terreno</h2>
        <p>{terrain?.name} — {terrain?.areaHectares?.toFixed(2)} hectareas</p>
      </div>

      <div className="two-col">
        {/* Map */}
        <div className="col-main">
          <div className="map-container">
            <MapContainer center={[4.6, -74.1]} zoom={15} style={{ height: '100%', width: '100%' }}>
              {terrainGeoJson && <FitBounds geoJson={terrainGeoJson} />}
              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                attribution="Tiles &copy; Esri" maxZoom={19}
              />
              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"
                maxZoom={19}
              />
              {terrainGeoJson && (
                <GeoJSON data={terrainGeoJson}
                  style={{ color: '#ffffff', weight: 3, fillOpacity: 0.05, dashArray: '8,4' }} />
              )}
              {parcels.map((p) => {
                const info = parcelInfo[p.id] || {}
                const color = STATUS_COLORS[p.status] || '#888'
                let geo
                try { geo = JSON.parse(p.geoJson) } catch { return null }
                return (
                  <GeoJSON
                    key={`p-${p.id}-${p.status}`}
                    data={geo}
                    style={{
                      color, weight: info.lote ? 3 : 2, fillColor: color,
                      fillOpacity: info.lote ? 0.35 : 0.2,
                      dashArray: p.status === 'EN_DESCANSO' ? '6,3' : undefined,
                    }}
                  >
                    <Tooltip sticky className="parcel-tooltip">
                      <div style={{ minWidth: 180 }}>
                        <div style={{ fontWeight: 700, marginBottom: 4, fontSize: 14, color: '#1a1a1a' }}>{p.name}</div>
                        <div style={{ display: 'flex', gap: 6, marginBottom: 5, alignItems: 'center' }}>
                          <span style={{ padding: '1px 8px', borderRadius: 10, fontSize: 11, fontWeight: 600, background: color + '22', color }}>
                            {STATUS_LABELS[p.status]}
                          </span>
                          <span style={{ fontSize: 12, color: '#888' }}>{p.areaHectares?.toFixed(2)} ha</span>
                        </div>
                        {info.lote && (
                          <div style={{ fontSize: 12, color: '#b45309', marginBottom: 3, fontWeight: 500 }}>
                            🐄 {info.lote.name} ({info.lote.cabezas} cab.)
                          </div>
                        )}
                        {info.biomass != null && (
                          <div style={{ fontSize: 12, marginBottom: 3 }}>
                            <span style={{ color: '#555' }}>Pasto: </span>
                            <span style={{ fontWeight: 600, color: getBiomassColor(info.biomass) }}>
                              {Math.round(info.biomass)} kg/ha
                            </span>
                            <span style={{ fontSize: 11, color: '#888' }}> ({getBiomassLabel(info.biomass)})</span>
                          </div>
                        )}
                        {info.ndvi !== null ? (
                          <div style={{ fontSize: 12 }}>
                            <span style={{ color: '#888' }}>NDVI: </span>
                            <span style={{ fontWeight: 600, color: info.ndvi > 0.5 ? '#16a34a' : info.ndvi > 0.3 ? '#ca8a04' : '#dc2626' }}>
                              {info.ndvi.toFixed(3)}
                            </span>
                          </div>
                        ) : (
                          <div style={{ fontSize: 11, color: '#999', fontStyle: 'italic' }}>Sin datos NDVI</div>
                        )}
                      </div>
                    </Tooltip>
                  </GeoJSON>
                )
              })}
              <FeatureGroup ref={featureGroupRef}>
                <EditControl
                  position="topright"
                  onCreated={handleCreated}
                  onDeleted={handleDeleted}
                  draw={{
                    polygon: { allowIntersection: false, shapeOptions: { color: '#f59e0b', weight: 3, fillOpacity: 0.3 } },
                    rectangle: false, circle: false, circlemarker: false, marker: false, polyline: false,
                  }}
                />
              </FeatureGroup>
            </MapContainer>
          </div>

          {drawnParcel && (
            <div className="area-display">
              <div className="area-stat">
                <span className="label">Nueva Parcela</span>
                <span className="value">{drawnArea.ha.toFixed(2)}<span className="unit">ha</span></span>
              </div>
              <div style={{ flex: 1 }}>
                <input
                  value={parcelName}
                  onChange={(e) => setParcelName(e.target.value)}
                  placeholder="Nombre de la parcela..."
                  style={{
                    width: '100%', padding: '8px 12px', background: 'var(--color-bg)',
                    border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)',
                    color: 'var(--color-text)', fontSize: 14, fontFamily: 'var(--font-body)',
                  }}
                />
              </div>
              <button className="action-btn action-btn--primary" onClick={handleSaveParcel} disabled={saving}>
                {saving ? <span className="spinner" /> : '✓ Guardar'}
              </button>
            </div>
          )}
        </div>

        {/* Side Panel */}
        <div className="col-side">
          <div className="card">
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 12 }}>Resumen</h3>
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
                    height: '100%', width: `${Math.min(coveragePercent, 100)}%`,
                    background: 'var(--color-primary)', borderRadius: 4, transition: 'width 0.3s',
                  }} />
                </div>
                <div style={{ fontSize: 13, color: 'var(--color-text-secondary)', marginTop: 4 }}>
                  {coveragePercent.toFixed(1)}% del terreno
                </div>
              </div>
            </div>
          </div>

          <div className="side-nav-buttons">
            <button className="action-btn action-btn--nav" onClick={() => navigate(`/terrains/${terrainId}/rotation`)} disabled={parcels.length === 0}>
              🔄 Rotacion
            </button>
            <button className="action-btn action-btn--nav" onClick={() => navigate(`/terrains/${terrainId}/lotes`)}>
              🐄 Lotes
            </button>
            <button className="action-btn action-btn--nav action-btn--primary" onClick={() => navigate(`/terrains/${terrainId}/ndvi`)} disabled={parcels.length === 0}>
              🛰️ NDVI
            </button>
          </div>

          <div className="card">
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 16 }}>
              Parcelas ({parcels.length})
            </h3>
            {parcels.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '24px 0', color: 'var(--color-text-muted)', fontSize: 13 }}>
                Dibuja parcelas en el mapa usando el poligono
              </div>
            ) : (
              <div className="parcel-list">
                {parcels.map((p) => {
                  const info = parcelInfo[p.id] || {}
                  const isLocked = !!info.lote
                  const color = STATUS_COLORS[p.status] || '#888'
                  return (
                    <div key={p.id} className={`parcel-item ${isLocked ? 'parcel-item--locked' : ''}`}>
                      <div className="info">
                        <span className="name" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span style={{ width: 10, height: 10, borderRadius: '50%', background: color, flexShrink: 0 }} />
                          {p.name}
                        </span>
                        <span className="area">
                          {p.areaHectares?.toFixed(2)} ha
                          {info.biomass != null && (
                            <span style={{ color: getBiomassColor(info.biomass), marginLeft: 6, fontWeight: 500 }}>
                              · {Math.round(info.biomass)} kg/ha
                            </span>
                          )}
                        </span>
                        {info.lote && (
                          <span style={{ fontSize: 11, color: '#f59e0b', fontWeight: 500 }}>
                            🔒 {info.lote.name}
                          </span>
                        )}
                      </div>
                      <div className="actions">
                        <select
                          className="status-select"
                          value={p.status}
                          onChange={(e) => handleStatusChange(p.id, e.target.value)}
                          disabled={isLocked}
                          title={isLocked ? `En uso por ${info.lote.name}` : 'Cambiar estado'}
                        >
                          <option value="DISPONIBLE">Disponible</option>
                          <option value="EN_USO">En uso</option>
                          <option value="EN_DESCANSO">En descanso</option>
                        </select>
                        <button
                          className="action-btn action-btn--danger action-btn--icon action-btn--small"
                          onClick={() => handleDeleteParcel(p.id)}
                          title={isLocked ? `En uso por ${info.lote.name}` : 'Eliminar'}
                          disabled={isLocked}
                        >
                          ×
                        </button>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          <button className="action-btn action-btn--nav" onClick={() => navigate(`/farms/${terrain?.farmId}/terrain/new`)}>
            ← Volver al Terreno
          </button>
        </div>
      </div>
    </div>
  )
}
