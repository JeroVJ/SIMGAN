import { useState, useEffect, useMemo } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { MapContainer, TileLayer, GeoJSON, Marker, Popup, useMap, useMapEvents } from 'react-leaflet'
import L from 'leaflet'
import toast from 'react-hot-toast'
import {
  ComposedChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts'
import Spinner from '../components/Spinner'
import OperationProgress from '../components/OperationProgress'
import { useBiomassCalibration, useCalibration } from '../hooks'

delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
})

const MIN_POINTS = 7

function extractRSquared(model) {
  if (!model) return null
  const candidates = [model.rSquared, model.r_squared, model.rsquared, model.r2, model.R2]
  for (const value of candidates) {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return null
}

function numberIcon(num) {
  return L.divIcon({
    className: 'biomass-marker-icon',
    html: `<div style="
      background:#2563eb;color:#fff;width:28px;height:28px;border-radius:50%;
      display:flex;align-items:center;justify-content:center;font-weight:700;
      font-size:13px;border:2px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,.35)
    ">${num}</div>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  })
}

function FitBounds({ geoJson }) {
  const map = useMap()
  useEffect(() => {
    if (!geoJson) return
    try {
      const geo = typeof geoJson === 'string' ? JSON.parse(geoJson) : geoJson
      const layer = L.geoJSON(geo)
      map.fitBounds(layer.getBounds(), { padding: [30, 30] })
    } catch { /* ignore */ }
  }, [geoJson, map])
  return null
}

function MapClickHandler({ onMapClick, enabled }) {
  useMapEvents({
    click(e) {
      if (enabled) onMapClick(e.latlng)
    },
  })
  return null
}

/** Recharts scatter + regression line chart */
function BiomassChart({ points, model }) {
  const valid = useMemo(
    () => points.filter(p => p.ndviAtPoint != null && p.biomassKgPerHa != null),
    [points]
  )

  const chartData = useMemo(() => {
    if (!valid.length || !model) return []
    const sorted = [...valid].sort((a, b) => a.ndviAtPoint - b.ndviAtPoint)
    const xMin = sorted[0].ndviAtPoint - 0.02
    const xMax = sorted[sorted.length - 1].ndviAtPoint + 0.02
    const a = model.coefficientA
    // Endpoints for regression line
    const lineStart = { ndvi: +xMin.toFixed(4), regrLine: +(a * xMin).toFixed(1) }
    const lineEnd   = { ndvi: +xMax.toFixed(4), regrLine: +(a * xMax).toFixed(1) }
    // Actual sample points: include regrLine so line passes through them smoothly
    const sampleRows = sorted.map(p => ({
      ndvi: +p.ndviAtPoint.toFixed(4),
      biomasa: +p.biomassKgPerHa.toFixed(1),
      regrLine: +(a * p.ndviAtPoint).toFixed(1),
    }))
    // Merge: all unique ndvi values in order
    const all = [lineStart, ...sampleRows, lineEnd]
    all.sort((a, b) => a.ndvi - b.ndvi)
    return all
  }, [valid, model])

  if (!chartData.length) return null

  return (
    <ResponsiveContainer width="100%" height={240}>
      <ComposedChart data={chartData} margin={{ top: 12, right: 16, bottom: 28, left: 16 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#2a3d2a" />
        <XAxis
          dataKey="ndvi"
          type="number"
          domain={['auto', 'auto']}
          tickFormatter={v => v.toFixed(2)}
          stroke="#5c7a5c"
          tick={{ fontSize: 10 }}
          label={{ value: 'NDVI', position: 'insideBottom', offset: -12, fill: '#94a3b8', fontSize: 11 }}
        />
        <YAxis
          stroke="#5c7a5c"
          tick={{ fontSize: 10 }}
          tickFormatter={v => Math.round(v)}
          width={52}
          label={{ value: 'kg/ha', angle: -90, position: 'insideLeft', fill: '#94a3b8', fontSize: 11, offset: 8 }}
        />
        <Tooltip
          contentStyle={{ background: '#172117', border: '1px solid #2a3d2a', borderRadius: 8, fontSize: 12 }}
          labelFormatter={v => `NDVI: ${(+v).toFixed(4)}`}
          formatter={(value, name) => [
            name === 'biomasa' ? `${value} kg/ha` : `${value} kg/ha`,
            name === 'biomasa' ? 'Muestra' : 'Regresión',
          ]}
        />
        <Legend
          wrapperStyle={{ fontSize: 11, paddingTop: 4 }}
          formatter={name => name === 'biomasa' ? 'Muestras de campo' : 'Regresión lineal'}
        />
        {/* Regression line — rendered first so dots appear on top */}
        <Line
          type="monotone"
          dataKey="regrLine"
          stroke="#f59e0b"
          strokeWidth={2}
          strokeDasharray="6 3"
          dot={false}
          name="regrLine"
          connectNulls
        />
        {/* Scatter dots — stroke 0 so no connecting line, only dots */}
        <Line
          type="monotone"
          dataKey="biomasa"
          stroke="transparent"
          strokeWidth={0}
          dot={{ fill: '#4ade80', r: 5, strokeWidth: 1.5, stroke: '#fff' }}
          activeDot={{ r: 7, fill: '#4ade80' }}
          name="biomasa"
          connectNulls={false}
        />
      </ComposedChart>
    </ResponsiveContainer>
  )
}

export default function CalibrationBiomassPage() {
  const { terrainId } = useParams()
  const navigate = useNavigate()
  const { status, loading, calibrating, calibrateParcel } = useBiomassCalibration(terrainId)
  const { status: optimStatus, loading: optimLoading } = useCalibration(terrainId, 'OPTIM')
  const { status: alertStatus, loading: alertLoading } = useCalibration(terrainId, 'ALERT')

  // Per-parcel local state for point placement
  const [activeParcelId, setActiveParcelId] = useState(null)
  const [parcelPoints, setParcelPoints] = useState({}) // { parcelId: [{lat, lng, cutAreaM2, greenWeightKg}] }
  const [placingPoints, setPlacingPoints] = useState(false)
  const [recalibratingIds, setRecalibratingIds] = useState(new Set()) // parcels being recalibrated

  // Initialize points from existing data
  useEffect(() => {
    if (!status?.parcels) return
    const initial = {}
    for (const p of status.parcels) {
      if (p.points && p.points.length > 0) {
        initial[p.parcelId] = p.points.map(pt => ({
          lat: pt.latitude,
          lng: pt.longitude,
          cutAreaM2: pt.cutAreaM2 || '',
          greenWeightKg: pt.greenWeightKg || '',
        }))
      }
    }
    setParcelPoints(prev => ({ ...initial, ...prev }))
  }, [status])

  const parcels = useMemo(() => status?.parcels || [], [status])

  const activeParcelRaw = useMemo(
    () => parcels.find(p => p.parcelId === activeParcelId),
    [parcels, activeParcelId]
  )

  // Override calibrated flag when user is recalibrating
  const activeParcel = useMemo(() => {
    if (!activeParcelRaw) return null
    if (recalibratingIds.has(activeParcelId)) {
      return { ...activeParcelRaw, calibrated: false, model: null, points: [] }
    }
    return activeParcelRaw
  }, [activeParcelRaw, activeParcelId, recalibratingIds])

  const activeRSquared = useMemo(
    () => extractRSquared(activeParcel?.model),
    [activeParcel?.model]
  )

  useEffect(() => {
    if (!optimLoading && !alertLoading && optimStatus?.calibrated && alertStatus && !alertStatus.calibrated) {
      navigate(`/terrains/${terrainId}/ndvi/calibration-alert`, { replace: true })
    }
  }, [optimLoading, alertLoading, optimStatus, alertStatus, terrainId, navigate])

  if (loading || optimLoading || alertLoading) return <Spinner page label="Cargando calibración de biomasa..." />

  if (!optimStatus?.calibrated) {
    return (
      <div className="page-container">
        <div className="page-header">
          <div className="breadcrumb">
            <Link to="/farms">Fincas</Link>
            <span>›</span>
            <Link to={`/terrains/${terrainId}/parcels`}>Terreno</Link>
            <span>›</span>
            <span>Calibración Biomasa</span>
          </div>
          <h2>Calibración de Biomasa</h2>
        </div>
        <div className="card mb-24" style={{ borderLeft: '4px solid #f59e0b' }}>
          <h3>Primero calibra el NDVI Óptimo</h3>
          <p style={{ color: 'var(--color-text-secondary)', margin: '12px 0' }}>
            La calibración de biomasa requiere una imagen satelital de referencia.
            Primero debes completar la calibración NDVI óptima.
          </p>
          <button
            className="action-btn action-btn--primary"
            onClick={() => navigate(`/terrains/${terrainId}/ndvi/calibration-optim`)}
          >
            Ir a Calibración NDVI Óptimo
          </button>
        </div>
      </div>
    )
  }

  const currentPoints = parcelPoints[activeParcelId] || []

  function handleMapClick(latlng) {
    if (!activeParcelId || !placingPoints) return
    setParcelPoints(prev => ({
      ...prev,
      [activeParcelId]: [...(prev[activeParcelId] || []), {
        lat: latlng.lat,
        lng: latlng.lng,
        cutAreaM2: '',
        greenWeightKg: '',
      }]
    }))
  }

  function removePoint(idx) {
    setParcelPoints(prev => ({
      ...prev,
      [activeParcelId]: (prev[activeParcelId] || []).filter((_, i) => i !== idx)
    }))
  }

  function updatePoint(idx, field, value) {
    setParcelPoints(prev => ({
      ...prev,
      [activeParcelId]: (prev[activeParcelId] || []).map((pt, i) =>
        i === idx ? { ...pt, [field]: value } : pt
      )
    }))
  }

  async function handleCalibrate() {
    if (!activeParcelId) return
    const pts = parcelPoints[activeParcelId] || []
    if (pts.length < MIN_POINTS) {
      toast.error(`Se requieren al menos ${MIN_POINTS} puntos. Tienes ${pts.length}.`)
      return
    }

    for (let i = 0; i < pts.length; i++) {
      const p = pts[i]
      if (!p.cutAreaM2 || !p.greenWeightKg || Number(p.cutAreaM2) <= 0 || Number(p.greenWeightKg) <= 0) {
        toast.error(`Completa el área y peso del punto ${i + 1}.`)
        return
      }
    }

    const points = pts.map((p, i) => ({
      pointIndex: i + 1,
      latitude: p.lat,
      longitude: p.lng,
      cutAreaM2: Number(p.cutAreaM2),
      greenWeightKg: Number(p.greenWeightKg),
    }))

    await calibrateParcel(activeParcelId, points)
    setPlacingPoints(false)
    setRecalibratingIds(prev => { const next = new Set(prev); next.delete(activeParcelId); return next })
  }

  const sceneInfo = optimStatus?.calibrations?.[0]

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div className="breadcrumb">
          <Link to="/farms">Fincas</Link>
          <span>›</span>
          <Link to={`/terrains/${terrainId}/parcels`}>Terreno</Link>
          <span>›</span>
          <span>Calibración Biomasa</span>
        </div>
        <div className="flex justify-between items-center">
          <div>
            <h2>Calibración de Biomasa por Potrero</h2>
            <p style={{ color: 'var(--color-text-secondary)' }}>
              {status?.calibratedParcels || 0}/{status?.totalParcels || 0} potreros calibrados
              {sceneInfo && <> · Escena: <strong>{sceneInfo.source}</strong> del {sceneInfo.calibrationDate}</>}
            </p>
          </div>
        </div>
        <OperationProgress
          active={calibrating}
          title="Calibracion de biomasa en curso"
          expectedSeconds={300}
          hint="La calibracion de biomasa puede tardar hasta 5 minutos."
        />
      </div>

      {/* How it works */}
      <div className="card mb-24" style={{ borderLeft: '4px solid #3b82f6' }}>
        <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 16, marginBottom: 8 }}>
          ¿Cómo funciona la calibración de biomasa?
        </h3>
        <div style={{ color: 'var(--color-text-secondary)', fontSize: 13, lineHeight: 1.7 }}>
          <p><strong>1.</strong> Selecciona un potrero de la lista.</p>
          <p><strong>2.</strong> Haz clic en el mapa para marcar al menos <strong>{MIN_POINTS} puntos</strong> donde realizaste cortes de pasto.</p>
          <p><strong>3.</strong> Para cada punto ingresa el <strong>área de corte (m²)</strong> y el <strong>peso obtenido (kg de forraje verde)</strong>.</p>
          <p><strong>4.</strong> El sistema calcula el NDVI en cada punto usando la imagen satelital de la calibración óptima.</p>
          <p><strong>5.</strong> Con los pares (NDVI, biomasa) se ajusta una regresión lineal: <strong>biomasa = a × NDVI</strong>.</p>
          <p><strong>6.</strong> El modelo calibrado se usa para estimar biomasa real en cada potrero.</p>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 16, alignItems: 'start' }}>
        {/* Parcel list sidebar */}
        <div className="card" style={{ padding: 0 }}>
          <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--color-border)' }}>
            <h3 style={{ fontSize: 14, margin: 0 }}>Potreros</h3>
          </div>
          <div style={{ maxHeight: 500, overflowY: 'auto' }}>
            {parcels.map(p => (
              <button
                key={p.parcelId}
                onClick={() => { setActiveParcelId(p.parcelId); setPlacingPoints(false) }}
                style={{
                  display: 'block',
                  width: '100%',
                  padding: '10px 16px',
                  border: 'none',
                  borderBottom: '1px solid var(--color-border)',
                  background: activeParcelId === p.parcelId ? 'var(--color-primary-bg)' : 'transparent',
                  cursor: 'pointer',
                  textAlign: 'left',
                  fontSize: 13,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <strong style={{ color: 'white' }}>{p.parcelName}</strong>
                  {p.calibrated
                    ? <span style={{ color: '#4ade80', fontSize: 11 }}>✓ Calibrado</span>
                    : <span style={{ color: '#f59e0b', fontSize: 11 }}>Pendiente</span>}
                </div>
                {p.model && (
                  <div style={{ fontSize: 11, color: 'var(--color-text-muted)', marginTop: 4 }}>
                    {p.model.formula
                      ? <span style={{ color: '#fcd34d' }}>{p.model.formula}</span>
                      : `a=${p.model.coefficientA?.toFixed(2)}`}
                    {(() => {
                      const pRSquared = extractRSquared(p.model)
                      return pRSquared != null ? <> · R²={pRSquared.toFixed(4)}</> : null
                    })()}
                  </div>
                )}
              </button>
            ))}
          </div>
        </div>

        {/* Main content area */}
        <div>
          {!activeParcelId ? (
            <div className="card" style={{ textAlign: 'center', padding: 40, color: 'var(--color-text-secondary)' }}>
              <p style={{ fontSize: 16 }}>← Selecciona un potrero para iniciar la calibración</p>
            </div>
          ) : (
            <div>
              {/* Map */}
              <div className="card mb-24" style={{ padding: 0, overflow: 'hidden' }}>
                <div style={{ padding: '10px 16px', borderBottom: '1px solid var(--color-border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <h3 style={{ fontSize: 14, margin: 0 }}>
                      {activeParcel?.parcelName} — Puntos de Muestreo
                    </h3>
                    <span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>
                      {currentPoints.length} puntos marcados (mínimo {MIN_POINTS})
                    </span>
                  </div>
                  <div className="flex gap-8">
                    <button
                      className={`action-btn ${placingPoints ? 'action-btn--danger' : 'action-btn--primary'}`}
                      onClick={() => setPlacingPoints(!placingPoints)}
                      style={{ fontSize: 12, padding: '6px 12px' }}
                    >
                      {placingPoints ? 'Dejar de marcar' : 'Marcar puntos en mapa'}
                    </button>
                    {currentPoints.length > 0 && (
                      <button
                        className="action-btn"
                        onClick={() => setParcelPoints(prev => ({ ...prev, [activeParcelId]: [] }))}
                        style={{ fontSize: 12, padding: '6px 12px' }}
                      >
                        Limpiar puntos
                      </button>
                    )}
                  </div>
                </div>
                <div style={{ height: 350 }}>
                  <ParcelMap
                    key={activeParcelId}
                    parcel={activeParcel}
                    points={currentPoints}
                    onMapClick={handleMapClick}
                    placingPoints={placingPoints}
                  />
                </div>
              </div>

              {/* Data table */}
              {currentPoints.length > 0 && (
                <div className="card mb-24">
                  <div className="card-header">
                    <h3>Datos de Campo{activeParcel?.calibrated && <span style={{ fontSize: 12, color: '#f59e0b', marginLeft: 10, fontWeight: 400 }}>Edita los valores y recalcula para ajustar la función</span>}</h3>
                  </div>
                  <div style={{ overflowX: 'auto' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                      <thead>
                        <tr style={{ borderBottom: '2px solid var(--color-border)' }}>
                          <th style={thStyle}>#</th>
                          <th style={thStyle}>Lat</th>
                          <th style={thStyle}>Lng</th>
                          <th style={thStyle}>Área de corte (m²)</th>
                          <th style={thStyle}>Peso forraje verde (kg)</th>
                          <th style={thStyle}>Biomasa (kg/ha)</th>
                          {activeParcel?.calibrated && activeParcel?.points?.length > 0 && (
                            <th style={{ ...thStyle, color: '#4ade80' }}>NDVI</th>
                          )}
                          <th style={thStyle}></th>
                        </tr>
                      </thead>
                      <tbody>
                        {currentPoints.map((pt, idx) => {
                          const biomass = pt.cutAreaM2 && pt.greenWeightKg && Number(pt.cutAreaM2) > 0
                            ? ((Number(pt.greenWeightKg) / Number(pt.cutAreaM2)) * 10000).toFixed(1)
                            : '—'
                          const ndvi = activeParcel?.points?.[idx]?.ndviAtPoint
                          return (
                            <tr key={idx} style={{ borderBottom: '1px solid var(--color-border)' }}>
                              <td style={tdStyle}><strong>{idx + 1}</strong></td>
                              <td style={tdStyle}>{pt.lat.toFixed(6)}</td>
                              <td style={tdStyle}>{pt.lng.toFixed(6)}</td>
                              <td style={tdStyle}>
                                <input
                                  type="number"
                                  min="0.01"
                                  step="0.01"
                                  value={pt.cutAreaM2}
                                  onChange={e => updatePoint(idx, 'cutAreaM2', e.target.value)}
                                  placeholder="ej: 0.25"
                                  style={inputStyle}
                                />
                              </td>
                              <td style={tdStyle}>
                                <input
                                  type="number"
                                  min="0.01"
                                  step="0.01"
                                  value={pt.greenWeightKg}
                                  onChange={e => updatePoint(idx, 'greenWeightKg', e.target.value)}
                                  placeholder="ej: 0.5"
                                  style={inputStyle}
                                />
                              </td>
                              <td style={{ ...tdStyle, fontWeight: 600, color: '#4ade80' }}>{biomass}</td>
                              {activeParcel?.calibrated && activeParcel?.points?.length > 0 && (
                                <td style={{ ...tdStyle, fontWeight: 600, color: '#4ade80' }}>
                                  {ndvi != null ? ndvi.toFixed(4) : '—'}
                                </td>
                              )}
                              <td style={tdStyle}>
                                <button
                                  onClick={() => removePoint(idx)}
                                  style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer', fontSize: 16 }}
                                  title="Eliminar punto"
                                >✕</button>
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>

                  <div style={{ marginTop: 16, display: 'flex', gap: 12, alignItems: 'center' }}>
                    <button
                      className="action-btn action-btn--primary"
                      onClick={handleCalibrate}
                      disabled={calibrating || currentPoints.length < MIN_POINTS}
                    >
                      {calibrating
                        ? <><span className="spinner" /> Calibrando...</>
                        : activeParcel?.calibrated
                          ? `Recalcular Función (${currentPoints.length} puntos)`
                          : `Calibrar Biomasa (${currentPoints.length} puntos)`}
                    </button>
                    {currentPoints.length < MIN_POINTS && (
                      <span style={{ fontSize: 12, color: '#f59e0b' }}>
                        Faltan {MIN_POINTS - currentPoints.length} puntos
                      </span>
                    )}
                  </div>
                </div>
              )}

              {/* Regression results */}
              {activeParcel?.calibrated && activeParcel.model && (
                <div className="card mb-24" style={{ borderLeft: '4px solid #4ade80' }}>
                  <div className="card-header">
                    <h3>Modelo de Regresión — {activeParcel.parcelName}</h3>
                  </div>

                  {/* Formula display */}
                  <div style={{
                    background: 'rgba(245, 158, 11, 0.08)',
                    border: '1.5px solid rgba(245, 158, 11, 0.35)',
                    borderRadius: 10,
                    padding: '14px 20px',
                    textAlign: 'center',
                    marginBottom: 20,
                  }}>
                    <div style={{ fontSize: 10, letterSpacing: 1.5, color: '#f59e0b', textTransform: 'uppercase', marginBottom: 8, fontWeight: 600 }}>
                      Ecuación del Modelo
                    </div>
                    <div style={{ fontSize: 22, fontWeight: 700, color: '#fcd34d', letterSpacing: 0.5 }}>
                      {activeParcel.model.formula
                        ? activeParcel.model.formula
                        : `Biomasa = ${activeParcel.model.coefficientA?.toFixed(2)} × NDVI`}
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'center', gap: 24, marginTop: 10 }}>
                      <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--color-text-secondary)' }}>
                        R² = {activeRSquared != null ? activeRSquared.toFixed(4) : '—'}
                      </span>
                      <span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>
                        {activeParcel.model.sampleCount} muestras · Biomasa en kg/ha
                      </span>
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 24, alignItems: 'start' }}>
                    <div>
                      <div className="ndvi-parcel-stats" style={{ marginBottom: 16 }}>
                        <div>
                          <span className="label">R² (ajuste)</span>
                          <span className="value">
                            {activeRSquared != null ? activeRSquared.toFixed(4) : '—'}
                          </span>
                        </div>
                        <div>
                          <span className="label">Muestras</span>
                          <span className="value">{activeParcel.model.sampleCount}</span>
                        </div>
                        <div>
                          <span className="label">Escena</span>
                          <span className="value" style={{ fontSize: 11 }}>{activeParcel.model.sceneId || '—'}</span>
                        </div>
                      </div>
                    </div>
                    <BiomassChart points={activeParcel.points || []} model={activeParcel.model} />
                  </div>


                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Navigation */}
      <div className="flex gap-12" style={{ marginTop: 24 }}>
        <button
          className="action-btn"
          onClick={() => navigate(`/terrains/${terrainId}/ndvi/calibration-alert`)}
        >
          ← Calibrar Umbral de Alerta
        </button>
        {status?.allCalibrated && (
          <button
            className="action-btn action-btn--primary"
            onClick={() => navigate(`/terrains/${terrainId}/ndvi`)}
          >
            Ir al Dashboard NDVI →
          </button>
        )}
      </div>
    </div>
  )
}

/** Map component that shows one parcel boundary + markers */
function ParcelMap({ parcel, points, onMapClick, placingPoints }) {
  if (!parcel) return null

  return (
    <MapContainer
      center={[4.6, -74.1]}
      zoom={15}
      style={{ height: '100%', width: '100%', cursor: placingPoints ? 'crosshair' : '' }}
    >
      <TileLayer
        url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        attribution="Tiles &copy; Esri" maxZoom={19}
      />
      <TileLayer
        url="https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"
        maxZoom={19}
      />
      <MapClickHandler onMapClick={onMapClick} enabled={placingPoints} />
      <ParcelGeoJsonLayer parcelId={parcel.parcelId} />
      {points.map((pt, idx) => (
        <Marker key={idx} position={[pt.lat, pt.lng]} icon={numberIcon(idx + 1)}>
          <Popup>
            <strong>Punto {idx + 1}</strong><br />
            {pt.cutAreaM2 ? `Área: ${pt.cutAreaM2} m²` : 'Sin área'}<br />
            {pt.greenWeightKg ? `Peso: ${pt.greenWeightKg} kg` : 'Sin peso'}
          </Popup>
        </Marker>
      ))}
    </MapContainer>
  )
}

/** Loads parcel GeoJSON via API and renders it */
function ParcelGeoJsonLayer({ parcelId }) {
  const map = useMap()
  const [geoJson, setGeoJson] = useState(null)

  useEffect(() => {
    if (!parcelId) return
    // Clear previous parcel immediately so the new one does not depend on a full page reload.
    setGeoJson(null)
    const token = localStorage.getItem('token')
    fetch(`/api/parcels/${encodeURIComponent(parcelId)}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
      .then(r => { if (!r.ok) throw new Error(); return r.json() })
      .then(data => {
        if (data.geoJson) setGeoJson(data.geoJson)
      })
      .catch(() => {})
  }, [parcelId])

  useEffect(() => {
    if (!geoJson || !map) return
    try {
      const geo = typeof geoJson === 'string' ? JSON.parse(geoJson) : geoJson
      const layer = L.geoJSON(geo)
      map.fitBounds(layer.getBounds(), { padding: [40, 40] })
    } catch { /* ignore */ }
  }, [geoJson, map])

  if (!geoJson) return null

  const geo = typeof geoJson === 'string' ? JSON.parse(geoJson) : geoJson
  return (
    <GeoJSON
      data={geo}
      style={{ color: '#3b82f6', weight: 2, fillColor: '#3b82f680', fillOpacity: 0.15 }}
    />
  )
}

const thStyle = { textAlign: 'left', padding: '8px 10px', fontSize: 12, color: 'var(--color-text-muted)', fontWeight: 600 }
const tdStyle = { padding: '8px 10px', verticalAlign: 'middle' }
const inputStyle = {
  width: '100%',
  maxWidth: 120,
  padding: '6px 8px',
  border: '1px solid var(--color-border)',
  borderRadius: 6,
  background: 'var(--color-bg-secondary)',
  color: 'var(--color-text)',
  fontSize: 13,
}
