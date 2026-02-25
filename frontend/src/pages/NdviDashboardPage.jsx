import { useState, useEffect, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  LineChart, Line, AreaChart, Area, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, ReferenceLine
} from 'recharts'
import { ndviApi, terrainApi } from '../services/api'
import toast from 'react-hot-toast'

const PARCEL_COLORS = ['#4ade80', '#3b82f6', '#f59e0b', '#ef4444', '#a855f7', '#ec4899', '#14b8a6', '#f97316']

const HEALTH_COLORS = {
  'EXCELENTE': '#4ade80',
  'BUENO': '#84cc16',
  'REGULAR': '#f59e0b',
  'CRÍTICO': '#ef4444'
}

const STATUS_LABELS = {
  'DISPONIBLE': { label: 'Disponible', icon: '🌿', color: '#4ade80' },
  'EN_USO': { label: 'En uso', icon: '🐄', color: '#f59e0b' },
  'EN_DESCANSO': { label: 'En descanso', icon: '💤', color: '#3b82f6' }
}

const SEVERITY_COLORS = {
  'CRITICAL': '#ef4444',
  'HIGH': '#f97316',
  'MEDIUM': '#f59e0b',
  'LOW': '#3b82f6'
}

export default function NdviDashboardPage() {
  const { terrainId } = useParams()
  const navigate = useNavigate()

  const [dashboard, setDashboard] = useState(null)
  const [comparison, setComparison] = useState([])
  const [recommendations, setRecommendations] = useState([])
  const [history, setHistory] = useState([])
  const [loading, setLoading] = useState(true)
  const [analyzing, setAnalyzing] = useState(false)
  const [activeTab, setActiveTab] = useState('overview')
  const [selectedParcel, setSelectedParcel] = useState(null)
  const [parcelTimeline, setParcelTimeline] = useState([])

  useEffect(() => { loadAll() }, [terrainId])

  async function loadAll() {
    setLoading(true)
    try {
      const [dash, comp, recs, hist] = await Promise.all([
        ndviApi.getDashboard(terrainId),
        ndviApi.getComparison(terrainId),
        ndviApi.getRecommendations(terrainId),
        ndviApi.getRotationHistory(terrainId)
      ])
      setDashboard(dash)
      setComparison(comp)
      setRecommendations(recs)
      setHistory(hist)
    } catch (err) {
      toast.error('Error cargando dashboard NDVI')
    } finally {
      setLoading(false)
    }
  }

  async function handleAnalyze() {
    setAnalyzing(true)
    try {
      const result = await ndviApi.analyze(terrainId)

      // Show detailed message
      if (result.message) {
        toast.success(result.message, { duration: 6000 })
      }
      if (result.planetNote) {
        toast(result.planetNote, { icon: '🛰️', duration: 4000 })
      }
      if (result.sentinelNote) {
        toast(result.sentinelNote, { icon: '🌍', duration: 4000 })
      }
      if (result.error) {
        toast.error(result.error)
      }

      // Always reload dashboard after analysis
      await loadAll()
    } catch (err) {
      toast.error('Error ejecutando análisis: ' + (err.response?.data?.error || err.message))
    } finally {
      setAnalyzing(false)
    }
  }

  async function handleSelectParcel(parcelId) {
    setSelectedParcel(parcelId)
    try {
      const timeline = await ndviApi.getParcelTimeline(parcelId)
      setParcelTimeline(timeline)
    } catch {
      setParcelTimeline([])
    }
  }

  async function handleAcknowledgeAlert(alertId) {
    try {
      await ndviApi.acknowledgeAlert(alertId)
      await loadAll()
      toast.success('Alerta reconocida')
    } catch {
      toast.error('Error')
    }
  }

  // Prepare timeline data grouped by date
  const timelineByDate = useMemo(() => {
    if (!dashboard?.timeline) return []
    const grouped = {}
    dashboard.timeline.forEach(p => {
      if (!grouped[p.date]) grouped[p.date] = { date: p.date }
      grouped[p.date][p.parcelName || `P${p.parcelId}`] = p.meanNdvi
      grouped[p.date][`bio_${p.parcelName || p.parcelId}`] = p.biomassKgPerHa
    })
    return Object.values(grouped).sort((a, b) => a.date.localeCompare(b.date))
  }, [dashboard?.timeline])

  const parcelNames = useMemo(() => {
    if (!dashboard?.parcels) return []
    return dashboard.parcels.map(p => p.parcelName)
  }, [dashboard?.parcels])

  if (loading) {
    return (
      <div className="empty-state">
        <div className="spinner" />
        <p style={{ marginTop: 16 }}>Cargando analíticas NDVI...</p>
      </div>
    )
  }

  const hasData = dashboard?.timeline?.length > 0

  return (
    <div>
      {/* Header */}
      <div className="page-header">
        <div className="breadcrumb">
          <a href="/farms">Fincas</a>
          <span>›</span>
          <span>{dashboard?.farmName}</span>
          <span>›</span>
          <a href={`/terrains/${terrainId}/parcels`}>{dashboard?.terrainName}</a>
          <span>›</span>
          <span>NDVI Analytics</span>
        </div>
        <div className="flex justify-between items-center">
          <div>
            <h2>🛰️ Analíticas NDVI</h2>
            <p>{dashboard?.terrainName} — {dashboard?.terrainAreaHa?.toFixed(2)} ha · {dashboard?.parcels?.length} parcelas</p>
          </div>
          <button
            className="btn btn-primary"
            onClick={handleAnalyze}
            disabled={analyzing}
          >
            {analyzing ? <><span className="spinner" /> Analizando...</> : '🛰️ Ejecutar Análisis'}
          </button>
        </div>
      </div>

      {!hasData ? (
        <div className="empty-state card">
          <div className="icon">🛰️</div>
          <h3>Sin datos NDVI</h3>
          <p>Ejecuta un análisis para generar datos satelitales de este terreno.</p>
          <button className="btn btn-primary" onClick={handleAnalyze} disabled={analyzing}>
            {analyzing ? 'Analizando...' : '🛰️ Ejecutar Primer Análisis'}
          </button>
        </div>
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
              <div className="ndvi-summary-sub">kg MS/ha promedio</div>
            </div>
            <div className="ndvi-summary-card">
              <div className="ndvi-summary-label">Alertas Activas</div>
              <div className="ndvi-summary-value" style={{ color: dashboard?.activeAlerts > 0 ? '#ef4444' : '#4ade80' }}>
                {dashboard?.activeAlerts || 0}
              </div>
              <div className="ndvi-summary-sub">{dashboard?.activeAlerts > 0 ? 'Requieren atención' : 'Todo en orden'}</div>
            </div>
          </div>

          {/* Tabs */}
          <div className="ndvi-tabs">
            {[
              { key: 'overview', label: '📊 Evolución NDVI' },
              { key: 'comparison', label: '📋 Comparación' },
              { key: 'recommendations', label: '🎯 Recomendaciones' },
              { key: 'alerts', label: `⚠️ Alertas (${dashboard?.activeAlerts || 0})` },
              { key: 'history', label: '📜 Historial' },
              { key: 'biomass', label: '🌱 Biomasa' }
            ].map(tab => (
              <button
                key={tab.key}
                className={`ndvi-tab ${activeTab === tab.key ? 'active' : ''}`}
                onClick={() => setActiveTab(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Tab Content */}
          <div className="ndvi-tab-content">
            {activeTab === 'overview' && (
              <div>
                <div className="card mb-24">
                  <div className="card-header">
                    <h3>Evolución NDVI por Parcela</h3>
                    <span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>
                      Último análisis: {dashboard?.lastAnalysisDate}
                    </span>
                  </div>
                  <ResponsiveContainer width="100%" height={380}>
                    <LineChart data={timelineByDate}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#2a3d2a" />
                      <XAxis dataKey="date" stroke="#5c7a5c" tick={{ fontSize: 11 }}
                        tickFormatter={d => d?.substring(5)} />
                      <YAxis domain={[0, 1]} stroke="#5c7a5c" tick={{ fontSize: 11 }} />
                      <Tooltip
                        contentStyle={{ background: '#172117', border: '1px solid #2a3d2a', borderRadius: 8 }}
                        labelStyle={{ color: '#e8f5e8' }}
                      />
                      <Legend />
                      <ReferenceLine y={0.3} stroke="#ef4444" strokeDasharray="5 5" label={{ value: 'Umbral alerta', fill: '#ef4444', fontSize: 11 }} />
                      <ReferenceLine y={0.6} stroke="#4ade80" strokeDasharray="5 5" label={{ value: 'Óptimo', fill: '#4ade80', fontSize: 11 }} />
                      {parcelNames.map((name, i) => (
                        <Line
                          key={name}
                          type="monotone"
                          dataKey={name}
                          stroke={PARCEL_COLORS[i % PARCEL_COLORS.length]}
                          strokeWidth={2}
                          dot={false}
                          connectNulls
                        />
                      ))}
                    </LineChart>
                  </ResponsiveContainer>
                </div>

                {/* Per-parcel mini cards */}
                <div className="ndvi-parcel-grid">
                  {dashboard?.parcels?.map((p, i) => (
                    <div
                      key={p.parcelId}
                      className="ndvi-parcel-card"
                      style={{ borderLeftColor: PARCEL_COLORS[i % PARCEL_COLORS.length] }}
                      onClick={() => handleSelectParcel(p.parcelId)}
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
                            {p.trendSlope > 0.001 ? '📈 Mejorando' : p.trendSlope < -0.001 ? '📉 Bajando' : '➡️ Estable'}
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
                <div className="card-header">
                  <h3>Ranking de Parcelas por NDVI</h3>
                </div>
                <div className="table-container">
                  <table>
                    <thead>
                      <tr>
                        <th>#</th>
                        <th>Parcela</th>
                        <th>Área (ha)</th>
                        <th>Estado</th>
                        <th>NDVI Actual</th>
                        <th>NDVI Promedio</th>
                        <th>Biomasa (kg/ha)</th>
                        <th>Salud</th>
                        <th>Recomendación</th>
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
                    ✅ Todas las parcelas están en su estado óptimo. No hay cambios recomendados.
                  </div>
                ) : (
                  <div className="ndvi-recs-list">
                    {recommendations.map(rec => (
                      <div key={rec.parcelId} className="ndvi-rec-card" data-urgency={rec.urgency}>
                        <div className="flex justify-between items-center mb-16">
                          <h4>{rec.parcelName}</h4>
                          <span className={`ndvi-urgency ${rec.urgency?.toLowerCase()}`}>
                            {rec.urgency}
                          </span>
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
                        <p style={{ fontSize: 13, color: 'var(--color-text-secondary)', marginTop: 12 }}>
                          {rec.reason}
                        </p>
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

            {activeTab === 'alerts' && (
              <div>
                {(!dashboard?.alerts || dashboard.alerts.length === 0) ? (
                  <div className="card" style={{ textAlign: 'center', padding: 40, color: 'var(--color-text-muted)' }}>
                    ✅ Sin alertas. Todas las parcelas están dentro de los umbrales normales.
                  </div>
                ) : (
                  <div className="ndvi-alerts-list">
                    {dashboard.alerts.map(alert => (
                      <div
                        key={alert.id}
                        className={`ndvi-alert-card ${alert.acknowledged ? 'acknowledged' : ''}`}
                        style={{ borderLeftColor: SEVERITY_COLORS[alert.severity] }}
                      >
                        <div className="flex justify-between items-center">
                          <div>
                            <span style={{ fontSize: 12, color: SEVERITY_COLORS[alert.severity], fontWeight: 700, textTransform: 'uppercase' }}>
                              {alert.severity}
                            </span>
                            <span style={{ fontSize: 12, color: 'var(--color-text-muted)', marginLeft: 8 }}>
                              {alert.parcelName}
                            </span>
                          </div>
                          {!alert.acknowledged && (
                            <button className="btn btn-secondary btn-sm" onClick={() => handleAcknowledgeAlert(alert.id)}>
                              ✓ Reconocer
                            </button>
                          )}
                        </div>
                        <p style={{ margin: '8px 0', fontSize: 14 }}>{alert.message}</p>
                        <div style={{ fontSize: 11, color: 'var(--color-text-muted)' }}>
                          NDVI: {alert.currentValue?.toFixed(3)} · Umbral: {alert.threshold} · {alert.createdAt?.substring(0, 16)}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {activeTab === 'history' && (
              <div className="card">
                <div className="card-header">
                  <h3>Historial de Rotación</h3>
                </div>
                {history.length === 0 ? (
                  <p style={{ color: 'var(--color-text-muted)', textAlign: 'center', padding: 24 }}>
                    Sin historial de rotación registrado.
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
                    <h3>🌱 Materia Vegetal por Parcela</h3>
                    <span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>
                      kg de materia seca / hectárea
                    </span>
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
                      <span className="guide-desc">Buen estado. Apto para pastoreo rotacional.</span>
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

          {/* Navigation */}
          <div className="flex gap-12 mt-24">
            <button className="btn btn-secondary" onClick={() => navigate(`/terrains/${terrainId}/parcels`)}>
              ← Parcelas
            </button>
            <button className="btn btn-secondary" onClick={() => navigate(`/terrains/${terrainId}/rotation`)}>
              🔄 Rotación
            </button>
          </div>
        </>
      )}
    </div>
  )
}

function getHealthColor(ndvi) {
  if (ndvi == null) return '#5c7a5c'
  if (ndvi >= 0.60) return '#4ade80'
  if (ndvi >= 0.40) return '#84cc16'
  if (ndvi >= 0.25) return '#f59e0b'
  return '#ef4444'
}
