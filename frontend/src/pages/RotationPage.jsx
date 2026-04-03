import { useParams, useNavigate, Link } from 'react-router-dom'
import { MapContainer, TileLayer, GeoJSON, useMap, Tooltip } from 'react-leaflet'
import { useEffect } from 'react'
import L from 'leaflet'

import Spinner from '../components/Spinner'
import TerrainNav from '../components/TerrainNav'

import { useTerrain } from '../hooks'
import { getBiomassColor, getBiomassLabel } from '../utils/grazing'
import { getStatusClass } from '../utils/ndvi'

function FitBounds({ geoJson }) {
  const map = useMap()

  useEffect(() => {
    if (!geoJson) return

    try {
      const geo = typeof geoJson === 'string' ? JSON.parse(geoJson) : geoJson
      const layer = L.geoJSON(geo)
      map.fitBounds(layer.getBounds(), { padding: [30, 30] })
    } catch {}
  }, [geoJson, map])

  return null
}

const STATUS_CONFIG = {
  DISPONIBLE: { label: 'Disponible', color: '#4ade80', bgAlpha: 0.35, icon: '🌿' },
  EN_USO: { label: 'En uso', color: '#f59e0b', bgAlpha: 0.45, icon: '🐄' },
  EN_DESCANSO: { label: 'En descanso', color: '#3b82f6', bgAlpha: 0.3, icon: '💤' }
}

