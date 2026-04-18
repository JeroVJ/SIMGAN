import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { MapContainer, TileLayer, FeatureGroup, GeoJSON, useMap, Tooltip } from 'react-leaflet'
import { EditControl } from 'react-leaflet-draw'
import * as turf from '@turf/turf'
import L from 'leaflet'
import toast from 'react-hot-toast'

import Spinner from '../components/Spinner'
import ConfirmDialog from '../components/ConfirmDialog'
import TerrainTabs from '../components/TerrainTabs'
import { Trash2, Cpu, CheckCircle2, Loader2, Info } from 'lucide-react'

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
  const [drawnInside, setDrawnInside] = useState(true)
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

    let inside = true
    if (terrain) {
      try {
        const terrainGeo = JSON.parse(terrain.geoJson)
        inside = turf.booleanContains(terrainGeo, geoJson)

        if (!inside) {
          toast.error('El potrero debe estar completamente dentro del terreno')
        }
      } catch {
        inside = false
      }
    }

    setDrawnParcel(geoJson)
    setDrawnInside(inside)
    setDrawnArea({
      sqm: area,
      ha: area / 10000
    })
  }

  function handleDeleted() {
    setDrawnParcel(null)
    setDrawnInside(true)
    setDrawnArea({ sqm: 0, ha: 0 })
  }

  async function handleSaveParcel() {

    if (!drawnParcel) {
      toast.error('Dibuja un potrero primero')
      return
    }

    if (!drawnInside) {
      toast.error('No puedes guardar: el potrero se sale del terreno')
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
        name: parcelName.trim() || `Potrero ${parcels.length + 1}`,
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
      setDrawnInside(true)
      setDrawnArea({ sqm: 0, ha: 0 })
      setParcelName('')
      setSoilType('')
      setPastureType('')

      if (featureGroupRef.current) {
        featureGroupRef.current.clearLayers()
      }

      toast.success('Potrero guardado')

    } catch (err) {
      console.error('Error guardando potrero:', err)
      toast.error(err?.message || 'Error guardando potrero')
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
      message: '¿Eliminar este potrero? Esta acción no se puede deshacer.'
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
        title="Eliminar potrero"
        message={confirm?.message}
        confirmLabel="Eliminar"
        variant="danger"
        onConfirm={async () => {
          try {
            await deleteParcel(confirm.id)
            toast.success('Potrero eliminado')
          } catch (err) {
            console.error('Error eliminando potrero:', err)
            toast.error('Error eliminando potrero')
          }
          setConfirm(null)
        }}
        onCancel={() => setConfirm(null)}
      />

      <TerrainTabs
        terrainId={terrainId}
        farmId={terrain?.farmId}
        farmName={terrain?.farmName}
        terrainName={terrain?.name}
        areaHa={terrain?.areaHectares}
      />


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
              Nuevo Potrero
            </h3>
            <div className="form-group mb-16">
              <label>Nombre del Potrero</label>
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

                {!drawnInside && (
                  <div style={{
                    padding: '10px 14px',
                    background: 'rgba(239, 68, 68, 0.08)',
                    border: '1px solid rgba(239, 68, 68, 0.35)',
                    borderRadius: 'var(--radius-sm)',
                    marginBottom: 12,
                    color: 'var(--color-danger)',
                    fontSize: 12.5,
                    lineHeight: 1.5,
                    display: 'flex',
                    gap: 8,
                  }}>
                    <Info size={14} strokeWidth={2} style={{ flexShrink: 0, marginTop: 2 }} />
                    <span>
                      El potrero se sale del terreno. Dibuja de nuevo para poder guardarlo.
                    </span>
                  </div>
                )}

                <button
                  className="comp-btn comp-btn--primary"
                  style={{ width: '100%', justifyContent: 'center' }}
                  onClick={handleSaveParcel}
                  disabled={saving || !drawnInside}
                >
                  {saving
                    ? <><Loader2 size={14} className="oh-spin" style={{ animation: 'oh-spin 0.7s linear infinite' }} /> Guardando...</>
                    : <><CheckCircle2 size={14} strokeWidth={1.9} /> Guardar potrero</>}
                </button>
              </>
            )}
            {!drawnParcel && (
              <div style={{
                padding: '14px 16px',
                background: 'var(--color-bg)',
                border: '1px solid var(--color-border)',
                borderRadius: 'var(--radius-sm)',
                fontSize: 12.5,
                color: 'var(--color-text-secondary)',
                lineHeight: 1.65,
                display: 'flex',
                gap: 10,
              }}>
                <Info size={15} strokeWidth={1.9} style={{ color: 'var(--color-primary)', flexShrink: 0, marginTop: 2 }} />
                <div>
                  <strong style={{ color: 'var(--color-text)', display: 'block', marginBottom: 4 }}>
                    Cómo dibujar un potrero
                  </strong>
                  Usa el icono de polígono en el mapa, haz clic para definir vértices y cierra el polígono tocando el primer punto. El área se calcula automáticamente.
                </div>
              </div>
            )}
          </div>

          {parcels.length > 0 && (
            <div className="card">
              <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 12 }}>
                Potreros Existentes ({parcels.length})
              </h3>
              <div style={{ fontSize: 13, marginBottom: 16, color: 'var(--color-text-secondary)' }}>
                <strong>Cobertura:</strong> {coveragePercent.toFixed(1)}% del Terreno
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
                    className="comp-btn comp-btn--sm comp-btn--outline"
                    onClick={(e) => {
                      e.stopPropagation()
                      if (!farm?.iotEnabled) {
                        toast.error('IoT deshabilitado para esta finca')
                        return
                      }
                      navigate(`/parcels/${p.id}/sensors`)
                    }}
                    disabled={!farm?.iotEnabled}
                    title={farm?.iotEnabled ? 'Ver sensores' : 'IoT deshabilitado'}
                  >
                    <Cpu size={12} strokeWidth={1.9} />
                    {farm?.iotEnabled ? 'Sensores' : 'IoT off'}
                  </button>
                  <button
                    className="comp-btn comp-btn--sm comp-btn--icon-only"
                    onClick={(e) => {
                      e.stopPropagation()
                      handleDeleteParcel(p.id)
                    }}
                    title="Eliminar potrero"
                    aria-label="Eliminar potrero"
                    style={{ color: 'var(--color-danger)' }}
                  >
                    <Trash2 size={13} strokeWidth={1.9} />
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