import { useState, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { MapContainer, TileLayer, GeoJSON, useMap, Tooltip, Marker } from 'react-leaflet'
import * as turf from '@turf/turf'
import L from 'leaflet'
import { useTerrain } from '../hooks'
import { getBiomassColor, getBiomassLabel, getDailyConsumption } from '../utils/grazing'
import Spinner from '../components/Spinner'
import ConfirmDialog from '../components/ConfirmDialog'

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

function createNameIcon(name) {
  return L.divIcon({ className: 'parcel-center-label', html: `<span>${name}</span>`, iconSize: [0, 0], iconAnchor: [0, 0] })
}

function getCentroid(geoJson) {
  try {
    const geo = typeof geoJson === 'string' ? JSON.parse(geoJson) : geoJson
    const center = turf.centroid(geo)
    return [center.geometry.coordinates[1], center.geometry.coordinates[0]]
  } catch { return null }
}

const STATUS_COLORS = {
  DISPONIBLE:  'var(--color-status-disponible)',
  EN_USO:      'var(--color-status-en-uso)',
  EN_DESCANSO: 'var(--color-status-descanso)',
}
// Leaflet requires actual hex values — keep separate for map use
const STATUS_COLORS_HEX = { DISPONIBLE: '#4ade80', EN_USO: '#f59e0b', EN_DESCANSO: '#3b82f6' }
const STATUS_LABELS = { DISPONIBLE: 'Disponible', EN_USO: 'En Uso', EN_DESCANSO: 'En Descanso' }

export default function LotesPage() {
  const { terrainId } = useParams()
  const navigate = useNavigate()
  const {
    terrain, parcels, lotes, parcelInfo, grazingAlerts, loading,
    createLote, assignParcel, unassignParcel, closeLote, deleteLote,
  } = useTerrain(terrainId)

  const [showCreate, setShowCreate] = useState(false)
  const [showAssign, setShowAssign] = useState(null)
  const [form, setForm] = useState({ name: '', fechaIngreso: new Date().toISOString().split('T')[0] })
  const [closingLote, setClosingLote] = useState(null)
  const [closeFecha, setCloseFecha] = useState(new Date().toISOString().split('T')[0])
  const [confirm, setConfirm] = useState(null)

  async function handleCreate(e) {
    e.preventDefault()
    if (!form.name.trim()) return
    try {
      await createLote(form)
      setShowCreate(false)
      setForm({ name: '', fechaIngreso: new Date().toISOString().split('T')[0] })
    } catch { /* toast shown in hook */ }
  }

  async function handleAssign(loteId, parcelId) {
    try { await assignParcel(loteId, parcelId); setShowAssign(null) }
    catch { /* toast shown in hook */ }
  }

  function handleUnassign(loteId) {
    setConfirm({
      title: 'Mover lote',
      message: '¿Retirar el lote del potrero actual?',
      confirmLabel: 'Retirar',
      variant: 'warning',
      onConfirm: async () => {
        try { await unassignParcel(loteId) } catch { /* toast shown in hook */ }
      },
    })
  }

  function handleClose(loteId) {
    setClosingLote(loteId)
    setCloseFecha(new Date().toISOString().split('T')[0])
  }

  async function confirmClose() {
    if (!closingLote) return
    try { await closeLote(closingLote, { fechaSalida: closeFecha }) }
    catch { /* toast shown in hook */ }
    finally { setClosingLote(null) }
  }

  function handleDelete(loteId, name) {
    setConfirm({
      title: `Eliminar "${name}"`,
      message: `¿Eliminar este lote y todo su ganado? Esta acción no se puede deshacer.`,
      confirmLabel: 'Eliminar',
      variant: 'danger',
      onConfirm: async () => {
        try { await deleteLote(loteId) } catch { /* toast shown in hook */ }
      },
    })
  }

  const availableParcels = parcels.filter(p => p.status === 'DISPONIBLE' || p.status === 'EN_DESCANSO')
  const activeLotes = lotes.filter(l => !l.fechaSalida)
  const closedLotes = lotes.filter(l => l.fechaSalida)

  if (loading) return <Spinner page label="Cargando lotes..." />

  return (
    <div className="page-container">
      <ConfirmDialog
        open={!!confirm}
        title={confirm?.title}
        message={confirm?.message}
        confirmLabel={confirm?.confirmLabel || 'Confirmar'}
        variant={confirm?.variant || 'danger'}
        onConfirm={() => { confirm?.onConfirm?.(); setConfirm(null) }}
        onCancel={() => setConfirm(null)}
      />

      <div className="breadcrumb">
        <Link to="/farms">Fincas</Link><span className="sep">/</span>
        {terrain && <Link to={`/terrains/${terrainId}/parcels`}>{terrain.name}</Link>}
        <span className="sep">/</span><span>Lotes</span>
      </div>

      {grazingAlerts.length > 0 && (
        <div className="grazing-alerts" style={{ marginBottom: 16 }}>
          {grazingAlerts.map((alert, i) => (
            <div key={i} className={`grazing-alert grazing-alert--${alert.severity}`}>
              <span className="grazing-alert__icon">{alert.severity === 'critical' ? '🚨' : '⚠️'}</span>
              <span className="grazing-alert__msg">{alert.message}</span>
            </div>
          ))}
        </div>
      )}

      <div className="lotes-layout">
        {/* LEFT: Map */}
        <div className="lotes-map-panel">
          <div className="panel-header">
            <h3>Potreros</h3>
            <div className="map-legend">
              <span className="legend-item">
                <span className="legend-dot" style={{ background: 'var(--color-status-disponible)' }} /> Disponible
              </span>
              <span className="legend-item">
                <span className="legend-dot" style={{ background: 'var(--color-status-en-uso)' }} /> En Uso
              </span>
              <span className="legend-item">
                <span className="legend-dot" style={{ background: 'var(--color-status-descanso)' }} /> Descanso
              </span>
            </div>
          </div>

          <div className="map-wrapper" style={{ borderRadius: 10, overflow: 'hidden', border: '1px solid var(--color-border)' }}>
            <MapContainer center={[4.6, -74.1]} zoom={15} style={{ height: '100%', width: '100%' }}>
              <TileLayer url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}" attribution="Esri" />
              {terrain?.geoJson && <FitBounds geoJson={terrain.geoJson} />}
              {terrain?.geoJson && (
                <GeoJSON
                  key={'terrain-' + terrainId}
                  data={typeof terrain.geoJson === 'string' ? JSON.parse(terrain.geoJson) : terrain.geoJson}
                  style={{ color: '#fff', weight: 2, fillOpacity: 0.03, dashArray: '8,4' }}
                />
              )}
              {parcels.map(p => {
                const info = parcelInfo[p.id] || {}
                const color = STATUS_COLORS_HEX[p.status] || '#888'
                let geo
                try { geo = typeof p.geoJson === 'string' ? JSON.parse(p.geoJson) : p.geoJson } catch { return null }
                return (
                  <GeoJSON
                    key={`p-${p.id}-${p.status}`}
                    data={geo}
                    style={{
                      color, weight: info.lote ? 3 : 1.5, fillColor: color,
                      fillOpacity: info.lote ? 0.35 : 0.12,
                      dashArray: p.status === 'EN_DESCANSO' ? '6,3' : undefined,
                    }}
                  >
                    <Tooltip sticky className="parcel-tooltip">
                      <div style={{ minWidth: 200 }}>
                        <div style={{ fontWeight: 700, marginBottom: 5, fontSize: 15, color: '#1a1a1a' }}>{p.name}</div>
                        <div style={{ display: 'flex', gap: 6, marginBottom: 6, alignItems: 'center' }}>
                          <span style={{ padding: '2px 10px', borderRadius: 10, fontSize: 11, fontWeight: 600, background: color + '22', color }}>
                            {STATUS_LABELS[p.status]}
                          </span>
                          <span style={{ fontSize: 12, color: '#888' }}>{p.areaHectares?.toFixed(2)} ha</span>
                        </div>
                        {info.biomass != null && (
                          <div style={{ fontSize: 13, marginBottom: 4, padding: '4px 0', borderTop: '1px solid #eee' }}>
                            <span style={{ color: '#555' }}>Pasto: </span>
                            <span style={{ fontWeight: 700, fontSize: 14, color: getBiomassColor(info.biomass) }}>
                              {Math.round(info.biomass).toLocaleString()} kg/ha
                            </span>
                            <span style={{ fontSize: 11, color: '#888', marginLeft: 4 }}>({getBiomassLabel(info.biomass)})</span>
                          </div>
                        )}
                        {info.lote && (
                          <div style={{ fontSize: 12, color: '#b45309', marginBottom: 4, fontWeight: 600, padding: '3px 0' }}>
                            {info.lote.name} — {info.lote.cabezas} cabezas
                          </div>
                        )}
                        {info.grazingDays != null && (
                          <div style={{ fontSize: 13, fontWeight: 600, color: info.grazingDays <= 3 ? 'var(--color-ndvi-bad)' : info.grazingDays <= 7 ? 'var(--color-warning)' : 'var(--color-ndvi-good)' }}>
                            ~{info.grazingDays} dias de pasto restante
                          </div>
                        )}
                        {info.ndvi !== null ? (
                          <div style={{ fontSize: 12, marginTop: 2 }}>
                            <span style={{ color: '#888' }}>NDVI: </span>
                            <span style={{ fontWeight: 600, color: info.ndvi > 0.5 ? 'var(--color-ndvi-good)' : info.ndvi > 0.3 ? 'var(--color-ndvi-mid)' : 'var(--color-ndvi-bad)' }}>
                              {info.ndvi.toFixed(3)}
                            </span>
                          </div>
                        ) : (
                          <div style={{ fontSize: 11, color: '#999', fontStyle: 'italic', marginTop: 2 }}>Sin datos NDVI</div>
                        )}
                      </div>
                    </Tooltip>
                  </GeoJSON>
                )
              })}
              {parcels.map(p => {
                const center = getCentroid(p.geoJson)
                if (!center) return null
                return <Marker key={`label-${p.id}`} position={center} icon={createNameIcon(p.name)} interactive={false} />
              })}
            </MapContainer>
          </div>

          <div className="side-nav-buttons" style={{ marginTop: 10, flexDirection: 'row' }}>
            <button className="action-btn action-btn--nav" style={{ flex: 1 }} onClick={() => navigate(`/terrains/${terrainId}/parcels`)}>Potreros</button>
            <button className="action-btn action-btn--nav" style={{ flex: 1 }} onClick={() => navigate(`/terrains/${terrainId}/ndvi`)}>NDVI</button>
            <button className="action-btn action-btn--nav" style={{ flex: 1 }} onClick={() => navigate(`/terrains/${terrainId}/rotation`)}>Pastoreo</button>
          </div>
        </div>

        {/* RIGHT: Lotes */}
        <div className="lotes-list-panel">
          <div className="panel-header">
            <h3>Lotes Activos ({activeLotes.length})</h3>
            <button className="action-btn action-btn--primary" onClick={() => setShowCreate(!showCreate)}>
              + Nuevo Lote
            </button>
          </div>

          {showCreate && (
            <form onSubmit={handleCreate} className="create-lote-form">
              <input type="text" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })}
                placeholder="Nombre del lote" className="input-field" />
              <input type="date" value={form.fechaIngreso} onChange={e => setForm({ ...form, fechaIngreso: e.target.value })}
                className="input-field" />
              <div style={{ display: 'flex', gap: 6 }}>
                <button type="submit" className="action-btn action-btn--primary" style={{ flex: 1 }}>Crear</button>
                <button type="button" className="action-btn action-btn--ghost" onClick={() => setShowCreate(false)}>×</button>
              </div>
            </form>
          )}

          {activeLotes.length === 0 && !showCreate && (
            <div style={{ textAlign: 'center', padding: '32px 16px', color: 'var(--color-text-muted)', fontSize: 13 }}>
              Sin lotes activos. Crea un lote para comenzar.
            </div>
          )}

          {activeLotes.map(lote => {
            const parcelForLote = lote.currentParcelId ? parcels.find(p => p.id === lote.currentParcelId) : null
            const info = parcelForLote ? parcelInfo[parcelForLote.id] : null
            const grazingDays = info?.grazingDays
            const dailyConsumption = getDailyConsumption(lote)

            return (
              <div key={lote.id} className="lote-card-v2">
                <div className="lote-card-v2__title-row">
                  <h4 className="lote-card-v2__name">{lote.name}</h4>
                  <span className="lote-card-v2__date">{lote.fechaIngreso}</span>
                </div>

                {lote.currentParcelName ? (
                  <div className="lote-card-v2__parcel-badge">
                    <span className="lote-card-v2__parcel-dot" />
                    <span className="lote-card-v2__parcel-name">{lote.currentParcelName}</span>
                    {parcelForLote && (
                      <span className="lote-card-v2__parcel-area">{parcelForLote.areaHectares?.toFixed(2)} ha</span>
                    )}
                  </div>
                ) : (
                  <div className="lote-card-v2__no-parcel">Sin potrero asignado</div>
                )}

                <div className="lote-card-v2__stats">
                  <div className="lote-stat">
                    <div className="lote-stat__value">{lote.cabezas}</div>
                    <div className="lote-stat__label">Cabezas</div>
                  </div>
                  <div className="lote-stat">
                    <div className="lote-stat__value">{lote.pesoPromedioActual || '—'}<span className="lote-stat__unit">kg</span></div>
                    <div className="lote-stat__label">Peso Prom.</div>
                  </div>
                  <div className="lote-stat">
                    <div className="lote-stat__value" style={{ color: dailyConsumption ? 'var(--color-warning)' : 'var(--color-text-muted)' }}>
                      {dailyConsumption || '—'}<span className="lote-stat__unit">kg/dia</span>
                    </div>
                    <div className="lote-stat__label">Consumo forraje</div>
                  </div>
                  <div className="lote-stat">
                    <div className="lote-stat__value" style={{
                      color: grazingDays == null ? 'var(--color-text-muted)'
                        : grazingDays <= 3 ? 'var(--color-danger)' : grazingDays <= 7 ? 'var(--color-warning)' : 'var(--color-primary)',
                    }}>
                      {grazingDays != null ? `~${grazingDays}` : '—'}<span className="lote-stat__unit">dias</span>
                    </div>
                    <div className="lote-stat__label">Pasto Restante</div>
                  </div>
                </div>

                {grazingDays != null && (
                  <div className="lote-card-v2__progress">
                    <div className="lote-card-v2__progress-bar">
                      <div className="lote-card-v2__progress-fill" style={{
                        width: `${Math.min(100, (grazingDays / 30) * 100)}%`,
                        background: grazingDays <= 3 ? 'var(--color-danger)' : grazingDays <= 7 ? 'var(--color-warning)' : 'var(--color-primary)',
                      }} />
                    </div>
                  </div>
                )}

                {lote.gananciaPromedioKg != null && (
                  <div className="lote-card-v2__gain" style={{
                    color: lote.gananciaPromedioKg > 0 ? 'var(--color-positive)' : lote.gananciaPromedioKg < 0 ? 'var(--color-danger)' : 'var(--color-text-muted)',
                  }}>
                    {lote.gananciaPromedioKg > 0 ? '▲' : lote.gananciaPromedioKg < 0 ? '▼' : '—'} {lote.gananciaPromedioKg > 0 ? '+' : ''}{lote.gananciaPromedioKg} kg ganancia promedio
                  </div>
                )}

                <div className="lote-card-v2__actions">
                  <button className="action-btn action-btn--nav-sm" onClick={() => navigate(`/lotes/${lote.id}`)}>Gestionar</button>
                  {!lote.currentParcelId ? (
                    <button className="action-btn action-btn--nav-sm action-btn--accent"
                      onClick={() => setShowAssign(showAssign === lote.id ? null : lote.id)}>Asignar Potrero</button>
                  ) : (
                    <button className="action-btn action-btn--nav-sm action-btn--warning"
                      onClick={() => handleUnassign(lote.id)}>Mover</button>
                  )}
                  <button className="action-btn action-btn--nav-sm action-btn--ghost" onClick={() => handleClose(lote.id)}>Cerrar</button>
                </div>

                {closingLote === lote.id && (
                  <div className="create-lote-form" style={{ marginTop: 8 }}>
                    <label className="field-label">Fecha de salida</label>
                    <input type="date" className="input-field" value={closeFecha}
                      onChange={e => setCloseFecha(e.target.value)} />
                    <div style={{ display: 'flex', gap: 6 }}>
                      <button className="action-btn action-btn--primary action-btn--small" style={{ flex: 1 }} onClick={confirmClose}>
                        Confirmar Cierre
                      </button>
                      <button className="action-btn action-btn--ghost action-btn--small" onClick={() => setClosingLote(null)}>
                        Cancelar
                      </button>
                    </div>
                  </div>
                )}

                {showAssign === lote.id && (
                  <div className="assign-dropdown">
                    {availableParcels.length === 0
                      ? <p style={{ color: 'var(--color-danger)', fontSize: 13, margin: 0 }}>No hay potreros disponibles</p>
                      : <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                        {availableParcels.map(p => {
                          const pInfo = parcelInfo[p.id] || {}
                          return (
                            <button key={p.id} className="action-btn action-btn--outline"
                              onClick={() => handleAssign(lote.id, p.id)}
                              style={{ fontSize: 13, padding: '8px 14px' }}>
                              <span className="legend-dot" style={{ background: STATUS_COLORS_HEX[p.status], width: 10, height: 10 }} />
                              {p.name}
                              {pInfo.biomass != null && (
                                <span style={{ color: getBiomassColor(pInfo.biomass), fontSize: 11, marginLeft: 6 }}>
                                  {Math.round(pInfo.biomass)} kg/ha
                                </span>
                              )}
                            </button>
                          )
                        })}
                      </div>
                    }
                  </div>
                )}
              </div>
            )
          })}

          {closedLotes.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <div style={{ fontSize: 11, color: 'var(--color-text-muted)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 8 }}>
                Cerrados ({closedLotes.length})
              </div>
              {closedLotes.map(lote => (
                <div key={lote.id} className="lote-item lote-item--closed">
                  <div className="lote-item__header">
                    <span className="lote-item__name">{lote.name}</span>
                    <span className="lote-item__meta">{lote.cabezas} cab. · {lote.fechaIngreso} → {lote.fechaSalida}</span>
                  </div>
                  <div className="lote-item__actions">
                    <button className="action-btn action-btn--nav-sm action-btn--ghost" onClick={() => navigate(`/lotes/${lote.id}`)}>Ver</button>
                    <button className="action-btn action-btn--nav-sm action-btn--danger" onClick={() => handleDelete(lote.id, lote.name)}>Eliminar</button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
