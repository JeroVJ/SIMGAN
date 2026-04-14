import { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import toast from 'react-hot-toast'
import { FileDown, Loader2, Map, ChevronDown, ChevronRight, FileText } from 'lucide-react'
import { farmApi, terrainApi, reportApi } from '../services/api'
import Spinner from '../components/Spinner'
import EmptyState from '../components/EmptyState'

export default function ReportesPage() {
  const [farms, setFarms] = useState([])
  const [terrainsByFarm, setTerrainsByFarm] = useState({})
  const [loading, setLoading] = useState(true)
  const [expandedFarms, setExpandedFarms] = useState({})
  const [downloadingId, setDownloadingId] = useState(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const data = await farmApi.getAll()
      setFarms(data)

      const pairs = await Promise.all(
        data.map(async (farm) => {
          try {
            const terrains = await terrainApi.getByFarm(farm.id)
            return [farm.id, terrains]
          } catch {
            return [farm.id, []]
          }
        })
      )
      const map = Object.fromEntries(pairs)
      setTerrainsByFarm(map)

      // Auto-expand farms that have terrains
      const expanded = {}
      for (const [farmId, terrains] of pairs) {
        if (terrains.length > 0) expanded[farmId] = true
      }
      setExpandedFarms(expanded)
    } catch {
      toast.error('Error cargando fincas')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  function toggleFarm(farmId) {
    setExpandedFarms(prev => ({ ...prev, [farmId]: !prev[farmId] }))
  }

  async function handleDownload(terrain) {
    setDownloadingId(terrain.id)
    try {
      const blob = await reportApi.downloadTerrainReport(terrain.id)
      const url  = URL.createObjectURL(blob)
      const a    = document.createElement('a')
      a.href     = url
      a.download = `SIMGAN-Informe-${terrain.name || terrain.id}.pdf`
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
      toast.success(`Informe de ${terrain.name} descargado`)
    } catch {
      toast.error('No se pudo generar el informe. Intente de nuevo.')
    } finally {
      setDownloadingId(null)
    }
  }

  if (loading) return <Spinner page label="Cargando fincas..." />

  return (
    <div className="page-container">
      <div className="page-header">
        <h2 style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <FileText size={22} strokeWidth={1.75} style={{ color: 'var(--oh-green)' }} />
          Reportes
        </h2>
        <p>Genera y descarga informes PDF completos por terreno.</p>
      </div>

      {farms.length === 0 ? (
        <EmptyState
          icon="📄"
          title="Sin fincas registradas"
          description="Crea una finca para poder generar reportes de sus terrenos."
          action={
            <Link to="/farms/new" className="action-btn action-btn--primary">
              Crear Finca
            </Link>
          }
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {farms.map(farm => {
            const terrains = terrainsByFarm[farm.id] || []
            const isOpen   = !!expandedFarms[farm.id]

            return (
              <div
                key={farm.id}
                style={{
                  background: 'var(--oh-surface)',
                  border: '1px solid var(--oh-border)',
                  borderRadius: 'var(--oh-r-lg)',
                  overflow: 'hidden',
                }}
              >
                {/* Farm header */}
                <button
                  onClick={() => toggleFarm(farm.id)}
                  style={{
                    width: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    padding: '14px 18px',
                    background: 'transparent',
                    border: 'none',
                    cursor: 'pointer',
                    color: 'var(--oh-text)',
                    textAlign: 'left',
                  }}
                >
                  {isOpen
                    ? <ChevronDown size={16} style={{ color: 'var(--oh-green)', flexShrink: 0 }} />
                    : <ChevronRight size={16} style={{ color: 'var(--oh-text-sec)', flexShrink: 0 }} />
                  }
                  <span style={{ fontWeight: 600, fontSize: 15 }}>{farm.name}</span>
                  {farm.location && (
                    <span style={{ fontSize: 12, color: 'var(--oh-text-sec)', marginLeft: 4 }}>
                      {farm.location}
                    </span>
                  )}
                  <span
                    style={{
                      marginLeft: 'auto',
                      fontSize: 12,
                      color: 'var(--oh-text-muted)',
                      background: 'var(--oh-surface-2)',
                      border: '1px solid var(--oh-border)',
                      borderRadius: 20,
                      padding: '2px 10px',
                    }}
                  >
                    {terrains.length} terreno{terrains.length !== 1 ? 's' : ''}
                  </span>
                </button>

                {/* Terrains list */}
                {isOpen && (
                  <div
                    style={{
                      borderTop: '1px solid var(--oh-border)',
                      padding: terrains.length === 0 ? '14px 20px' : '8px 16px 12px',
                    }}
                  >
                    {terrains.length === 0 ? (
                      <p style={{ fontSize: 13, color: 'var(--oh-text-muted)', margin: 0 }}>
                        No hay terrenos en esta finca.
                      </p>
                    ) : (
                      terrains.map(terrain => (
                        <div
                          key={terrain.id}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 12,
                            padding: '10px 4px',
                            borderBottom: '1px solid var(--oh-border)',
                          }}
                        >
                          <Map size={14} strokeWidth={1.75} style={{ color: 'var(--oh-text-sec)', flexShrink: 0 }} />
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ fontWeight: 500, fontSize: 14, color: 'var(--oh-text)' }}>
                              {terrain.name}
                            </div>
                            {terrain.description && (
                              <div style={{ fontSize: 12, color: 'var(--oh-text-sec)', marginTop: 2 }}>
                                {terrain.description}
                              </div>
                            )}
                          </div>
                          <button
                            onClick={() => handleDownload(terrain)}
                            disabled={downloadingId === terrain.id}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 6,
                              padding: '7px 14px',
                              background: downloadingId === terrain.id
                                ? 'var(--oh-surface-2)'
                                : 'var(--oh-green-dim)',
                              border: '1px solid var(--oh-green-border)',
                              borderRadius: 'var(--oh-r-sm)',
                              color: 'var(--oh-green)',
                              fontSize: 13,
                              fontWeight: 500,
                              cursor: downloadingId === terrain.id ? 'not-allowed' : 'pointer',
                              whiteSpace: 'nowrap',
                              flexShrink: 0,
                              opacity: downloadingId === terrain.id ? 0.6 : 1,
                            }}
                          >
                            {downloadingId === terrain.id
                              ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} />
                              : <FileDown size={14} />
                            }
                            {downloadingId === terrain.id ? 'Generando...' : 'Descargar PDF'}
                          </button>
                        </div>
                      ))
                    )}
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
