import { useState, useEffect, useMemo } from 'react'
import { useParams } from 'react-router-dom'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer, ReferenceLine,
} from 'recharts'
import { TrendingUp } from 'lucide-react'

import Spinner from '../components/Spinner'
import EmptyState from '../components/EmptyState'
import TerrainTabs from '../components/TerrainTabs'
import { autoCalibrationApi, ndviApi, terrainApi } from '../services/api'

const DEFAULT_ALERT_NDVI = 0.3
const DEFAULT_OPTIM_NDVI = 0.6

const RANGES = [
  { key: '3m',  label: '3 meses',  months: 3 },
  { key: '6m',  label: '6 meses',  months: 6 },
  { key: '12m', label: '12 meses', months: 12 },
  { key: 'all', label: 'Todo',     months: null },
]

function isoMonthsAgo(months) {
  const d = new Date()
  d.setMonth(d.getMonth() - months)
  return d.toISOString().slice(0, 10)
}

/**
 * Dedicated NDVI timeline page for a terrain.
 *
 * Shows the terrain-level mean NDVI over time with the calibrated alert (p25)
 * and optimal (p75) thresholds overlaid. Data comes from the 12-month
 * auto-calibration (NdviTerrainRecord, terrain-level), which is where the real
 * NDVI series lives; it falls back to the per-parcel analysis timeline when no
 * calibration job exists. Separate from the auto-calibration page itself.
 */