export default function RotationPage() {

  const { terrainId } = useParams()
  const navigate = useNavigate()

  const { terrain, parcels, parcelInfo, loading, updateParcelStatus } = useTerrain(terrainId)

  async function cycleStatus(parcelId, currentStatus) {

    const order = ['DISPONIBLE', 'EN_USO', 'EN_DESCANSO']

    const nextStatus = order[(order.indexOf(currentStatus) + 1) % order.length]

    try {
      await updateParcelStatus(parcelId, nextStatus)
    } catch {}
  }

  if (loading) return <Spinner page label="Cargando rotación..." />

  const terrainGeoJson = (() => {
    try {
      return JSON.parse(terrain.geoJson)
    } catch {
      return null
    }
  })()

  const statusCounts = {
    DISPONIBLE: parcels.filter(p => p.status === 'DISPONIBLE').length,
    EN_USO: parcels.filter(p => p.status === 'EN_USO').length,
    EN_DESCANSO: parcels.filter(p => p.status === 'EN_DESCANSO').length
  }

  return (

    <div className="page-container">

      <div className="page-header">

        <div className="breadcrumb">

          <Link to="/farms">Fincas</Link>

          <span>›</span>

          <Link to={`/terrains/${terrainId}/parcels`}>
            {terrain?.name || 'Terreno'}
          </Link>

          <span>›</span>

          <span>Rotación</span>

        </div>

        <TerrainNav name={terrain?.name} />
        <h2>Rotación de Pastoreo</h2>

        <p>
          Gestiona el estado de cada parcela para la rotación del ganado
        </p>

      </div>


      {/* Status summary */}

      <div style={{ display: 'flex', gap: 16, marginBottom: 24 }}>

        {Object.entries(STATUS_CONFIG).map(([key, config]) => (

          <div
            key={key}
            style={{
              flex: 1,
              padding: '16px 20px',
              background: 'var(--color-surface)',
              border: `1px solid ${config.color}33`,
              borderRadius: 'var(--radius)',
              display: 'flex',
              alignItems: 'center',
              gap: 12
            }}
          >

            <span style={{ fontSize: 28 }}>
              {config.icon}
            </span>

            <div>

              <div
                style={{
                  fontSize: 11,
                  color: 'var(--color-text-muted)',
                  textTransform: 'uppercase',
                  letterSpacing: 1
                }}
              >
                {config.label}
              </div>

              <div
                style={{
                  fontSize: 28,
                  fontFamily: 'var(--font-display)',
                  color: config.color
                }}
              >
                {statusCounts[key]}
              </div>

            </div>

          </div>

        ))}

      </div>


      <div className="two-col">


        {/* Map */}

        <div className="col-main">

          <div className="map-container">

            <MapContainer
              center={[4.6, -74.1]}
              zoom={15}
              style={{ height: '100%', width: '100%' }}
            >

              {terrainGeoJson && <FitBounds geoJson={terrainGeoJson} />}

              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                attribution="Tiles © Esri"
                maxZoom={19}
              />

              {terrainGeoJson && (

                <GeoJSON
                  data={terrainGeoJson}
                  style={{
                    color: '#ffffff',
                    weight: 2,
                    fillOpacity: 0.05,
                    dashArray: '6,3'
                  }}
                />

              )}

              {parcels.map(p => {

                const config = STATUS_CONFIG[p.status] || STATUS_CONFIG.DISPONIBLE

                const info = parcelInfo[p.id] || {}

                let geo

                try {
                  geo = JSON.parse(p.geoJson)
                } catch {
                  return null
                }

                return (

                  <GeoJSON
                    key={`${p.id}-${p.status}`}
                    data={geo}
                    style={{
                      color: config.color,
                      weight: info.lote ? 3 : 2,
                      fillColor: config.color,
                      fillOpacity: config.bgAlpha
                    }}
                  >

                    <Tooltip sticky>

                      <div style={{ minWidth: 180 }}>

                        <div style={{ fontWeight: 700, marginBottom: 4 }}>
                          {p.name}
                        </div>

                        <div style={{ fontSize: 12 }}>
                          {p.areaHectares?.toFixed(2)} ha
                        </div>

                        {info.lote && (

                          <div
                            style={{
                              fontSize: 12,
                              color: '#b45309',
                              fontWeight: 500
                            }}
                          >
                            🐄 {info.lote.name} ({info.lote.cabezas})
                          </div>

                        )}

                        {info.biomass != null && (

                          <div style={{ fontSize: 12 }}>

                            Pasto:

                            <span
                              style={{
                                fontWeight: 600,
                                color: getBiomassColor(info.biomass)
                              }}
                            >
                              {Math.round(info.biomass)} kg/ha
                            </span>

                            <span style={{ fontSize: 11 }}>
                              ({getBiomassLabel(info.biomass)})
                            </span>

                          </div>

                        )}

                      </div>

                    </Tooltip>

                  </GeoJSON>

                )

              })}

            </MapContainer>

          </div>

        </div>


        {/* Controls */}

        <div className="col-side">

          <div className="card">

            <h3
              style={{
                fontFamily: 'var(--font-display)',
                fontSize: 18,
                marginBottom: 16
              }}
            >
              Control de Parcelas
            </h3>

            <div className="rotation-grid">

              {parcels.map(p => {

                const config = STATUS_CONFIG[p.status] || STATUS_CONFIG.DISPONIBLE

                const info = parcelInfo[p.id] || {}

                const isLocked = !!info.lote

                return (

                  <div
                    key={p.id}
                    className="rotation-card"
                    style={{
                      cursor: isLocked ? 'not-allowed' : 'pointer',
                      borderColor: `${config.color}44`,
                      opacity: isLocked ? 0.7 : 1
                    }}
                    onClick={() => !isLocked && cycleStatus(p.id, p.status)}
                  >

                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>

                      <div>

                        <h4 style={{ fontSize: 16 }}>
                          {isLocked && '🔒 '}
                          {p.name}
                        </h4>

                        <div>
                          {p.areaHectares?.toFixed(2)} ha
                        </div>

                        {info.lote && (
                          <div style={{ fontSize: 11 }}>
                            {info.lote.name}
                          </div>
                        )}

                      </div>

                      <div className={`status-badge ${getStatusClass(p.status)}`}>
                        {config.icon} {config.label}
                      </div>

                    </div>

                  </div>

                )

              })}

            </div>

          </div>


          <div className="side-nav-buttons">

            <button
              className="action-btn action-btn--nav"
              onClick={() => navigate(`/terrains/${terrainId}/parcels`)}
            >
              ← Volver a Parcelas
            </button>

            <button
              className="action-btn action-btn--nav"
              onClick={() => navigate(`/terrains/${terrainId}/lotes`)}
            >
              🐄 Lotes
            </button>

            <button
              className="action-btn action-btn--nav action-btn--primary"
              onClick={() => navigate(`/terrains/${terrainId}/ndvi`)}
            >
              🛰️ Dashboard NDVI
            </button>

          </div>

        </div>

      </div>

    </div>
  )
}