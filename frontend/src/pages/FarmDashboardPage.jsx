import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { MapPin, Layers, LayoutGrid, Tractor, Beef } from 'lucide-react'
import { useFarm } from '../hooks'
import { terrainApi } from '../services/api'
import StatCard from '../components/StatCard'
import Spinner from '../components/Spinner'
import EmptyState from '../components/EmptyState'
import ConfirmDialog from '../components/ConfirmDialog'
import toast from 'react-hot-toast'

export default function FarmDashboardPage() {
  const { farmId } = useParams()
  const navigate = useNavigate()
  const { farm, terrains, terrainData, stats, loading, reload } = useFarm(farmId)
  const [confirmDelete, setConfirmDelete] = useState(null)

  async function handleDeleteTerrain() {
    if (!confirmDelete?.id) return

    try {
      await terrainApi.delete(confirmDelete.id)
      toast.success('Terreno eliminado')
      await reload()
    } catch (err) {
      toast.error(err?.response?.data?.error || err?.response?.data?.message || 'Error eliminando terreno')
    } finally {
      setConfirmDelete(null)
    }
  }

  if (loading) return <Spinner page label="Cargando finca..." />

  if (!farm) return null

  return (
    <div className="page-container">
      <ConfirmDialog
        open={!!confirmDelete}
        title="Eliminar terreno"
        message={confirmDelete ? `¿Eliminar el terreno "${confirmDelete.name}"? También se eliminarán sus potreros. Esta acción no se puede deshacer.` : ''}
        confirmLabel="Eliminar"
        variant="danger"
        onConfirm={handleDeleteTerrain}
        onCancel={() => setConfirmDelete(null)}
      />

      {/* Breadcrumb */}
      <div className="breadcrumb">
        <Link to="/farms">Fincas</Link>
        <span className="sep">/</span>
        <span>{farm.name}</span>
      </div>

      {/* Header */}
      <div className="dashboard-header">
        <div>
          <h1 className="dashboard-title">{farm.name}</h1>
          <div className="dashboard-meta">
            {farm.department && <span>{farm.municipality && `${farm.municipality}, `}{farm.department}</span>}
          </div>
        </div>
        <button className="action-btn action-btn--outline" onClick={() => navigate(`/farms/${farmId}/terrain/new`)}>
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M8 3v10M3 8h10" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>
          Agregar Terreno
        </button>
      </div>

      {/* Stats */}
      <div className="p4-stats-grid">
        <StatCard
          label="Terrenos"
          value={terrains.length}
          sub="registradas"
          icon={<Layers size={18} strokeWidth={1.75} />}
          accent="green"
        />
        <StatCard
          label="Potreros"
          value={stats.totalParcels}
          sub="delimitados"
          icon={<LayoutGrid size={18} strokeWidth={1.75} />}
          accent="blue"
        />
        <StatCard
          label="Hectáreas"
          value={stats.totalHa.toFixed(1)}
          sub="en total"
          icon={<MapPin size={18} strokeWidth={1.75} />}
          accent="green"
        />
        <StatCard
          label="Lotes Activos"
          value={stats.activeLotes}
          sub="en pastoreo"
          icon={<Tractor size={18} strokeWidth={1.75} />}
          accent="amber"
        />
        <StatCard
          label="Cabezas"
          value={stats.totalCabezas}
          sub="de ganado"
          icon={<Beef size={18} strokeWidth={1.75} />}
          accent="amber"
        />
      </div>

      {/* Terrains */}
      {terrains.length === 0 ? (
        <EmptyState
          icon="🗺️"
          title="Sin terrenos"
          description="Crea tu primer terreno para empezar a gestionar potreros y ganado."
          action={
            <button className="action-btn action-btn--primary" onClick={() => navigate(`/farms/${farmId}/terrain/new`)}>
              Crear Terreno
            </button>
          }
        />
      ) : (
        <div className="terrain-grid">
          {terrains.map(t => {
            const data = terrainData[t.id] || { parcels: [], lotes: [], ndvi: null }
            const aLotes = data.lotes.filter(l => !l.fechaSalida)
            const cabezas = data.lotes.reduce((a, l) => a + (l.cabezas || 0), 0)
            const avgNdvi = data.ndvi?.avgNdvi
            const disponibles = data.parcels.filter(p => p.status === 'DISPONIBLE').length
            const enUso = data.parcels.filter(p => p.status === 'EN_USO').length
            const enDescanso = data.parcels.filter(p => p.status === 'EN_DESCANSO').length

            return (
              <div key={t.id} className="terrain-card">
                <div className="terrain-card__header">
                  <h3>{t.name || `Terreno ${t.id}`}</h3>
                  <span className="terrain-card__area">{t.areaHectares?.toFixed(1)} ha</span>
                </div>

                {data.parcels.length > 0 && (
                  <div className="parcel-status-bar">
                    {disponibles > 0 && <div className="psb__seg psb__seg--disponible" style={{ flex: disponibles }} title={`${disponibles} disponible(s)`} />}
                    {enUso > 0 && <div className="psb__seg psb__seg--en-uso" style={{ flex: enUso }} title={`${enUso} en uso`} />}
                    {enDescanso > 0 && <div className="psb__seg psb__seg--descanso" style={{ flex: enDescanso }} title={`${enDescanso} en descanso`} />}
                  </div>
                )}

                <div className="terrain-card__stats">
                  <div>
                    <span className="tcs__val">{data.parcels.length}</span>
                    <span className="tcs__lbl">Potreros</span>
                  </div>
                  <div>
                    <span className="tcs__val">{aLotes.length}</span>
                    <span className="tcs__lbl">Lotes</span>
                  </div>
                  <div>
                    <span className="tcs__val">{cabezas}</span>
                    <span className="tcs__lbl">Cabezas</span>
                  </div>
                  <div>
                    <span className="tcs__val" style={{ color: avgNdvi ? (avgNdvi > 0.5 ? '#4ade80' : avgNdvi > 0.3 ? '#f59e0b' : '#ef4444') : 'var(--color-text-muted)' }}>
                      {avgNdvi ? avgNdvi.toFixed(2) : '—'}
                    </span>
                    <span className="tcs__lbl">NDVI</span>
                  </div>
                </div>

                <div className="terrain-card__footer-nav">
                  <button className="terrain-card__footer-nav-item" onClick={() => navigate(`/terrains/${t.id}/edit`)}>
                    Editar
                  </button>
                  <button className="terrain-card__footer-nav-item" onClick={() => navigate(`/terrains/${t.id}/parcels`)}>
                    Potreros
                  </button>
                  <button className="terrain-card__footer-nav-item" onClick={() => navigate(`/terrains/${t.id}/lotes`)}>
                    Ganado
                  </button>
                  <button className="terrain-card__footer-nav-item" onClick={() => navigate(`/terrains/${t.id}/ndvi`)}>
                    NDVI
                  </button>
                  <button className="terrain-card__footer-nav-item" onClick={() => navigate(`/terrains/${t.id}/rotation`)}>
                    Pastoreo
                  </button>
                  <button
                    className="terrain-card__footer-nav-item"
                    style={{ color: '#ef4444' }}
                    onClick={() => setConfirmDelete({ id: t.id, name: t.name || `Terreno ${t.id}` })}
                  >
                    Eliminar
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