export default function NdviTimelinePage() {
  const { terrainId } = useParams()

  const [terrain, setTerrain]       = useState(null)
  const [timeline, setTimeline]     = useState([])   // [{ date, ndvi }]
  const [thresholds, setThresholds] = useState({ alert: DEFAULT_ALERT_NDVI, optim: DEFAULT_OPTIM_NDVI })
  const [source, setSource]         = useState(null) // 'calibration' | 'analysis' | null
  const [loading, setLoading]       = useState(true)
  const [range, setRange]           = useState('12m')

  // Terrain meta for the breadcrumb / tabs header.
  useEffect(() => {
    let active = true
    terrainApi.getById(terrainId)
      .then(t => { if (active) setTerrain(t) })
      .catch(() => {})
    return () => { active = false }
  }, [terrainId])

  // Load the NDVI series. Prefer the 12-month auto-calibration (terrain-level
  // records + calibrated thresholds); fall back to per-parcel analysis records.
  useEffect(() => {
    let active = true
    setLoading(true)

    async function load() {
      let points = []
      let alert = DEFAULT_ALERT_NDVI
      let optim = DEFAULT_OPTIM_NDVI
      let src = null

      try {
        const auto = await autoCalibrationApi.status(terrainId)
        if (auto?.thresholdLow != null) alert = auto.thresholdLow
        if (auto?.thresholdHigh != null) optim = auto.thresholdHigh
        if (Array.isArray(auto?.timeline) && auto.timeline.length) {
          points = auto.timeline
          src = 'calibration'
        }
      } catch { /* no calibration job yet */ }

      if (points.length === 0) {
        try {
          const tl = await ndviApi.getTimeline(terrainId)
          if (Array.isArray(tl) && tl.length) {
            points = tl
            src = 'analysis'
          }
        } catch { /* no analysis records either */ }
      }

      if (!active) return
      setTimeline(
        points
          .filter(p => p.meanNdvi != null)
          .map(p => ({ date: p.date, ndvi: Number(Number(p.meanNdvi).toFixed(3)) }))
      )
      setThresholds({ alert, optim })
      setSource(src)
      setLoading(false)
    }

    load()
    return () => { active = false }
  }, [terrainId])

  const chartData = useMemo(() => {
    const months = RANGES.find(r => r.key === range)?.months
    if (!months) return timeline
    const cutoff = isoMonthsAgo(months)
    return timeline.filter(p => p.date >= cutoff)
  }, [timeline, range])

  const stats = useMemo(() => {
    if (!chartData.length) return null
    const vals = chartData.map(d => d.ndvi)
    const latest = vals[vals.length - 1]
    return {
      latest,
      min: Math.min(...vals),
      max: Math.max(...vals),
      avg: vals.reduce((a, b) => a + b, 0) / vals.length,
      count: vals.length,
      belowAlert: latest < thresholds.alert,
    }
  }, [chartData, thresholds])

  return (
    <div className="page-container">
      <TerrainTabs
        terrainId={terrainId}
        farmId={terrain?.farmId}
        farmName={terrain?.farmName}
        terrainName={terrain?.name}
        areaHa={terrain?.areaHectares}
      />

      <div className="card mb-24">
        <div
          className="card-header"
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 12 }}
        >
          <div>
            <h3 style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <TrendingUp size={18} strokeWidth={2} /> Línea de tiempo NDVI — Terreno
            </h3>
            <p style={{ fontSize: 12, color: 'var(--color-text-muted)', margin: '4px 0 0' }}>
              NDVI promedio del terreno con los umbrales de alerta y óptimo.
              {source === 'calibration' && ' Fuente: calibración automática de 12 meses.'}
              {source === 'analysis' && ' Fuente: análisis NDVI del terreno.'}
            </p>
          </div>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {RANGES.map(r => (
              <button
                key={r.key}
                className={`comp-btn comp-btn--sm ${range === r.key ? 'comp-btn--primary' : 'comp-btn--outline'}`}
                onClick={() => setRange(r.key)}
              >
                {r.label}
              </button>
            ))}
          </div>
        </div>

        {loading ? (
          <Spinner label="Cargando línea de tiempo..." />
        ) : chartData.length === 0 ? (
          <EmptyState
            icon="🛰"
            title="Sin datos NDVI"
            description={
              timeline.length === 0
                ? 'Este terreno aún no tiene serie NDVI. Corre la calibración automática de 12 meses o un análisis NDVI para generar datos.'
                : 'No hay capturas NDVI en el rango seleccionado. Prueba el rango "Todo".'
            }
          />
        ) : (
          <>
            {stats && (
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
                <StatPill label="NDVI actual" value={stats.latest.toFixed(3)} color={stats.belowAlert ? '#ef4444' : '#4ade80'} />
                <StatPill label="Promedio" value={stats.avg.toFixed(3)} />
                <StatPill label="Mínimo" value={stats.min.toFixed(3)} />
                <StatPill label="Máximo" value={stats.max.toFixed(3)} />
                <StatPill label="Capturas" value={stats.count} />
              </div>
            )}

            <ResponsiveContainer width="100%" height={400}>
              <LineChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#2a3d2a" />
                <XAxis dataKey="date" stroke="#5c7a5c" tick={{ fontSize: 11 }} tickFormatter={d => d?.substring(5)} />
                <YAxis domain={[0, 1]} stroke="#5c7a5c" tick={{ fontSize: 11 }} />
                <Tooltip
                  contentStyle={{ background: '#172117', border: '1px solid #2a3d2a', borderRadius: 8 }}
                  labelStyle={{ color: '#e8f5e8' }}
                />
                <Legend />
                <ReferenceLine
                  y={thresholds.alert}
                  stroke="#ef4444"
                  strokeDasharray="5 5"
                  label={{ value: `Umbral alerta (${thresholds.alert.toFixed(2)})`, fill: '#ef4444', fontSize: 11, position: 'insideTopRight' }}
                />
                <ReferenceLine
                  y={thresholds.optim}
                  stroke="#4ade80"
                  strokeDasharray="5 5"
                  label={{ value: `Óptimo (${thresholds.optim.toFixed(2)})`, fill: '#4ade80', fontSize: 11, position: 'insideBottomRight' }}
                />
                <Line type="monotone" dataKey="ndvi" name="NDVI terreno" stroke="#3b82f6" strokeWidth={2.5} dot={{ r: 3 }} connectNulls />
              </LineChart>
            </ResponsiveContainer>
          </>
        )}
      </div>
    </div>
  )
}

function StatPill({ label, value, color }) {
  return (
    <div style={{ padding: '10px 16px', background: 'var(--color-surface-2)', border: '1px solid var(--color-border)', borderRadius: 10, minWidth: 90 }}>
      <div style={{ fontSize: 11, color: 'var(--color-text-muted)' }}>{label}</div>
      <div style={{ fontSize: 18, fontWeight: 700, color: color || 'var(--color-text)' }}>{value}</div>
    </div>
  )
}
