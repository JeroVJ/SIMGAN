import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { MapContainer, TileLayer, FeatureGroup, GeoJSON, useMap, Tooltip } from 'react-leaflet'
import { EditControl } from 'react-leaflet-draw'
import * as turf from '@turf/turf'
import L from 'leaflet'
import toast from 'react-hot-toast'

import Spinner from '../components/Spinner'
import ConfirmDialog from '../components/ConfirmDialog'
import TerrainNav from '../components/TerrainNav'

import { useTerrain } from '../hooks'
import api from '../services/api'
import { getBiomassColor, getBiomassLabel } from '../utils/grazing'

const SOIL_TYPES = ['Arenosa', 'Limosa', 'Arcillosa', 'Franco Arcillosa']
const PASTURE_TYPES = ['Brachiaria humidicola']


delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
})

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

const STATUS_COLORS = {
  DISPONIBLE: '#4ade80',
  EN_USO: '#f59e0b',
  EN_DESCANSO: '#3b82f6'
}

const STATUS_LABELS = {
  DISPONIBLE: 'Disponible',
  EN_USO: 'En Uso',
  EN_DESCANSO: 'En Descanso'
}

export default function ParcelsPage() {

  const { terrainId } = useParams()
  const navigate = useNavigate()

  const {
    terrain,
    parcels,
    parcelInfo,
    loading,
    addParcel,
    updateParcelStatus,
    deleteParcel
  } = useTerrain(terrainId)

  const [saving, setSaving] = useState(false)
  const [parcelName, setParcelName] = useState('')
  const [drawnParcel, setDrawnParcel] = useState(null)
  const [drawnArea, setDrawnArea] = useState({ sqm: 0, ha: 0 })
  const [confirm, setConfirm] = useState(null)
  const [farm, setFarm] = useState(null)
  const [soilType, setSoilType] = useState('')
  const [pastureType, setPastureType] = useState('')
  const [farmLoading, setFarmLoading] = useState(true)

  const featureGroupRef = useRef(null)

  // Cargar datos de la finca
  useEffect(() => {
    if (!terrain?.farmId) return
    
    const loadFarm = async () => {
      try {
        setFarmLoading(true)
        const res = await api.get(`/farms/${terrain.farmId}`)
        console.log('Farm data loaded:', res.data)
        setFarm(res.data)
      } catch (err) {
        console.error('Error cargando finca:', err)
      } finally {
        setFarmLoading(false)
      }
    }
    
    loadFarm()
  }, [terrain?.farmId])

  function handleCreated(e) {

    const layer = e.layer
    const geoJson = layer.toGeoJSON()

    const area = turf.area(geoJson)

    if (terrain) {
      try {
        const terrainGeo = JSON.parse(terrain.geoJson)
        const inside = turf.booleanContains(terrainGeo, geoJson)

        if (!inside) {
          toast('⚠️ La parcela no está completamente dentro del terreno', { icon: '⚠️' })
        }

      } catch {}
    }

    setDrawnParcel(geoJson)
    setDrawnArea({
      sqm: area,
      ha: area / 10000
    })
  }

  function handleDeleted() {
    setDrawnParcel(null)
    setDrawnArea({ sqm: 0, ha: 0 })
  }

  async function handleSaveParcel() {

    if (!drawnParcel) {
      toast.error('Dibuja una parcela primero')
      return
    }

    // Si la finca NO es homogénea, validar que haya soilType y pastureType
    if (!farm?.isHomogeneous) {
      if (!soilType) {
        toast.error('Selecciona el tipo de suelo')
        return
      }
      if (!pastureType) {
        toast.error('Selecciona el tipo de pasto')
        return
      }
    }

    setSaving(true)

    try {

      const parcelData = {
        name: parcelName.trim() || `Parcela ${parcels.length + 1}`,
        terrainId: parseInt(terrainId),
        geoJson: JSON.stringify(drawnParcel),
        areaSqMeters: drawnArea.sqm,
        areaHectares: drawnArea.ha
      }

      // Si la finca NO es homogénea, incluir soil y pasture type
      if (!farm?.isHomogeneous) {
        parcelData.soilType = soilType
        parcelData.pastureType = pastureType
      }

      await addParcel(parcelData)

      setDrawnParcel(null)
      setDrawnArea({ sqm: 0, ha: 0 })
      setParcelName('')
      setSoilType('')
      setPastureType('')

      if (featureGroupRef.current) {
        featureGroupRef.current.clearLayers()
      }

      toast.success('Parcela guardada')

    } catch (err) {
      console.error('Error guardando parcela:', err)
      toast.error(err?.message || 'Error guardando parcela')
    }

    finally {
      setSaving(false)
    }
  }

  async function handleStatusChange(parcelId, status) {
    try {
      await updateParcelStatus(parcelId, status)
    } catch (err) {
      console.error('Error actualizando estado:', err)
      toast.error('Error actualizando estado')
    }
  }

  function handleDeleteParcel(id) {
    setConfirm({
      id,
      message: '¿Eliminar esta parcela? Esta acción no se puede deshacer.'
    })
  }

  if (loading) {
    return <Spinner page label="Cargando terreno..." />
  }

  const terrainGeoJson = (() => {
    try {
      return JSON.parse(terrain.geoJson)
    } catch {
      return null
    }
  })()

  const totalParcelArea = parcels.reduce((sum, p) => sum + (p.areaHectares || 0), 0)

  const coveragePercent =
    terrain?.areaHectares
      ? totalParcelArea / terrain.areaHectares * 100
      : 0


  return (

    <div className="page-container">

      <ConfirmDialog
        open={!!confirm}
        title="Eliminar parcela"
        message={confirm?.message}
        confirmLabel="Eliminar"
        variant="danger"
        onConfirm={async () => {
          try {
            await deleteParcel(confirm.id)
            toast.success('Parcela eliminada')
          } catch (err) {
            console.error('Error eliminando parcela:', err)
            toast.error('Error eliminando parcela')
          }
          setConfirm(null)
        }}
        onCancel={() => setConfirm(null)}
      />

      <div className="page-header">

        <div className="breadcrumb">

          <Link to="/farms">Fincas</Link>

          <span>›</span>

          <Link to={`/farms/${terrain?.farmId}`}>
            {terrain?.farmName}
          </Link>

          <span>›</span>

          <span>{terrain?.name}</span>

          <span>›</span>

          <span>Parcelas</span>

        </div>

        <TerrainNav name={terrain?.name} />
        <h2>Parcelas del Terreno</h2>

        <p>
          {terrain?.name} — {terrain?.areaHectares?.toFixed(2)} ha
        </p>

      </div>


      <div className="two-col">

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
                    weight: 3,
                    fillOpacity: 0.05,
                    dashArray: '8,4'
                  }}
                />
              )}

              {parcels.map(p => {

                const info = parcelInfo[p.id] || {}

                const color = STATUS_COLORS[p.status] || '#888'

                let geo

                try {
                  geo = JSON.parse(p.geoJson)
                } catch {
                  return null
                }

                return (

                  <GeoJSON
                    key={`p-${p.id}`}
                    data={geo}
                    style={{
                      color,
                      fillColor: color,
                      weight: 2,
                      fillOpacity: 0.25
                    }}
                  >

                    <Tooltip sticky>

                      <div style={{ minWidth: 180 }}>

                        <div style={{ fontWeight: 700 }}>
                          {p.name}
                        </div>

                        <div style={{ fontSize: 12 }}>
                          {p.areaHectares?.toFixed(2)} ha
                        </div>

                        {info.biomass != null && (
                          <div style={{ fontSize: 12 }}>
                            Pasto:
                            <span
                              style={{
                                color: getBiomassColor(info.biomass),
                                fontWeight: 600
                              }}
                            >
                              {Math.round(info.biomass)} kg/ha
                            </span>
                          </div>
                        )}

                      </div>

                    </Tooltip>

                  </GeoJSON>

                )

              })}

              <FeatureGroup ref={featureGroupRef}>

                <EditControl
                  position="topright"
                  onCreated={handleCreated}
                  onDeleted={handleDeleted}
                  draw={{
                    rectangle: false,
                    circle: false,
                    circlemarker: false,
                    marker: false,
                    polyline: false,
                    polygon: {
                      allowIntersection: false,
                      shapeOptions: {
                        color: '#f59e0b',
                        weight: 3,
                        fillOpacity: 0.3
                      }
                    }
                  }}
                />


            </FeatureGroup>

            </MapContainer>

          </div>

        </div>

        {/* Side Panel */}
        <div className="col-side">
          <div className="card">
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 20, marginBottom: 16 }}>
              Nueva Parcela
            </h3>
            <div className="form-group mb-16">
              <label>Nombre de la Parcela</label>
              <input
                value={parcelName}
                onChange={(e) => setParcelName(e.target.value)}
                placeholder="Ej: Potrero A"
              />
            </div>
            
            {farm && !farm.isHomogeneous && (
              <>
                <div className="form-group mb-16">
                  <label>Tipo de Suelo *</label>
                  <select
                    value={soilType}
                    onChange={(e) => setSoilType(e.target.value)}
                  >
                    <option value="">Seleccionar...</option>
                    {SOIL_TYPES.map(soil => (
                      <option key={soil} value={soil}>{soil}</option>
                    ))}
                  </select>
                </div>
                <div className="form-group mb-16">
                  <label>Tipo de Pasto *</label>
                  <select
                    value={pastureType}
                    onChange={(e) => setPastureType(e.target.value)}
                  >
                    <option value="">Seleccionar...</option>
                    {PASTURE_TYPES.map(pasture => (
                      <option key={pasture} value={pasture}>{pasture}</option>
                    ))}
                  </select>
                </div>
              </>
            )}
            
            {drawnParcel && (
              <>
                <div style={{ padding: '12px 16px', background: 'var(--color-bg)', borderRadius: 'var(--radius-sm)', marginBottom: 16 }}>
                  <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
                    <strong>Área dibujada:</strong>
                  </div>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>
                    {drawnArea.ha.toFixed(2)} <span style={{ fontSize: 12 }}>ha</span>
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>
                    {drawnArea.sqm.toLocaleString('es-CO', { maximumFractionDigits: 0 })} m²
                  </div>
                </div>
                <button
                  className="action-btn action-btn--primary"
                  style={{ width: '100%' }}
                  onClick={handleSaveParcel}
                  disabled={saving}
                >
                  {saving ? <><span className="spinner" /> Guardando...</> : '✓ Guardar Parcela'}
                </button>
              </>
            )}
            {!drawnParcel && (
              <div style={{ padding: '12px 16px', background: 'var(--color-bg)', borderRadius: 'var(--radius-sm)', fontSize: 13, color: 'var(--color-text-secondary)' }}>
                <strong style={{ color: 'var(--color-text)' }}>Instrucciones:</strong><br />
                1. Usa el icono de polígono en el mapa<br />
                2. Haz clic para definir vértices<br />
                3. Cierra el polígono haciendo clic en el primer punto<br />
                4. El área se calcula automáticamente
              </div>
            )}
          </div>

          {parcels.length > 0 && (
            <div className="card">
              <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 12 }}>
                Parcelas Existentes ({parcels.length})
              </h3>
              <div style={{ fontSize: 13, marginBottom: 16, color: 'var(--color-text-secondary)' }}>
                <strong>Cobertura:</strong> {coveragePercent.toFixed(1)}% del terreno
              </div>
              <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
                {Object.entries(STATUS_LABELS).map(([status, label]) => (
                  <div key={status} style={{ fontSize: 11, display: 'flex', alignItems: 'center', gap: 4 }}>
                    <div style={{ width: 8, height: 8, background: STATUS_COLORS[status], borderRadius: 2 }} />
                    <span>{label}</span>
                  </div>
                ))}
              </div>
              {parcels.map(p => (
                <div key={p.id} className="parcel-item" style={{ cursor: 'pointer' }}>
                  <div className="info">
                    <span className="name" style={{ color: STATUS_COLORS[p.status] }}>
                      {p.name}
                    </span>
                    <span className="area">{p.areaHectares?.toFixed(2)} ha</span>
                  </div>
                  <select
                    value={p.status || 'DISPONIBLE'}
                    onChange={(e) => handleStatusChange(p.id, e.target.value)}
                    style={{ fontSize: 11, padding: '4px 6px' }}
                    onClick={(e) => e.stopPropagation()}
                  >
                    <option value="DISPONIBLE">Disponible</option>
                    <option value="EN_USO">En Uso</option>
                    <option value="EN_DESCANSO">En Descanso</option>
                  </select>
                  <button
                    onClick={(e) => {
                      e.stopPropagation()
                      if (!farm?.iotEnabled) {
                        toast.error('IoT deshabilitado para esta finca')
                        return
                      }
                      navigate(`/parcels/${p.id}/sensors`)
                    }}
                    style={{
                      background: farm?.iotEnabled ? '#3b82f6' : '#9ca3af',
                      color: 'white',
                      border: 'none',
                      padding: '4px 8px',
                      borderRadius: '4px',
                      cursor: farm?.iotEnabled ? 'pointer' : 'not-allowed',
                      fontSize: 11,
                      fontWeight: 500
                    }}
                  >
                     {farm?.iotEnabled ? 'Sensores' : 'IoT OFF'}
                  </button>
                  <button
                    onClick={(e) => {
                      e.stopPropagation()
                      handleDeleteParcel(p.id)
                    }}
                    style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer', fontSize: 16 }}
                  >
                    🗑️
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

      </div>

    </div>
  )
}