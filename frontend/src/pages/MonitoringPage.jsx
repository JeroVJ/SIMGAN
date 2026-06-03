import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine, Legend } from 'recharts'
import toast from 'react-hot-toast'
import Spinner from '../components/Spinner'
import { monitoringApi } from '../services/api'

export default function MonitoringPage() {
  const { parcelId } = useParams()
  const navigate = useNavigate()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [toggling, setToggling] = useState(false)
  const [fetching, setFetching] = useState(false)
  // When a fetch finds no usable scene, holds the next week offset to try.
  const [prevWeekOffset, setPrevWeekOffset] = useState(null)

  const reload = useCallback(async () => {
    try {
      const res = await monitoringApi.get(parcelId)
      setData(res)
    } catch (err) {
      toast.error('Error cargando monitoreo: ' + (err.response?.data?.error || err.message))
    }
  }, [parcelId])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      await reload()
      if (!cancelled) setLoading(false)
    })()
    return () => { cancelled = true }
  }, [reload])

  async function handleToggle() {
    setToggling(true)
    try {
      await monitoringApi.toggle(parcelId, !data.monitoringEnabled)
      await reload()
      toast.success(!data.monitoringEnabled ? 'Monitoreo activado' : 'Monitoreo desactivado')
    } catch (err) {
      toast.error('Error: ' + (err.response?.data?.error || err.message))
    } finally {
      setToggling(false)
    }
  }

  const MAX_WEEKS_BACK = 8

  async function handleFetchNow(weeksBack = 0) {
    setFetching(true)
    try {
      const result = await monitoringApi.fetchNow(parcelId, weeksBack)
      const records = result?.recordsProcessed ?? 0

      if (records > 0) {
        toast.success(result.message || 'Imagen procesada')
        setPrevWeekOffset(null)
        await reload()
      } else {
        // No usable scene that week — offer to look one week further back.
        const weekLabel = weeksBack === 0 ? 'esta semana' : `${weeksBack} semana(s) atrás`
        toast(`No hubo imagen Sentinel utilizable ${weekLabel} (nubes o sin paso del satélite).`, { icon: '🛰️' })
        setPrevWeekOffset(weeksBack + 1 <= MAX_WEEKS_BACK ? weeksBack + 1 : null)
      }
    } catch (err) {
      toast.error('Error: ' + (err.response?.data?.error || err.message))
    } finally {
      setFetching(false)
    }
  }

  if (loading) return <Spinner page label="Cargando monitoreo..." />
  if (!data) return null

  const { thresholdHigh, thresholdLow, latestNdvi, latestDate, timeline, monitoringEnabled, terrainId, terrainName } = data
  const calibrated = thresholdHigh != null || thresholdLow != null
  const status = computeStatus(latestNdvi, thresholdLow, thresholdHigh)

  return (
    <div className="page-container">
      <div className="page-header">
        <div className="breadcrumb">
          <Link to="/farms">Fincas</Link>
          <span>›</span>
          <Link to={`/terrains/${terrainId}/parcels`}>{terrainName || 'Terreno'}</Link>
          <span>›</span>
          <span>Monitoreo</span>
        </div>
        <h2>Monitoreo NDVI — {data.parcelName}</h2>
        <p>NDVI actual del potrero comparado con los umbrales óptimo y de alerta.</p>
      </div>

      {!calibrated && (
        <div className="card mb-24" style={{ borderLeft: '4px solid #f59e0b' }}>
          <h3>El terreno aún no tiene calibración</h3>
          <p style={{ color: 'var(--color-text-secondary)', margin: '12px 0' }}>
            Para usar monitoreo necesitas calibrar primero. Recomendamos la calibración automática
            (12 meses) que deriva los umbrales reales del terreno.
          </p>
          <button
            className="action-btn action-btn--primary"
            onClick={() => navigate(`/terrains/${terrainId}/ndvi/calibration-auto`)}
          >
            Ir a calibración automática
          </button>
        </div>
      )}

      {/* Current state */}
      <div className="card mb-24" style={{ borderLeft: `4px solid ${status.color}` }}>
        <div className="flex justify-between items-center" style={{ marginBottom: 16 }}>
          <h3>Estado actual</h3>
          <span style={{
            background: status.color, color: '#fff', padding: '4px 10px',
            borderRadius: 12, fontSize: 12, fontWeight: 600
          }}>{status.label}</span>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16 }}>
          <Stat label="NDVI más reciente" value={latestNdvi != null ? latestNdvi.toFixed(3) : '—'} />
          <Stat label="Fecha" value={latestDate || '—'} />
          <Stat label="Umbral óptimo" value={thresholdHigh != null ? thresholdHigh.toFixed(3) : '—'} />
          <Stat label="Umbral alerta" value={thresholdLow != null ? thresholdLow.toFixed(3) : '—'} />
        </div>
      </div>

      {/* Controls */}
      <div className="card mb-24">
        <div className="card-header">
          <h3>Configuración</h3>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, padding: '12px 0' }}>
            <div>
              <div style={{ fontWeight: 600 }}>Monitoreo automático semanal</div>
              <div style={{ fontSize: 13, color: 'var(--color-text-muted)', marginTop: 4 }}>
                Cada lunes el sistema descarga la mejor imagen Sentinel de la última semana y la analiza.
              </div>
            </div>
            <button
              onClick={handleToggle}
              disabled={toggling}
              style={{
                width: 52, height: 28, borderRadius: 14, border: 'none', cursor: 'pointer',
                background: monitoringEnabled ? '#22c55e' : 'var(--color-bg-secondary)',
                position: 'relative', transition: 'background 0.2s',
              }}
            >
              <span style={{
                position: 'absolute', top: 2, left: monitoringEnabled ? 26 : 2,
                width: 24, height: 24, borderRadius: '50%', background: '#fff',
                transition: 'left 0.2s',
              }} />
            </button>
          </div>
          <div style={{ display: 'flex', gap: 12, alignItems: 'center', justifyContent: 'space-between' }}>
            <div>
              <div style={{ fontWeight: 600 }}>Pedir imagen ahora</div>
              <div style={{ fontSize: 13, color: 'var(--color-text-muted)', marginTop: 4 }}>
                Procesa una imagen de la semana actual sin esperar al próximo lunes.
              </div>
            </div>
            <button
              className="action-btn action-btn--primary"
              onClick={() => handleFetchNow(0)}
              disabled={fetching}
            >
              {fetching ? <><span className="spinner" /> Procesando...</> : 'Procesar ahora'}
            </button>
          </div>

          {prevWeekOffset != null && (
            <div style={{
              marginTop: 12, padding: '12px 14px', borderRadius: 'var(--radius-sm)',
              background: 'var(--color-bg)', border: '1px solid var(--color-border)',
              display: 'flex', gap: 12, alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap',
            }}>
              <div style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>
                No se encontró imagen en esa semana. ¿Buscar en la semana anterior?
              </div>
              <button
                className="action-btn action-btn--outline"
                onClick={() => handleFetchNow(prevWeekOffset)}
                disabled={fetching}
              >
                {fetching
                  ? <><span className="spinner" /> Buscando...</>
                  : `Mirar semana anterior (−${prevWeekOffset})`}
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Timeline */}
      {timeline && timeline.length > 0 && (
        <div className="card mb-24">
          <div className="card-header">
            <h3>Histórico NDVI (6 meses)</h3>
          </div>
          <div style={{ width: '100%', height: 300 }}>
            <ResponsiveContainer>
              <LineChart data={timeline}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis domain={[0, 1]} tick={{ fontSize: 11 }} />
                <Tooltip />
                <Legend />
                <Line type="monotone" dataKey="meanNdvi" stroke="#3b82f6" strokeWidth={2} dot={{ r: 3 }} name="NDVI" />
                {thresholdHigh != null && (
                  <ReferenceLine y={thresholdHigh} stroke="#4ade80" strokeDasharray="6 3" label={{ value: 'Óptimo', fill: '#4ade80', fontSize: 11 }} />
                )}
                {thresholdLow != null && (
                  <ReferenceLine y={thresholdLow} stroke="#ef4444" strokeDasharray="6 3" label={{ value: 'Alerta', fill: '#ef4444', fontSize: 11 }} />
                )}
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      <button
        className="action-btn"
        onClick={() => navigate(`/terrains/${terrainId}/parcels`)}
      >
        ← Volver a Potreros
      </button>
    </div>
  )
}

function Stat({ label, value }) {
  return (
    <div>
      <div style={{ color: 'var(--color-text-muted)', fontSize: 11, textTransform: 'uppercase', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 18, fontWeight: 600 }}>{value}</div>
    </div>
  )
}

function computeStatus(ndvi, low, high) {
  if (ndvi == null) return { label: 'Sin datos', color: '#94a3b8' }
  if (low != null && ndvi < low) return { label: 'No pastorear', color: '#ef4444' }
  if (high != null && ndvi >= high) return { label: 'Óptimo', color: '#22c55e' }
  return { label: 'Normal', color: '#3b82f6' }
}
