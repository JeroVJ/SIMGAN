import { useState, useEffect, useMemo } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import Spinner from '../components/Spinner'
import OperationProgress from '../components/OperationProgress'
import EmptyState from '../components/EmptyState'
import {
  LineChart, Line, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, ReferenceLine,
} from 'recharts'
import { useNdvi } from '../hooks'
import { useCalibration, useBiomassCalibration } from '../hooks'
import { getHealthColor } from '../utils/ndvi'

const PARCEL_COLORS = ['#4ade80', '#3b82f6', '#f59e0b', '#ef4444', '#a855f7', '#ec4899', '#14b8a6', '#f97316']
const DEFAULT_ALERT_NDVI_THRESHOLD = 0.3

const HEALTH_COLORS = {
  EXCELENTE: '#4ade80', BUENO: '#84cc16', CRÍTICO: '#ef4444',
}

const STATUS_LABELS = {
  DISPONIBLE:  { label: 'Disponible', color: '#4ade80' },
  EN_USO:      { label: 'En uso',   color: '#f59e0b' },
  EN_DESCANSO: { label: 'En descanso', color: '#3b82f6' },
}

export default function NdviDashboardPage() {
  const { terrainId } = useParams()
  const navigate = useNavigate()
  const {
    dashboard, comparison, recommendations, history,
    loading, analyzing, scheduling,
    timelineByDate, parcelNames,
    analyze, configureSchedule, selectParcel,
  } = useNdvi(terrainId)

  const { status: calOptim, loading: calOptimLoading } = useCalibration(terrainId, 'OPTIM')
  const { status: calAlert, loading: calAlertLoading } = useCalibration(terrainId, 'ALERT')
  const { status: biomassStatus, loading: biomassLoading } = useBiomassCalibration(terrainId)

  const [activeTab, setActiveTab] = useState('overview')
  const today = new Date().toISOString().slice(0, 10)
  const [analysisStartDate, setAnalysisStartDate] = useState(
    new Date(Date.now() - 180 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
  )
  const [analysisEndDate, setAnalysisEndDate] = useState(today)
  const [biomassMethod, setBiomassMethod] = useState('DEFAULT')
  const [scheduleDays, setScheduleDays] = useState('')

  // Redirect to optim calibration if not yet calibrated
  useEffect(() => {
    if (!calOptimLoading && calOptim && !calOptim.calibrated) {
      navigate(`/terrains/${terrainId}/ndvi/calibration-optim`, { replace: true })
    }
  }, [calOptimLoading, calOptim, terrainId, navigate])

  // Redirect to alert calibration if optim done but alert not
  useEffect(() => {
    if (!calOptimLoading && calOptim?.calibrated && !calAlertLoading && calAlert && !calAlert.calibrated) {
      navigate(`/terrains/${terrainId}/ndvi/calibration-alert`, { replace: true })
    }
  }, [calOptimLoading, calOptim, calAlertLoading, calAlert, terrainId, navigate])

  // Redirect to biomass calibration if NDVI is ready but biomass is not calibrated in all parcels
  useEffect(() => {
    if (
      !calOptimLoading &&
      !calAlertLoading &&
      !biomassLoading &&
      calOptim?.calibrated &&
      calAlert?.calibrated &&
      biomassStatus &&
      !biomassStatus.allCalibrated
    ) {
      navigate(`/terrains/${terrainId}/ndvi/calibration-biomass`, { replace: true })
    }
  }, [
    calOptimLoading,
    calAlertLoading,
    biomassLoading,
    calOptim,
    calAlert,
    biomassStatus,
    terrainId,
    navigate,
  ])

  // Compute calibrated reference values for chart lines
  const optimNdvi = useMemo(() => {
    if (!calOptim?.calibrations?.length) return 0.6
    const vals = calOptim.calibrations.map(c => c.referenceNdvi).filter(Boolean)
    return vals.length ? vals.reduce((a, b) => a + b, 0) / vals.length : 0.6
  }, [calOptim])

  const alertNdvi = useMemo(() => {
    if (!calAlert?.calibrations?.length) return DEFAULT_ALERT_NDVI_THRESHOLD
    const vals = calAlert.calibrations.map(c => c.referenceNdvi).filter(Boolean)
    return vals.length ? vals.reduce((a, b) => a + b, 0) / vals.length : DEFAULT_ALERT_NDVI_THRESHOLD
  }, [calAlert])

  useEffect(() => {
    if (dashboard?.analysisScheduleDays) {
      setScheduleDays(String(dashboard.analysisScheduleDays))
    }
  }, [dashboard?.analysisScheduleDays])

  const hasData = dashboard?.timeline?.length > 0

  if (loading || calOptimLoading || calAlertLoading || biomassLoading) {
    return <Spinner page label="Cargando analíticas NDVI..." />
  }

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div className="breadcrumb">
          <Link to="/farms">Fincas</Link>
          <span>›</span>
          <span>{dashboard?.farmName}</span>
          <span>›</span>
          <Link to={`/terrains/${terrainId}/parcels`}>{dashboard?.terrainName}</Link>
          <span>›</span>
          <span>NDVI Analytics</span>
        </div>
        <div className="flex justify-between items-center">
          <div>
            <h2> Analíticas NDVI</h2>
            <p>{dashboard?.terrainName} — {dashboard?.terrainAreaHa?.toFixed(2)} ha · {dashboard?.parcels?.length} potreros</p>
          </div>
          <div className="ndvi-controls-stack">
            <div className="ndvi-analysis-controls">
              <div className="ndvi-date-field">
                <label htmlFor="analysisStartDate" className="ndvi-date-label">Fecha inicial</label>
                <input
                  className="ndvi-date-input"
                  id="analysisStartDate"
                  type="date"
                  value={analysisStartDate}
                  max={analysisEndDate || today}
                  onChange={(e) => setAnalysisStartDate(e.target.value)}
                  disabled={analyzing}
                />
              </div>
              <div className="ndvi-date-field">
                <label htmlFor="analysisEndDate" className="ndvi-date-label">Fecha final</label>
                <input
                  className="ndvi-date-input"
                  id="analysisEndDate"
                  type="date"
                  value={analysisEndDate}
                  min={analysisStartDate}
                  max={today}
                  onChange={(e) => setAnalysisEndDate(e.target.value)}
                  disabled={analyzing}
                />
              </div>
              <div className="ndvi-date-field">
                <label htmlFor="biomassMethod" className="ndvi-date-label">Biomasa</label>
                <select
                  className="ndvi-date-input"
                  id="biomassMethod"
                  value={biomassMethod}
                  onChange={(e) => setBiomassMethod(e.target.value)}
                  disabled={analyzing}
                >
                  <option value="DEFAULT">Por defecto (fórmula)</option>
                  <option value="SAMPLING">Por muestreo (calibración biomasa)</option>
                </select>
              </div>
              <button
                className="action-btn action-btn--primary"
                onClick={() => analyze(analysisStartDate, analysisEndDate, biomassMethod)}
                disabled={analyzing || !analysisStartDate || !analysisEndDate || analysisStartDate > analysisEndDate}
              >
                {analyzing ? <><span className="spinner" /> Analizando...</> : 'Ejecutar Análisis'}
              </button>
            </div>

            <div className="ndvi-schedule-controls">
              <span className="ndvi-schedule-label">Programación análisis</span>
              <input
                className="ndvi-date-input ndvi-schedule-input"
                id="analysisScheduleDays"
                type="number"
                min={1}
                max={365}
                value={scheduleDays}
                onChange={(e) => setScheduleDays(e.target.value)}
                disabled={scheduling || !hasData}
                placeholder="Días"
              />
              {dashboard?.nextAnalysisDueDate && (
                <span className="ndvi-schedule-next">Próximo: {dashboard.nextAnalysisDueDate}</span>
              )}
              <button
                className="action-btn action-btn--small"
                onClick={() => configureSchedule(Number(scheduleDays))}
                disabled={scheduling || !hasData || !scheduleDays || Number(scheduleDays) < 1}
              >
                {scheduling ? <><span className="spinner" /> Guardando...</> : 'Guardar'}
              </button>
            </div>
          </div>
        </div>
        <p className="ndvi-analysis-steps">1) Fecha inicial &amp; final · 2) Método biomasa · 3) <strong>Ejecutar Análisis</strong></p>
        <OperationProgress
          active={analyzing}
          title="Analisis NDVI en curso"
          expectedSeconds={180}
          hint="Cada imagen Sentinel tarda entre 2 y 4 minutos en descargarse y procesarse."
        />
      </div>

      {!hasData ? (
        <EmptyState
          icon="🛰️"
          title="Sin datos NDVI"
          description="Ejecuta un análisis para generar datos satelitales de este terreno."
        />
      ) : (
        <>
          {/* Summary Cards */}
          <div className="ndvi-summary-grid">
            <div className="ndvi-summary-card">
              <div className="ndvi-summary-label">NDVI Promedio</div>
              <div className="ndvi-summary-value" style={{ color: getHealthColor(dashboard?.avgNdvi) }}>
                {dashboard?.avgNdvi?.toFixed(3)}
              </div>
              <div className="ndvi-summary-sub">Terreno completo</div>
            </div>
            <div className="ndvi-summary-card">
              <div className="ndvi-summary-label">Biomasa Total</div>
              <div className="ndvi-summary-value" style={{ color: '#84cc16' }}>
                {(dashboard?.totalBiomassKg / 1000)?.toFixed(1)}
              </div>
              <div className="ndvi-summary-sub">toneladas MS estimadas</div>
            </div>
            <div className="ndvi-summary-card">
              <div className="ndvi-summary-label">Biomasa / ha</div>
              <div className="ndvi-summary-value" style={{ color: '#14b8a6' }}>
                {dashboard?.avgBiomassPerHa?.toFixed(0)}
              </div>
              <div className="ndvi-summary-sub">kg Biomasa/ha promedio</div>
            </div>
          </div>

          {/* Tabs */}
          <div className="ndvi-tabs">
            {[
              { key: 'overview',        label: ' Evolución NDVI' },
              { key: 'comparison',      label: ' Comparación' },
              { key: 'recommendations', label: ' Recomendaciones' },
              { key: 'history',         label: ' Historial' },
              { key: 'biomass',         label: ' Biomasa' },
            ].map(tab => (
              <button
                key={tab.key}
                className={`ndvi-tab${activeTab === tab.key ? ' ndvi-tab--active' : ''}`}
                onClick={() => setActiveTab(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </div>

          <div className="ndvi-tab-content">
            {activeTab === 'overview' && (
              <div>
                <div className="card mb-24">
                  <div className="card-header">
                    <h3>Evolución NDVI por Potrero</h3>
                    <span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>
                      Último análisis: {dashboard?.lastAnalysisDate}
                    </span>
                  </div>
                  <ResponsiveContainer width="100%" height={380}>
                    <LineChart data={timelineByDate}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#2a3d2a" />
                      <XAxis dataKey="date" stroke="#5c7a5c" tick={{ fontSize: 11 }} tickFormatter={d => d?.substring(5)} />
                      <YAxis domain={[0, 1]} stroke="#5c7a5c" tick={{ fontSize: 11 }} />
                      <Tooltip contentStyle={{ background: '#172117', border: '1px solid #2a3d2a', borderRadius: 8 }} labelStyle={{ color: '#e8f5e8' }} />
                      <Legend />
                      <ReferenceLine y={alertNdvi} stroke="#ef4444" strokeDasharray="5 5" label={{ value: `Umbral alerta (${alertNdvi.toFixed(2)})`, fill: '#ef4444', fontSize: 11 }} />
                      <ReferenceLine y={optimNdvi} stroke="#4ade80" strokeDasharray="5 5" label={{ value: `Óptimo (${optimNdvi.toFixed(2)})`, fill: '#4ade80', fontSize: 11 }} />
                      {parcelNames.map((name, i) => (
                        <Line key={name} type="monotone" dataKey={name}
                          stroke={PARCEL_COLORS[i % PARCEL_COLORS.length]}
                          strokeWidth={2} dot={false} connectNulls />
                      ))}
                    </LineChart>
                  </ResponsiveContainer>
                </div>

                <div className="ndvi-parcel-grid">
                  {dashboard?.parcels?.map((p, i) => (
                    <div
                      key={p.parcelId}
                      className="ndvi-parcel-card"
                      style={{ borderLeftColor: PARCEL_COLORS[i % PARCEL_COLORS.length] }}
                      onClick={() => selectParcel(p.parcelId)}
                    >
                      <div className="flex justify-between items-center">
                        <h4>{p.parcelName}</h4>
                        <span className={`status-badge ${p.status?.toLowerCase()?.replace('_', '-')}`}>
                          {STATUS_LABELS[p.status]?.icon} {STATUS_LABELS[p.status]?.label}
                        </span>
                      </div>
                      <div className="ndvi-parcel-stats">
                        <div>
                          <span className="label">NDVI</span>
                          <span className="value" style={{ color: getHealthColor(p.latestNdvi) }}>
                            {p.latestNdvi?.toFixed(3) || '—'}
                          </span>
                        </div>
                        <div>
                          <span className="label">Biomasa</span>
                          <span className="value">{p.latestBiomass?.toFixed(0) || '—'} kg/ha</span>
                        </div>
                        <div>
                          <span className="label">Salud</span>
                          <span className="value" style={{ color: p.healthColor }}>{p.healthStatus || '—'}</span>
                        </div>
                        <div>
                          <span className="label">Tendencia</span>
                          <span className="value">
                            {p.trendSlope > 0.001 ? 'Mejorando' : p.trendSlope < -0.001 ? ' Bajando' : ' Estable'}
                          </span>
                        </div>
                      </div>
                      <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginTop: 8 }}>
                        {p.recommendation}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {activeTab === 'comparison' && (
              <div className="card">
                <div className="card-header"><h3>Ranking de Potreros por NDVI</h3></div>
                <div className="table-container">
                  <table>
                    <thead>
                      <tr>
                        <th>#</th><th>Potrero</th><th>Área (ha)</th><th>Estado</th>
                        <th>NDVI Actual</th><th>NDVI Promedio</th><th>Biomasa (kg/ha)</th>
                        <th>Salud</th><th>Recomendación</th>
                      </tr>
                    </thead>
                    <tbody>
                      {comparison.map(c => (
                        <tr key={c.parcelId}>
                          <td style={{ fontWeight: 700, color: 'var(--color-primary)' }}>#{c.rank}</td>
                          <td style={{ fontWeight: 600 }}>{c.parcelName}</td>
                          <td>{c.areaHectares?.toFixed(2)}</td>
                          <td>
                            <span className={`status-badge ${c.status?.toLowerCase()?.replace('_', '-')}`}>
                              {STATUS_LABELS[c.status]?.icon} {STATUS_LABELS[c.status]?.label}
                            </span>
                          </td>
                          <td style={{ color: getHealthColor(c.latestNdvi), fontWeight: 600 }}>
                            {c.latestNdvi?.toFixed(3)}
                          </td>
                          <td>{c.avgNdvi?.toFixed(3)}</td>
                          <td>{c.biomassKgPerHa?.toFixed(0)}</td>
                          <td>
                            <span style={{ color: HEALTH_COLORS[c.healthStatus] || '#fff', fontWeight: 600, fontSize: 12 }}>
                              {c.healthStatus}
                            </span>
                          </td>
                          <td style={{ fontSize: 12, maxWidth: 200 }}>{c.recommendation}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {activeTab === 'recommendations' && (
              <div>
                {recommendations.length === 0 ? (
                  <div className="card" style={{ textAlign: 'center', padding: 40, color: 'var(--color-text-muted)' }}>
                    Todos los potreros están en su estado óptimo. No hay cambios recomendados.
                  </div>
                ) : (
                  <div className="ndvi-recs-list">
                    {recommendations.map(rec => (
                      <div key={rec.parcelId} className="ndvi-rec-card" data-urgency={rec.urgency}>
                        <div className="flex justify-between items-center mb-16">
                          <h4>{rec.parcelName}</h4>
                          <span className={`ndvi-urgency ${rec.urgency?.toLowerCase()}`}>{rec.urgency}</span>
                        </div>
                        <div className="ndvi-rec-flow">
                          <span className={`status-badge ${rec.currentStatus?.toLowerCase()?.replace('_', '-')}`}>
                            {STATUS_LABELS[rec.currentStatus]?.icon} {STATUS_LABELS[rec.currentStatus]?.label}
                          </span>
                          <span style={{ fontSize: 20 }}>→</span>
                          <span className={`status-badge ${rec.recommendedStatus?.toLowerCase()?.replace('_', '-')}`}>
                            {STATUS_LABELS[rec.recommendedStatus]?.icon} {STATUS_LABELS[rec.recommendedStatus]?.label}
                          </span>
                        </div>
                        <p style={{ fontSize: 13, color: 'var(--color-text-secondary)', marginTop: 12 }}>{rec.reason}</p>
                        <div className="ndvi-rec-metrics">
                          <span>NDVI: <strong style={{ color: getHealthColor(rec.currentNdvi) }}>{rec.currentNdvi?.toFixed(3)}</strong></span>
                          <span>Biomasa: <strong>{rec.biomass?.toFixed(0)} kg/ha</strong></span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {activeTab === 'history' && (
              <div className="card">
                <div className="card-header"><h3>Historial de Terrenos</h3></div>
                {history.length === 0 ? (
                  <p style={{ color: 'var(--color-text-muted)', textAlign: 'center', padding: 24 }}>
                    Sin historial de terrenos registrado.
                  </p>
                ) : (
                  <div className="ndvi-history-list">
                    {history.map(h => (
                      <div key={h.id} className="ndvi-history-item">
                        <div className="ndvi-history-dot" />
                        <div className="ndvi-history-content">
                          <div className="flex items-center gap-8">
                            <strong>{h.parcelName}</strong>
                            {h.previousStatus && (
                              <>
                                <span className={`status-badge ${h.previousStatus?.toLowerCase()?.replace('_', '-')}`} style={{ fontSize: 11, padding: '2px 8px' }}>
                                  {STATUS_LABELS[h.previousStatus]?.icon} {STATUS_LABELS[h.previousStatus]?.label}
                                </span>
                                <span>→</span>
                              </>
                            )}
                            <span className={`status-badge ${h.newStatus?.toLowerCase()?.replace('_', '-')}`} style={{ fontSize: 11, padding: '2px 8px' }}>
                              {STATUS_LABELS[h.newStatus]?.icon} {STATUS_LABELS[h.newStatus]?.label}
                            </span>
                          </div>
                          <div style={{ fontSize: 12, color: 'var(--color-text-muted)', marginTop: 4 }}>
                            {h.changedAt?.substring(0, 16)?.replace('T', ' ')}
                            {h.ndviAtChange != null && ` · NDVI: ${h.ndviAtChange.toFixed(3)}`}
                            {h.biomassAtChange != null && ` · Biomasa: ${h.biomassAtChange.toFixed(0)} kg/ha`}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {activeTab === 'biomass' && (
              <div>
                <div className="card mb-24">
                  <div className="card-header">
                    <h3> Biomasa por Potrero</h3>
                    <span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>kg de materia seca / hectárea</span>
                  </div>
                  <ResponsiveContainer width="100%" height={350}>
                    <BarChart data={comparison} layout="vertical">
                      <CartesianGrid strokeDasharray="3 3" stroke="#2a3d2a" />
                      <XAxis type="number" stroke="#5c7a5c" tick={{ fontSize: 11 }} />
                      <YAxis dataKey="parcelName" type="category" width={120} stroke="#5c7a5c" tick={{ fontSize: 12 }} />
                      <Tooltip
                        contentStyle={{ background: '#172117', border: '1px solid #2a3d2a', borderRadius: 8 }}
                        formatter={(val) => [`${val?.toFixed(0)} kg/ha`, 'Biomasa']}
                      />
                      <Bar dataKey="biomassKgPerHa" fill="#4ade80" radius={[0, 6, 6, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>

                <div className="card">
                  <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 20, marginBottom: 16 }}>
                    Interpretación de Biomasa
                  </h3>
                  <div className="ndvi-biomass-guide">
                    <div className="guide-row">
                      <span className="guide-bar" style={{ background: '#ef4444', width: '15%' }} />
                      <span className="guide-label">{'< 1,000 kg/ha'}</span>
                      <span className="guide-desc">Pasto degradado. Descanso urgente.</span>
                    </div>
                    <div className="guide-row">
                      <span className="guide-bar" style={{ background: '#f59e0b', width: '30%' }} />
                      <span className="guide-label">1,000 - 2,500 kg/ha</span>
                      <span className="guide-desc">Pasto bajo. Pastoreo ligero o descanso.</span>
                    </div>
                    <div className="guide-row">
                      <span className="guide-bar" style={{ background: '#84cc16', width: '55%' }} />
                      <span className="guide-label">2,500 - 5,000 kg/ha</span>
                      <span className="guide-desc">Buen estado. Apto para pastoreo.</span>
                    </div>
                    <div className="guide-row">
                      <span className="guide-bar" style={{ background: '#4ade80', width: '80%' }} />
                      <span className="guide-label">{'>5,000 kg/ha'}</span>
                      <span className="guide-desc">Excelente. Alta capacidad de carga.</span>
                    </div>
                  </div>
                  <p style={{ fontSize: 12, color: 'var(--color-text-muted)', marginTop: 16 }}>
                    Modelo: biomasa (kg MS/ha) = max(0, (NDVI - 0.1) × 12,000). Basado en literatura de pasturas
                    tropicales Brachiaria/Estrella en el trópico colombiano.
                  </p>
                </div>
              </div>
            )}
          </div>

          <div className="flex gap-12 mt-24">
            <button className="action-btn" onClick={() => navigate(`/terrains/${terrainId}/parcels`)}>← Potreros</button>
            <button className="action-btn" onClick={() => navigate(`/terrains/${terrainId}/rotation`)}> Terrenos</button>
          </div>
        </>
      )}
    </div>
  )
}
