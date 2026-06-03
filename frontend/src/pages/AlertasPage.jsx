import { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import toast from 'react-hot-toast'
import { Bell, ChevronDown, ChevronRight, AlertTriangle, RefreshCw, Droplets, TrendingDown, Waves, Sprout } from 'lucide-react'
import { farmApi, terrainApi, alertApi } from '../services/api'
import Spinner from '../components/Spinner'
import EmptyState from '../components/EmptyState'

const TYPE_CONFIG = {
  ESTADO_FORRAJE_BAJO_O_EN_UMBRAL: {
    label: 'Forraje bajo umbral',
    color: '#f59e0b',
    bg: 'rgba(245,158,11,0.10)',
    border: 'rgba(245,158,11,0.25)',
    Icon: TrendingDown,
  },
  POTRERO_ENCHARCADO: {
    label: 'Potrero encharcado',
    color: '#3b82f6',
    bg: 'rgba(59,130,246,0.10)',
    border: 'rgba(59,130,246,0.25)',
    Icon: Waves,
  },
  POTRERO_CON_ESTRES_HIDRICO: {
    label: 'Estrés hídrico',
    color: '#ef4444',
    bg: 'rgba(239,68,68,0.10)',
    border: 'rgba(239,68,68,0.25)',
    Icon: Droplets,
  },
  POTRERO_RECUPERADO: {
    label: 'Potrero recuperado',
    color: '#16a34a',
    bg: 'rgba(22,163,74,0.10)',
    border: 'rgba(22,163,74,0.25)',
    Icon: Sprout,
  },
}

const DEFAULT_TYPE = {
  label: 'Alerta',
  color: '#a1a1a1',
  bg: 'rgba(161,161,161,0.10)',
  border: 'rgba(161,161,161,0.25)',
  Icon: AlertTriangle,
}

function formatDate(dateStr) {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleString('es-CO', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

export default function AlertasPage() {
  const [data, setData] = useState([]) // [{ farm, terrains: [{ terrain, alerts }] }]
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [expandedFarms, setExpandedFarms] = useState({})
  const [expandedTerrains, setExpandedTerrains] = useState({})

  const loadData = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true)
    else setLoading(true)

    try {
      const farmList = await farmApi.getAll()

      const result = await Promise.all(
        farmList.map(async (farm) => {
          let terrains = []
          try { terrains = await terrainApi.getByFarm(farm.id) } catch { terrains = [] }

          const terrainsWithAlerts = await Promise.all(
            terrains.map(async (terrain) => {
              let alerts = []
              try { alerts = await alertApi.getByTerrain(terrain.id) } catch { alerts = [] }
              return { terrain, alerts }
            })
          )
          return { farm, terrains: terrainsWithAlerts }
        })
      )
      setData(result)

      // Auto-expand farms/terrains with alerts
      const eFarms = {}
      const eTerrains = {}
      for (const { farm, terrains } of result) {
        const hasAlerts = terrains.some(t => t.alerts.length > 0)
        if (hasAlerts) {
          eFarms[farm.id] = true
          for (const { terrain, alerts } of terrains) {
            if (alerts.length > 0) eTerrains[terrain.id] = true
          }
        }
      }
      setExpandedFarms(eFarms)
      setExpandedTerrains(eTerrains)
    } catch {
      toast.error('Error cargando alertas')
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  const totalAlerts = data.flatMap(d => d.terrains).flatMap(t => t.alerts).length
  const hasFarms    = data.length > 0

  if (loading) return <Spinner page label="Cargando alertas..." />

  return (
    <div className="page-container">
      {/* Header */}
      <div
        className="page-header"
        style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}
      >
        <div>
          <h2 style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Bell size={22} strokeWidth={1.75} style={{ color: 'var(--oh-green)' }} />
            Alertas
            {totalAlerts > 0 && (
              <span style={{
                background: '#ef4444', color: '#fff', fontSize: 11, fontWeight: 700,
                borderRadius: 20, padding: '2px 8px', lineHeight: 1.4,
              }}>
                {totalAlerts}
              </span>
            )}
          </h2>
          <p>Alertas de forraje y sensores de todos tus terrenos.</p>
        </div>
        <button
          onClick={() => loadData(true)}
          disabled={refreshing}
          style={{
            display: 'flex', alignItems: 'center', gap: 6,
            padding: '6px 12px',
            background: 'var(--oh-surface)', border: '1px solid var(--oh-border)',
            borderRadius: 'var(--oh-r-sm)', color: 'var(--oh-text-sec)',
            fontSize: 12, cursor: 'pointer', opacity: refreshing ? 0.6 : 1,
          }}
        >
          <RefreshCw size={13} style={{ animation: refreshing ? 'spin 1s linear infinite' : 'none' }} />
          Actualizar
        </button>
      </div>

      {!hasFarms ? (
        <EmptyState
          icon="🔔"
          title="Sin fincas registradas"
          description="Crea una finca con terrenos para ver sus alertas."
          action={<Link to="/farms/new" className="action-btn action-btn--primary">Crear Finca</Link>}
        />
      ) : totalAlerts === 0 ? (
        <EmptyState
          
          title="Sin alertas registradas"
          description="Los terrenos no han generado alertas todavía."
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {data.map(({ farm, terrains }) => {
            const farmAlerts = terrains.flatMap(t => t.alerts)
            if (farmAlerts.length === 0) return null

            const isFarmOpen = !!expandedFarms[farm.id]

            return (
              <div
                key={farm.id}
                style={{
                  background: 'var(--oh-surface)', border: '1px solid var(--oh-border)',
                  borderRadius: 'var(--oh-r-lg)', overflow: 'hidden',
                }}
              >
                {/* Farm header */}
                <button
                  onClick={() => setExpandedFarms(p => ({ ...p, [farm.id]: !p[farm.id] }))}
                  style={{
                    width: '100%', display: 'flex', alignItems: 'center', gap: 10,
                    padding: '14px 18px', background: 'transparent', border: 'none',
                    cursor: 'pointer', color: 'var(--oh-text)', textAlign: 'left',
                  }}
                >
                  {isFarmOpen
                    ? <ChevronDown size={16} style={{ color: 'var(--oh-green)', flexShrink: 0 }} />
                    : <ChevronRight size={16} style={{ color: 'var(--oh-text-sec)', flexShrink: 0 }} />
                  }
                  <span style={{ fontWeight: 600, fontSize: 15 }}>{farm.name}</span>
                  <span style={{
                    marginLeft: 'auto', fontSize: 12, color: 'var(--oh-text-muted)',
                    background: 'var(--oh-surface-2)', border: '1px solid var(--oh-border)',
                    borderRadius: 20, padding: '2px 10px',
                  }}>
                    {farmAlerts.length} alerta{farmAlerts.length !== 1 ? 's' : ''}
                  </span>
                </button>

                {isFarmOpen && (
                  <div style={{ borderTop: '1px solid var(--oh-border)' }}>
                    {terrains.map(({ terrain, alerts }) => {
                      if (alerts.length === 0) return null
                      const isTerrainOpen = !!expandedTerrains[terrain.id]

                      return (
                        <div key={terrain.id} style={{ borderBottom: '1px solid var(--oh-border)' }}>
                          {/* Terrain sub-header */}
                          <button
                            onClick={() => setExpandedTerrains(p => ({ ...p, [terrain.id]: !p[terrain.id] }))}
                            style={{
                              width: '100%', display: 'flex', alignItems: 'center', gap: 8,
                              padding: '10px 20px 10px 36px', background: 'var(--oh-surface-2)',
                              border: 'none', cursor: 'pointer', color: 'var(--oh-text)', textAlign: 'left',
                            }}
                          >
                            {isTerrainOpen
                              ? <ChevronDown size={14} style={{ color: 'var(--oh-text-sec)', flexShrink: 0 }} />
                              : <ChevronRight size={14} style={{ color: 'var(--oh-text-muted)', flexShrink: 0 }} />
                            }
                            <span style={{ fontSize: 13, fontWeight: 500 }}>{terrain.name}</span>
                            <span style={{ marginLeft: 'auto', fontSize: 11, color: 'var(--oh-text-muted)' }}>
                              {alerts.length} alerta{alerts.length !== 1 ? 's' : ''}
                            </span>
                          </button>

                          {isTerrainOpen && (
                            <div style={{ padding: '4px 16px 8px' }}>
                              {alerts.map(alert => {
                                const cfg = TYPE_CONFIG[alert.alertType] || DEFAULT_TYPE
                                const TypeIcon = cfg.Icon
                                return (
                                  <div
                                    key={alert.id}
                                    style={{
                                      display: 'flex', alignItems: 'flex-start', gap: 12,
                                      padding: '10px 8px',
                                      borderBottom: '1px solid var(--oh-border)',
                                    }}
                                  >
                                    <TypeIcon size={16} style={{ color: cfg.color, flexShrink: 0, marginTop: 2 }} />
                                    <div style={{ flex: 1, minWidth: 0 }}>
                                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                                        <span style={{
                                          fontSize: 11, fontWeight: 600, padding: '2px 8px',
                                          borderRadius: 20, background: cfg.bg, color: cfg.color,
                                          border: `1px solid ${cfg.border}`,
                                        }}>
                                          {cfg.label}
                                        </span>
                                        {alert.parcel?.name && (
                                          <span style={{ fontSize: 12, color: 'var(--oh-text-sec)' }}>
                                            Parcela: <strong>{alert.parcel.name}</strong>
                                          </span>
                                        )}
                                      </div>
                                      {alert.message && (
                                        <p style={{ margin: '4px 0 0', fontSize: 12, color: 'var(--oh-text-sec)', lineHeight: 1.5 }}>
                                          {alert.message}
                                        </p>
                                      )}
                                      <span style={{ fontSize: 11, color: 'var(--oh-text-muted)', marginTop: 4, display: 'block' }}>
                                        {formatDate(alert.createdAt)}
                                      </span>
                                    </div>
                                  </div>
                                )
                              })}
                            </div>
                          )}
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      <style>{`
        @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
      `}</style>
    </div>
  )
}
