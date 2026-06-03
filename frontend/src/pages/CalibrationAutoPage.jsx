import { useState, useEffect, useRef, useCallback } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine, Legend } from 'recharts'
import toast from 'react-hot-toast'
import Spinner from '../components/Spinner'
import { autoCalibrationApi } from '../services/api'

const POLL_INTERVAL_MS = 15000

export default function CalibrationAutoPage() {
  const { terrainId } = useParams()
  const navigate = useNavigate()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [starting, setStarting] = useState(false)
  const [months, setMonths] = useState(12)
  const pollRef = useRef(null)

  const fetchStatus = useCallback(async () => {
    try {
      const res = await autoCalibrationApi.status(terrainId)
      setData(res)
      return res
    } catch (err) {
      console.warn('Error consultando status:', err)
      return null
    }
  }, [terrainId])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      const res = await fetchStatus()
      if (!cancelled) setLoading(false)
      if (!cancelled && res?.status === 'RUNNING') schedulePoll()
    })()
    return () => {
      cancelled = true
      if (pollRef.current) clearInterval(pollRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [terrainId])

  function schedulePoll() {
    if (pollRef.current) clearInterval(pollRef.current)
    pollRef.current = setInterval(async () => {
      const res = await fetchStatus()
      if (res?.status !== 'RUNNING' && pollRef.current) {
        clearInterval(pollRef.current)
        pollRef.current = null
      }
    }, POLL_INTERVAL_MS)
  }

  async function handleStart() {
    setStarting(true)
    try {
      await autoCalibrationApi.start(terrainId, months)
      toast.success(`Calibración iniciada sobre los últimos ${months} meses. Puede tardar varias horas.`)
      await fetchStatus()
      schedulePoll()
    } catch (err) {
      toast.error('No se pudo iniciar: ' + (err.response?.data?.error || err.message))
    } finally {
      setStarting(false)
    }
  }

  if (loading) return <Spinner page label="Consultando calibración automática..." />

  const job = data?.hasJob ? data : null
  const running = job?.status === 'RUNNING'
  const completed = job?.status === 'COMPLETED'
  const failed = job?.status === 'FAILED'
  const progress = job ? Math.min(100, Math.round((job.weeksCompleted / job.weeksTotal) * 100)) : 0

  return (
    <div className="page-container">
      <div className="page-header">
        <div className="breadcrumb">
          <Link to="/farms">Fincas</Link>
          <span>›</span>
          <Link to={`/terrains/${terrainId}/parcels`}>Terreno</Link>
          <span>›</span>
          <span>Calibración Automática (12 meses)</span>
        </div>
        <h2>Calibración Automática NDVI — 12 meses</h2>
        <p>
          Procesa una imagen Sentinel por semana del último año y deriva los umbrales óptimo (p75) y de
          alerta (p25) automáticamente. Es una calibración a nivel de terreno: aplica a todos los potreros.
        </p>
      </div>

      {/* No job yet */}
      {!job && (
        <div className="card mb-24" style={{ borderLeft: '4px solid #3b82f6' }}>
          <div className="card-header">
            <h3>Iniciar calibración</h3>
          </div>
          <p style={{ color: 'var(--color-text-secondary)', marginBottom: 16 }}>
            El proceso descarga ~52 imágenes Sentinel-2 (una por semana) y calcula NDVI para todos los
            potreros. Se ejecuta en segundo plano — puedes cerrar esta página y la calibración sigue corriendo.
          </p>
          <p style={{ color: 'var(--color-text-muted)', fontSize: 13, marginBottom: 16 }}>
            Tiempo estimado: 1–4 horas dependiendo de la disponibilidad de imágenes y tu conexión con Copernicus.
          </p>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12, flexWrap: 'wrap' }}>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label>Rango a analizar</label>
              <select value={months} onChange={(e) => setMonths(Number(e.target.value))} disabled={starting}>
                <option value={6}>Últimos 6 meses</option>
                <option value={12}>Últimos 12 meses</option>
                <option value={18}>Últimos 18 meses</option>
                <option value={24}>Últimos 24 meses</option>
                <option value={36}>Últimos 36 meses</option>
              </select>
            </div>
            <button
              className="action-btn action-btn--primary"
              onClick={handleStart}
              disabled={starting}
            >
              {starting ? <><span className="spinner" /> Iniciando...</> : `Iniciar calibración (${months} meses)`}
            </button>
          </div>
        </div>
      )}

      {/* Running */}
      {running && (
        <div className="card mb-24" style={{ borderLeft: '4px solid #f59e0b' }}>
          <div className="card-header">
            <h3>Calibración en progreso</h3>
          </div>
          <div style={{ marginBottom: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6, fontSize: 13 }}>
              <span>Semana {job.weeksCompleted} / {job.weeksTotal}</span>
              <span>{progress}%</span>
            </div>
            <div style={{ height: 8, background: 'var(--color-bg-secondary)', borderRadius: 4, overflow: 'hidden' }}>
              <div style={{
                height: '100%',
                width: `${progress}%`,
                background: 'linear-gradient(90deg, #4ade80, #22c55e)',
                transition: 'width 0.4s ease',
              }} />
            </div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12, fontSize: 13 }}>
            <Stat label="Escenas procesadas" value={job.scenesProcessed} />
            <Stat label="Semana actual" value={job.currentWeekStart || '—'} />
            <Stat label="Inicio" value={fmtDate(job.startedAt)} />
            <Stat label="Rango" value={`${job.rangeStart} → ${job.rangeEnd}`} />
          </div>
          <p style={{ color: 'var(--color-text-muted)', fontSize: 12, marginTop: 16 }}>
            Esta página se actualiza sola cada {POLL_INTERVAL_MS / 1000}s. Puedes salir y volver más tarde.
          </p>
        </div>
      )}

      {/* Completed */}
      {completed && (
        <div className="card mb-24" style={{ borderLeft: '4px solid #4ade80' }}>
          <div className="card-header">
            <h3>Calibración completada</h3>
          </div>
          <p style={{ color: 'var(--color-text-secondary)', marginBottom: 16 }}>
            Estos umbrales se usarán para alertas y rotación en todos los potreros del terreno.
          </p>
          <div className="ndvi-parcel-grid" style={{ marginBottom: 24 }}>
            <ThresholdCard label="Umbral Óptimo (p75)" value={job.thresholdHigh} color="#4ade80" hint="Por encima de este valor, condiciones óptimas para pastoreo." />
            <ThresholdCard label="Umbral Alerta (p25)" value={job.thresholdLow} color="#ef4444" hint="Por debajo, no se debe pastorear (riesgo de sobrepastoreo)." />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12, fontSize: 13 }}>
            <Stat label="Escenas procesadas" value={job.scenesProcessed} />
            <Stat label="Registros NDVI" value={data.recordCount ?? '—'} />
            <Stat label="Rango" value={`${job.rangeStart} → ${job.rangeEnd}`} />
            <Stat label="Finalizada" value={fmtDate(job.finishedAt)} />
          </div>
        </div>
      )}

      {/* Failed */}
      {failed && (
        <div className="card mb-24" style={{ borderLeft: '4px solid #ef4444' }}>
          <div className="card-header">
            <h3>Calibración falló</h3>
          </div>
          <p style={{ color: '#ef4444', marginBottom: 16 }}>
            {job.errorMessage || 'Error desconocido.'}
          </p>
          <button
            className="action-btn action-btn--primary"
            onClick={handleStart}
            disabled={starting}
          >
            Reintentar
          </button>
        </div>
      )}

      {/* Timeline chart */}
      {data?.timeline && data.timeline.length > 0 && (
        <div className="card mb-24">
          <div className="card-header">
            <h3>Línea de tiempo NDVI</h3>
          </div>
          <div style={{ width: '100%', height: 300 }}>
            <ResponsiveContainer>
              <LineChart data={data.timeline}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis domain={[0, 1]} tick={{ fontSize: 11 }} />
                <Tooltip />
                <Legend />
                <Line type="monotone" dataKey="meanNdvi" stroke="#3b82f6" strokeWidth={2} dot={{ r: 3 }} name="NDVI promedio" />
                {job?.thresholdHigh != null && (
                  <ReferenceLine y={job.thresholdHigh} stroke="#4ade80" strokeDasharray="6 3" label={{ value: 'Óptimo', fill: '#4ade80', fontSize: 11 }} />
                )}
                {job?.thresholdLow != null && (
                  <ReferenceLine y={job.thresholdLow} stroke="#ef4444" strokeDasharray="6 3" label={{ value: 'Alerta', fill: '#ef4444', fontSize: 11 }} />
                )}
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Actions */}
      <div className="flex gap-12" style={{ alignItems: 'flex-end', flexWrap: 'wrap' }}>
        {(completed || failed) && (
          <>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label>Rango a analizar</label>
              <select value={months} onChange={(e) => setMonths(Number(e.target.value))} disabled={starting}>
                <option value={6}>Últimos 6 meses</option>
                <option value={12}>Últimos 12 meses</option>
                <option value={18}>Últimos 18 meses</option>
                <option value={24}>Últimos 24 meses</option>
                <option value={36}>Últimos 36 meses</option>
              </select>
            </div>
            <button
              className="action-btn action-btn--primary"
              onClick={handleStart}
              disabled={starting}
            >
              {starting ? 'Iniciando...' : `Recalibrar (${months} meses)`}
            </button>
          </>
        )}
        <button
          className="action-btn"
          onClick={() => navigate(`/terrains/${terrainId}/parcels`)}
        >
          ← Volver a Potreros
        </button>
      </div>
    </div>
  )
}

function Stat({ label, value }) {
  return (
    <div>
      <div style={{ color: 'var(--color-text-muted)', fontSize: 11, textTransform: 'uppercase', marginBottom: 4 }}>{label}</div>
      <div style={{ fontWeight: 600 }}>{value ?? '—'}</div>
    </div>
  )
}

function ThresholdCard({ label, value, color, hint }) {
  return (
    <div className="ndvi-parcel-card" style={{ borderLeftColor: color }}>
      <h4 style={{ marginBottom: 8 }}>{label}</h4>
      <div style={{ fontSize: 28, fontWeight: 700, color, marginBottom: 6 }}>
        {value != null ? value.toFixed(3) : '—'}
      </div>
      <div style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>{hint}</div>
    </div>
  )
}

function fmtDate(value) {
  if (!value) return '—'
  try {
    return new Date(value).toLocaleString('es-CO', { dateStyle: 'short', timeStyle: 'short' })
  } catch {
    return value
  }
}
