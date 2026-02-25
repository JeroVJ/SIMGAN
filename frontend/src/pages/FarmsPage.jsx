import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { farmApi, terrainApi } from '../services/api'
import toast from 'react-hot-toast'

export default function FarmsPage() {
  const [farms, setFarms] = useState([])
  const [terrainsByFarm, setTerrainsByFarm] = useState({})
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    loadFarms()
  }, [])

  async function loadFarms() {
    try {
      const data = await farmApi.getAll()
      setFarms(data)

      // Load terrains for each farm
      const terrainsMap = {}
      for (const farm of data) {
        try {
          terrainsMap[farm.id] = await terrainApi.getByFarm(farm.id)
        } catch {
          terrainsMap[farm.id] = []
        }
      }
      setTerrainsByFarm(terrainsMap)
    } catch (err) {
      toast.error('Error cargando fincas')
    } finally {
      setLoading(false)
    }
  }

  async function handleDelete(e, id) {
    e.stopPropagation()
    if (!window.confirm('¿Eliminar esta finca y todos sus terrenos?')) return
    try {
      await farmApi.delete(id)
      toast.success('Finca eliminada')
      loadFarms()
    } catch {
      toast.error('Error eliminando finca')
    }
  }

  if (loading) {
    return (
      <div className="empty-state">
        <div className="spinner" />
        <p style={{ marginTop: 16 }}>Cargando fincas...</p>
      </div>
    )
  }

  return (
    <div>
      <div className="page-header">
        <h2>Mis Fincas</h2>
        <p>Gestiona tus fincas ganaderas y sus terrenos</p>
      </div>

      {farms.length === 0 ? (
        <div className="empty-state card">
          <div className="icon">🌾</div>
          <h3>Sin fincas registradas</h3>
          <p>Crea tu primera finca para comenzar a gestionar terrenos y parcelas.</p>
          <button className="btn btn-primary" onClick={() => navigate('/farms/new')}>
            ➕ Crear Finca
          </button>
        </div>
      ) : (
        <div className="farms-grid">
          {farms.map(farm => {
            const terrains = terrainsByFarm[farm.id] || []
            return (
              <div key={farm.id} className="farm-card" onClick={() => navigate(`/farms/${farm.id}/terrain/new`)}>
                <h3>{farm.name}</h3>
                <div className="meta">
                  <span>👤 {farm.owner}</span>
                  {farm.department && <span>📍 {farm.municipality ? `${farm.municipality}, ` : ''}{farm.department}</span>}
                  <span>🗺️ {terrains.length} terreno{terrains.length !== 1 ? 's' : ''}</span>
                </div>

                {/* Show terrains if any */}
                {terrains.length > 0 && (
                  <div style={{ marginTop: 12 }}>
                    {terrains.map(t => (
                      <div
                        key={t.id}
                        style={{
                          padding: '8px 12px',
                          background: 'var(--color-bg)',
                          borderRadius: 'var(--radius-sm)',
                          marginBottom: 6,
                          fontSize: 13,
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center'
                        }}
                        onClick={(e) => {
                          e.stopPropagation()
                          navigate(`/terrains/${t.id}/parcels`)
                        }}
                      >
                        <span>{t.name || `Terreno ${t.id}`}</span>
                        <span style={{ color: 'var(--color-primary)' }}>
                          {t.areaHectares?.toFixed(2)} ha
                        </span>
                      </div>
                    ))}
                  </div>
                )}

                <div className="card-actions">
                  <button
                    className="btn btn-primary btn-sm"
                    onClick={(e) => {
                      e.stopPropagation()
                      navigate(`/farms/${farm.id}/terrain/new`)
                    }}
                  >
                    🗺️ Agregar Terreno
                  </button>
                  <button
                    className="btn btn-danger btn-sm"
                    onClick={(e) => handleDelete(e, farm.id)}
                  >
                    🗑️
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
