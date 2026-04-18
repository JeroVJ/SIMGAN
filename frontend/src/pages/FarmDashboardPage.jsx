import { useParams, useNavigate, Link } from 'react-router-dom'
import {
  MapPin, Layers, LayoutGrid, Tractor, Beef, Plus,
  Map, Users, Activity, Shuffle, ArrowRight,
} from 'lucide-react'
import { useFarm } from '../hooks'
import StatCard from '../components/StatCard'
import Spinner from '../components/Spinner'
import EmptyState from '../components/EmptyState'

export default function FarmDashboardPage() {
  const { farmId } = useParams()
  const navigate = useNavigate()
  const { farm, terrains, terrainData, stats, loading } = useFarm(farmId)

  if (loading) return <Spinner page label="Cargando finca..." />
  if (!farm) return null

  return (
    <div className="page-container">
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
            {(farm.department || farm.municipality) && (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                <MapPin size={13} strokeWidth={1.9} />
                {[farm.municipality, farm.department].filter(Boolean).join(', ')}
              </span>
            )}
            {farm.isHomogeneous && (
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '2px 10px',
                  border: '1px solid var(--color-border)',
                  background: 'var(--color-surface-2)',
                  borderRadius: 100,
                  fontSize: 11.5,
                  color: 'var(--color-text-secondary)',
                }}
              >
                Finca homogénea · {farm.soilType || '—'} · {farm.pastureType || '—'}
              </span>
            )}
          </div>
        </div>
        <button
          className="comp-btn comp-btn--primary"
          onClick={() => navigate(`/farms/${farmId}/terrain/new`)}
        >
          <Plus size={15} strokeWidth={2} />
          Agregar Terreno
        </button>
      </div>

      {/* Stats */}
      <div className="p4-stats-grid">
        <StatCard
          label="Terrenos"
          value={terrains.length}
          sub="registrados"
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
          title="Aún no hay terrenos"
          description="Agrega tu primer terreno para delimitar potreros, asignar ganado y monitorear NDVI."
          action={
            <button
              className="comp-btn comp-btn--primary"
              onClick={() => navigate(`/farms/${farmId}/terrain/new`)}
            >
              <Plus size={15} strokeWidth={2} />
              Crear primer terreno
            </button>
          }
        />
      ) : (
        <>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 14,
              marginTop: 4,
            }}
          >
            <h2
              style={{
                fontSize: 17,
                fontWeight: 600,
                letterSpacing: '-0.01em',
                color: 'var(--color-text)',
              }}
            >
              Terrenos
            </h2>
            <span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>
              {terrains.length} {terrains.length === 1 ? 'terreno' : 'terrenos'}
            </span>
          </div>

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
                  <div
                    className="terrain-card__header"
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      gap: 10,
                    }}
                  >
                    <h3
                      style={{
                        flex: 1,
                        minWidth: 0,
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                      }}
                    >
                      {t.name || `Terreno ${t.id}`}
                    </h3>
                    <span className="terrain-card__area">
                      {t.areaHectares?.toFixed(1)} ha
                    </span>
                  </div>

                  {data.parcels.length > 0 && (
                    <div className="parcel-status-bar">
                      {disponibles > 0 && (
                        <div
                          className="psb__seg psb__seg--disponible"
                          style={{ flex: disponibles }}
                          title={`${disponibles} disponible(s)`}
                        />
                      )}
                      {enUso > 0 && (
                        <div
                          className="psb__seg psb__seg--en-uso"
                          style={{ flex: enUso }}
                          title={`${enUso} en uso`}
                        />
                      )}
                      {enDescanso > 0 && (
                        <div
                          className="psb__seg psb__seg--descanso"
                          style={{ flex: enDescanso }}
                          title={`${enDescanso} en descanso`}
                        />
                      )}
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
                      <span
                        className="tcs__val"
                        style={{
                          color: avgNdvi
                            ? avgNdvi > 0.5
                              ? 'var(--color-primary)'
                              : avgNdvi > 0.3
                                ? 'var(--color-warning)'
                                : 'var(--color-danger)'
                            : 'var(--color-text-muted)',
                        }}
                      >
                        {avgNdvi ? avgNdvi.toFixed(2) : '—'}
                      </span>
                      <span className="tcs__lbl">NDVI</span>
                    </div>
                  </div>

                  <div className="terrain-card__footer-nav">
                    <button
                      className="terrain-card__footer-nav-item"
                      onClick={() => navigate(`/terrains/${t.id}/parcels`)}
                    >
                      <Map size={12} strokeWidth={1.9} />
                      Potreros
                    </button>
                    <button
                      className="terrain-card__footer-nav-item"
                      onClick={() => navigate(`/terrains/${t.id}/lotes`)}
                    >
                      <Users size={12} strokeWidth={1.9} />
                      Ganado
                    </button>
                    <button
                      className="terrain-card__footer-nav-item"
                      onClick={() => navigate(`/terrains/${t.id}/ndvi`)}
                    >
                      <Activity size={12} strokeWidth={1.9} />
                      NDVI
                    </button>
                    <button
                      className="terrain-card__footer-nav-item"
                      onClick={() => navigate(`/terrains/${t.id}/rotation`)}
                    >
                      <Shuffle size={12} strokeWidth={1.9} />
                      Rotación
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        </>
      )}
    </div>
  )
}
