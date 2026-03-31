import { useState, useEffect, useRef, useMemo } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { MapContainer, TileLayer, GeoJSON, Marker, Popup, useMap, useMapEvents } from 'react-leaflet'
import L from 'leaflet'
import toast from 'react-hot-toast'
import Spinner from '../components/Spinner'
import { useBiomassCalibration, useCalibration } from '../hooks'

delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
})

const MIN_POINTS = 7

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

/** Mini scatter chart drawn on a canvas */
function RegressionChart({ points, model }) {
  const canvasRef = useRef(null)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || !points.length) return
    const ctx = canvas.getContext('2d')
    const W = canvas.width
    const H = canvas.height
    const pad = 40

    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = '#1e293b'
    ctx.fillRect(0, 0, W, H)

    const valid = points.filter(p => p.ndviAtPoint != null && p.biomassKgPerHa != null)
    if (!valid.length) return

    const xs = valid.map(p => p.ndviAtPoint)
    const ys = valid.map(p => p.biomassKgPerHa)
    const xMin = Math.min(...xs) - 0.05
    const xMax = Math.max(...xs) + 0.05
    const yMin = Math.min(...ys) * 0.9
    const yMax = Math.max(...ys) * 1.1

    const toX = v => pad + (v - xMin) / (xMax - xMin) * (W - 2 * pad)
    const toY = v => H - pad - (v - yMin) / (yMax - yMin) * (H - 2 * pad)

    // Axes
    ctx.strokeStyle = '#475569'
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(pad, pad)
    ctx.lineTo(pad, H - pad)
    ctx.lineTo(W - pad, H - pad)
    ctx.stroke()

    // Labels
    ctx.fillStyle = '#94a3b8'
    ctx.font = '11px sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText('NDVI', W / 2, H - 5)
    ctx.save()
    ctx.translate(12, H / 2)
    ctx.rotate(-Math.PI / 2)
    ctx.fillText('Biomasa (kg/ha)', 0, 0)
    ctx.restore()

    // Tick labels
    for (let i = 0; i <= 4; i++) {
      const v = xMin + (xMax - xMin) * i / 4
      ctx.fillStyle = '#94a3b8'
      ctx.fillText(v.toFixed(2), toX(v), H - pad + 14)
    }
    ctx.textAlign = 'right'
    for (let i = 0; i <= 4; i++) {
      const v = yMin + (yMax - yMin) * i / 4
      ctx.fillStyle = '#94a3b8'
      ctx.fillText(Math.round(v).toString(), pad - 5, toY(v) + 4)
    }

    // Points
    valid.forEach(p => {
      ctx.beginPath()
      ctx.arc(toX(p.ndviAtPoint), toY(p.biomassKgPerHa), 5, 0, Math.PI * 2)
      ctx.fillStyle = '#4ade80'
      ctx.fill()
      ctx.strokeStyle = '#fff'
      ctx.lineWidth = 1.5
      ctx.stroke()
    })

    // Regression line
    if (model) {
      const y1 = model.coefficientA * xMin + model.coefficientB
      const y2 = model.coefficientA * xMax + model.coefficientB
      ctx.strokeStyle = '#f59e0b'
      ctx.lineWidth = 2
      ctx.setLineDash([6, 3])
      ctx.beginPath()
      ctx.moveTo(toX(xMin), toY(y1))
      ctx.lineTo(toX(xMax), toY(y2))
      ctx.stroke()
      ctx.setLineDash([])

      // R² label
      ctx.fillStyle = '#f59e0b'
      ctx.font = 'bold 12px sans-serif'
      ctx.textAlign = 'left'
      ctx.fillText(`R² = ${model.rSquared?.toFixed(4)}`, pad + 8, pad + 16)
      ctx.fillText(`y = ${model.coefficientA?.toFixed(2)}·x + ${model.coefficientB?.toFixed(2)}`, pad + 8, pad + 32)
    }
  }, [points, model])

  return <canvas ref={canvasRef} width={420} height={280} style={{ borderRadius: 8, width: '100%', maxWidth: 420 }} />
}

