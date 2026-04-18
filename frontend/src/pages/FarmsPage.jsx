import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Plus, Trash2, MapPin, Layers, ArrowRight, ChevronDown, ChevronUp,
} from 'lucide-react'
import toast from 'react-hot-toast'
import { farmApi, terrainApi } from '../services/api'
import Spinner from '../components/Spinner'
import EmptyState from '../components/EmptyState'
import ConfirmDialog from '../components/ConfirmDialog'

export default function FarmsPage() {
  const [farms, setFarms] = useState([])
  const [terrainsByFarm, setTerrainsByFarm] = useState({})
  const [loading, setLoading] = useState(true)
  const [confirm, setConfirm] = useState(null)
  const [expandedFarmId, setExpandedFarmId] = useState(null)
  const navigate = useNavigate()

  useEffect(() => { loadFarms() }, [])

  async function loadFarms() {
    try {
      const data = await farmApi.getAll()
      setFarms(data)
      const terrainsMap = {}
      for (const farm of data) {
        try {
          terrainsMap[farm.id] = await terrainApi.getByFarm(farm.id)
        } catch {
          terrainsMap[farm.id] = []
        }
      }
      setTerrainsByFarm(terrainsMap)
    } catch {
      toast.error('Error cargando fincas')
    } finally {
      setLoading(false)
    }
  }

  function handleDelete(e, id) {
    e.stopPropagation()
    setConfirm({
      title: 'Eliminar finca',
      message: '¿Eliminar esta finca y todos sus terrenos? Esta acción no se puede deshacer.',
      confirmLabel: 'Eliminar',
      onConfirm: async () => {
        try {
          await farmApi.delete(id)
          toast.success('Finca eliminada')
          loadFarms()
        } catch {
          toast.error('Error eliminando finca')
        }
      },
    })
  }

  function toggleFarmDetails(id) {
    setExpandedFarmId(current => (current === id ? null : id))
  }

  if (loading) return <Spinner page label="Cargando fincas..." />

  return (
    <div className="page-container">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <h2>Mis Fincas</h2>
          <p>Gestiona tus fincas ganaderas, terrenos y potreros desde un solo lugar.</p>
        </div>
        {farms.length > 0 && (
          <button
            className="comp-btn comp-btn--primary"
            onClick={() => navigate('/farms/new')}
          >
            <Plus size={15} strokeWidth={2} />
            Nueva finca
          </button>
        )}
      </div>

      {farms.length === 0 ? (
        <EmptyState
          icon={<Layers size={24} strokeWidth={1.75} />}
          title="Sin fincas registradas"
          description="Crea tu primera finca para comenzar a gestionar terrenos, potreros y ganado."
          action={
            <button
              className="comp-btn comp-btn--primary"
              onClick={() => navigate('/farms/new')}
            >
              <Plus size={15} strokeWidth={2} />
              Crear primera finca
            </button>
          }
        />
      ) : (
        <div className="farms-grid">
          {farms.map(farm => {
            const terrains = terrainsByFarm[farm.id] || []
            const isExpanded = expandedFarmId === farm.id
            const totalHa = terrains.reduce((s, t) => s + (t.areaHectares || 0), 0)

            return (
              <div
                key={farm.id}
                className="farm-card"
                onClick={() => navigate(`/farms/${farm.id}`)}
                role="button"
                tabIndex={0}
              >
                <div className="farm-card__header">
                  <div className="farm-card__initial">
                    {(farm.name?.[0] || '?').toUpperCase()}
                  </div>
                  <h3>{farm.name || 'Finca sin nombre'}</h3>
                  <button
                    className="comp-btn comp-btn--ghost comp-btn--sm comp-btn--icon-only"
                    onClick={(e) => handleDelete(e, farm.id)}
                    title="Eliminar finca"
                    aria-label="Eliminar finca"
                  >
                    <Trash2 size={14} strokeWidth={1.9} />
                  </button>
                </div>

                {(farm.municipality || farm.department) && (
                  <div className="farm-card__meta">
                    <MapPin size={12} strokeWidth={1.9} />
                    {[farm.municipality, farm.department].filter(Boolean).join(', ')}
                  </div>
                )}

                <div className="farm-card__stat">
                  <span className="fcs-val">{terrains.length}</span>
                  <span className="fcs-lbl" style={{ marginLeft: 6, color: 'var(--color-text-secondary)' }}>
                    {terrains.length === 1 ? 'Terreno' : 'Terrenos'}
                  </span>
                  <span style={{ marginLeft: 'auto', fontSize: 12, color: 'var(--color-text-muted)' }}>
                    {totalHa.toFixed(1)} ha
                  </span>
                </div>

                {terrains.length > 0 && (
                  <div className="farm-card__terrains">
                    {terrains.slice(0, isExpanded ? terrains.length : 3).map(t => (
                      <div
                        key={t.id}
                        className="farm-terrain-row"
                        onClick={(e) => {
                          e.stopPropagation()
                          navigate(`/terrains/${t.id}/parcels`)
                        }}
                      >
                        <span>{t.name || `Terreno ${t.id}`}</span>
                        <span className="ftr-area">
                          {t.areaHectares?.toFixed(1)} ha
                        </span>
                      </div>
                    ))}
                    {terrains.length > 3 && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          toggleFarmDetails(farm.id)
                        }}
                        style={{
                          background: 'none',
                          border: 'none',
                          color: 'var(--color-text-muted)',
                          fontSize: 12,
                          cursor: 'pointer',
                          padding: '4px 0',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 4,
                          fontFamily: 'inherit',
                        }}
                      >
                        {isExpanded ? (
                          <>
                            <ChevronUp size={12} /> Mostrar menos
                          </>
                        ) : (
                          <>
                            <ChevronDown size={12} /> Ver {terrains.length - 3} más
                          </>
                        )}
                      </button>
                    )}
                  </div>
                )}

                <div
                  className="farm-card__footer"
                  onClick={e => e.stopPropagation()}
                >
                  <button
                    className="comp-btn comp-btn--sm"
                    onClick={() => navigate(`/farms/${farm.id}`)}
                    style={{ flex: 1 }}
                  >
                    Dashboard
                    <ArrowRight size={12} strokeWidth={2} />
                  </button>
                  <button
                    className="comp-btn comp-btn--sm comp-btn--outline"
                    onClick={() => navigate(`/farms/${farm.id}/terrain/new`)}
                  >
                    <Plus size={12} strokeWidth={2} />
                    Terreno
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      <ConfirmDialog
        open={!!confirm}
        title={confirm?.title}
        message={confirm?.message}
        confirmLabel={confirm?.confirmLabel || 'Confirmar'}
        variant="danger"
        onConfirm={() => {
          confirm?.onConfirm?.()
          setConfirm(null)
        }}
        onCancel={() => setConfirm(null)}
      />
    </div>
  )
}
