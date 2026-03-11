import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useFarms } from '../hooks'
import Spinner from '../components/Spinner'
import EmptyState from '../components/EmptyState'
import ConfirmDialog from '../components/ConfirmDialog'

export default function FarmsPage() {
  const navigate = useNavigate()
  const { farms, terrainsByFarm, loading, deleteFarm } = useFarms()
  const [confirm, setConfirm] = useState(null)

  function handleDelete(e, id) {
    e.stopPropagation()
    setConfirm({
      title: 'Eliminar finca',
      message: '¿Eliminar esta finca y todos sus terrenos? Esta acción no se puede deshacer.',
      confirmLabel: 'Eliminar',
      onConfirm: async () => {
        try { await deleteFarm(id) } catch { /* toast shown in hook */ }
      },
    })
  }

  if (loading) return <Spinner page label="Cargando fincas..." />

  return (
    <div className="page-container">
      <div className="page-header">
        <h2>Mis Fincas</h2>
        <p>Gestiona tus fincas ganaderas y sus terrenos</p>
      </div>

      {farms.length === 0 ? (
        <EmptyState
          icon="🌾"
          title="Sin fincas registradas"
          description="Crea tu primera finca para comenzar a gestionar terrenos y parcelas."
          action={
            <button className="action-btn action-btn--primary" onClick={() => navigate('/farms/new')}>
              Crear Finca
            </button>
          }
        />
      ) : (
        <div className="farms-grid">
          {farms.map(farm => {
            const terrains = terrainsByFarm[farm.id] || []
            return (
              <div key={farm.id} className="farm-card" onClick={() => navigate(`/farms/${farm.id}`)}>
                <div className="farm-card__header">
                  <div className="farm-card__initial">{farm.name[0]}</div>
                  <h3>{farm.name}</h3>
                  <button
                    className="action-btn action-btn--danger action-btn--icon"
                    onClick={(e) => handleDelete(e, farm.id)}
                    title="Eliminar finca"
                  >
                    <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                      <path d="M3 4h10M5.5 4V3a1 1 0 011-1h3a1 1 0 011 1v1M6.5 7v4M9.5 7v4M4.5 4l.5 8a1 1 0 001 1h4a1 1 0 001-1l.5-8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                  </button>
                </div>
                <div className="farm-card__meta">
                  {farm.owner && <span>{farm.owner}</span>}
                  {farm.department && <span>{farm.municipality ? `${farm.municipality}, ` : ''}{farm.department}</span>}
                </div>

                <div className="farm-card__stat">
                  <span className="fcs-val">{terrains.length}</span>
                  <span className="fcs-lbl">terreno{terrains.length !== 1 ? 's' : ''}</span>
                </div>

                {terrains.length > 0 && (
                  <div className="farm-card__terrains">
                    {terrains.map(t => (
                      <div key={t.id} className="farm-terrain-row"
                        onClick={(e) => { e.stopPropagation(); navigate(`/terrains/${t.id}/parcels`) }}>
                        <span>{t.name || `Terreno ${t.id}`}</span>
                        <span className="ftr-area">{t.areaHectares?.toFixed(1)} ha</span>
                      </div>
                    ))}
                  </div>
                )}

                <div className="farm-card__footer" onClick={e => e.stopPropagation()}>
                  <button className="action-btn action-btn--small" onClick={() => navigate(`/farms/${farm.id}`)}>
                    Dashboard
                  </button>
                  <button className="action-btn action-btn--small action-btn--outline" onClick={() => navigate(`/farms/${farm.id}/terrain/new`)}>
                    + Terreno
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
        onConfirm={() => { confirm?.onConfirm?.(); setConfirm(null) }}
        onCancel={() => setConfirm(null)}
      />
    </div>
  )
}