export default function CalibrationBiomassPage() {
  const { terrainId } = useParams()
  const navigate = useNavigate()
  const { status, loading, calibrating, calibrateParcel } = useBiomassCalibration(terrainId)
  const { status: optimStatus, loading: optimLoading } = useCalibration(terrainId, 'OPTIM')

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

  if (loading || optimLoading) return <Spinner page label="Cargando calibración de biomasa..." />

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
          <p><strong>5.</strong> Con los pares (NDVI, biomasa) se ajusta una regresión lineal: <strong>biomasa = a × NDVI + b</strong>.</p>
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
                  <strong>{p.parcelName}</strong>
                  {p.calibrated
                    ? <span style={{ color: '#4ade80', fontSize: 11 }}>✓ Calibrado</span>
                    : <span style={{ color: '#f59e0b', fontSize: 11 }}>Pendiente</span>}
                </div>
                {p.model && (
                  <div style={{ fontSize: 11, color: 'var(--color-text-muted)', marginTop: 4 }}>
                    R² = {p.model.rSquared?.toFixed(4)} · {p.model.sampleCount} muestras
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
                    <h3>Datos de Campo</h3>
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
                          <th style={thStyle}></th>
                        </tr>
                      </thead>
                      <tbody>
                        {currentPoints.map((pt, idx) => {
                          const biomass = pt.cutAreaM2 && pt.greenWeightKg && Number(pt.cutAreaM2) > 0
                            ? ((Number(pt.greenWeightKg) / Number(pt.cutAreaM2)) * 10000).toFixed(1)
                            : '—'
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
                                  disabled={activeParcel?.calibrated && !placingPoints}
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
                                  disabled={activeParcel?.calibrated && !placingPoints}
                                />
                              </td>
                              <td style={{ ...tdStyle, fontWeight: 600, color: '#4ade80' }}>{biomass}</td>
                              <td style={tdStyle}>
                                {!(activeParcel?.calibrated && !placingPoints) && (
                                  <button
                                    onClick={() => removePoint(idx)}
                                    style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer', fontSize: 16 }}
                                    title="Eliminar punto"
                                  >✕</button>
                                )}
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>

                  {/* Calibrate button */}
                  {!activeParcel?.calibrated && (
                    <div style={{ marginTop: 16, display: 'flex', gap: 12, alignItems: 'center' }}>
                      <button
                        className="action-btn action-btn--primary"
                        onClick={handleCalibrate}
                        disabled={calibrating || currentPoints.length < MIN_POINTS}
                      >
                        {calibrating
                          ? <><span className="spinner" /> Calibrando...</>
                          : `Calibrar Biomasa (${currentPoints.length} puntos)`}
                      </button>
                      {currentPoints.length < MIN_POINTS && (
                        <span style={{ fontSize: 12, color: '#f59e0b' }}>
                          Faltan {MIN_POINTS - currentPoints.length} puntos
                        </span>
                      )}
                    </div>
                  )}
                </div>
              )}

              {/* Regression results */}
              {activeParcel?.calibrated && activeParcel.model && (
                <div className="card mb-24" style={{ borderLeft: '4px solid #4ade80' }}>
                  <div className="card-header">
                    <h3>Modelo de Regresión — {activeParcel.parcelName}</h3>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 24, alignItems: 'start' }}>
                    <div>
                      <div className="ndvi-parcel-stats" style={{ marginBottom: 16 }}>
                        <div>
                          <span className="label">Ecuación</span>
                          <span className="value" style={{ fontSize: 15 }}>
                            biomasa = {activeParcel.model.coefficientA?.toFixed(2)} × NDVI + {activeParcel.model.coefficientB?.toFixed(2)}
                          </span>
                        </div>
                        <div>
                          <span className="label">R² (ajuste)</span>
                          <span className="value" style={{
                            color: activeParcel.model.rSquared >= 0.7 ? '#4ade80'
                              : activeParcel.model.rSquared >= 0.5 ? '#f59e0b' : '#ef4444'
                          }}>
                            {activeParcel.model.rSquared?.toFixed(4)}
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
                      {activeParcel.model.rSquared >= 0.7 ? (
                        <p style={{ color: '#4ade80', fontSize: 13 }}>
                          ✓ Buen ajuste. El modelo representa bien la relación NDVI↔biomasa para este potrero.
                        </p>
                      ) : activeParcel.model.rSquared >= 0.5 ? (
                        <p style={{ color: '#f59e0b', fontSize: 13 }}>
                          Ajuste moderado. Considera agregar más puntos de muestreo para mejorar la calibración.
                        </p>
                      ) : (
                        <p style={{ color: '#ef4444', fontSize: 13 }}>
                          Ajuste bajo. Revisa los datos de campo o agrega más puntos para obtener una calibración confiable.
                        </p>
                      )}

                      {/* Allow recalibration */}
                      <button
                        className="action-btn"
                        onClick={() => {
                          setRecalibratingIds(prev => new Set([...prev, activeParcelId]))
                          setParcelPoints(prev => ({ ...prev, [activeParcelId]: [] }))
                          setPlacingPoints(true)
                        }}
                        style={{ marginTop: 12, fontSize: 12 }}
                      >
                        Recalibrar este potrero
                      </button>
                    </div>
                    <RegressionChart points={activeParcel.points || []} model={activeParcel.model} />
                  </div>

                  {/* Points with NDVI */}
                  {activeParcel.points && activeParcel.points.length > 0 && (
                    <div style={{ marginTop: 16 }}>
                      <h4 style={{ fontSize: 13, marginBottom: 8 }}>Puntos con NDVI calculado</h4>
                      <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                          <thead>
                            <tr style={{ borderBottom: '2px solid var(--color-border)' }}>
                              <th style={thStyle}>#</th>
                              <th style={thStyle}>Área (m²)</th>
                              <th style={thStyle}>Peso (kg)</th>
                              <th style={thStyle}>Biomasa (kg/ha)</th>
                              <th style={thStyle}>NDVI</th>
                            </tr>
                          </thead>
                          <tbody>
                            {activeParcel.points.map(pt => (
                              <tr key={pt.pointIndex} style={{ borderBottom: '1px solid var(--color-border)' }}>
                                <td style={tdStyle}>{pt.pointIndex}</td>
                                <td style={tdStyle}>{pt.cutAreaM2}</td>
                                <td style={tdStyle}>{pt.greenWeightKg}</td>
                                <td style={tdStyle}>{pt.biomassKgPerHa?.toFixed(1)}</td>
                                <td style={{ ...tdStyle, color: '#4ade80', fontWeight: 600 }}>
                                  {pt.ndviAtPoint?.toFixed(4) ?? '—'}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}
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
          onClick={() => navigate(`/terrains/${terrainId}/ndvi/calibration-optim`)}
        >
          ← Calibración NDVI Óptimo
        </button>
        <button
          className="action-btn"
          onClick={() => navigate(`/terrains/${terrainId}/parcels`)}
        >
          Potreros
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
        attribution='&copy; Google'
        url="https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}"
        maxZoom={20}
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
