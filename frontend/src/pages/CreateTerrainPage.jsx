import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { MapContainer, TileLayer, FeatureGroup, GeoJSON, useMap } from 'react-leaflet'
import { EditControl } from 'react-leaflet-draw'
import * as turf from '@turf/turf'
import { farmApi, terrainApi } from '../services/api'
import { useFarm } from '../hooks'
import toast from 'react-hot-toast'
import L from 'leaflet'
import Spinner from '../components/Spinner'

// Fix Leaflet default marker icons
delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
})

function MapCenter({ lat, lng }) {
  const map = useMap()
  useEffect(() => {
    if (lat && lng) map.setView([lat, lng], 15)
  }, [lat, lng, map])
  return null
}

const TERRAIN_COLORS = ['#4ade80', '#3b82f6', '#f59e0b', '#ef4444', '#a855f7']

const panelTitleStyle = {
  fontFamily: 'var(--font-body)',
  fontSize: 16,
  fontWeight: 700,
  lineHeight: 1.25,
  letterSpacing: '0',
  color: '#f3f7f1',
  marginBottom: 18,
}

const fieldLabelStyle = {
  display: 'block',
  marginBottom: 9,
  fontSize: 11,
  fontWeight: 700,
  letterSpacing: '0.12em',
  textTransform: 'uppercase',
  color: '#6f8d6f',
}

const helperCardStyle = {
  padding: '16px 18px',
  background: '#102114',
  border: '1px solid rgba(255,255,255,0.03)',
  borderRadius: 'var(--radius-sm)',
  fontSize: 13,
  color: '#9bb29a',
  marginBottom: 16,
  lineHeight: 1.65,
}

const helperTitleStyle = {
  color: '#f3f7f1',
  fontWeight: 700,
  display: 'block',
  marginBottom: 8,
}

function getEditableGeoJson(featureGroup) {
  if (!featureGroup) return null

  let editableGeoJson = null
  featureGroup.eachLayer((layer) => {
    if (!editableGeoJson && typeof layer.toGeoJSON === 'function') {
      editableGeoJson = layer.toGeoJSON()
    }
  })

  return editableGeoJson
}

export default function CreateTerrainPage() {
  const { farmId: routeFarmId, terrainId } = useParams()
  const navigate = useNavigate()
  const isEdit = Boolean(terrainId)

  const [currentTerrain, setCurrentTerrain] = useState(null)
  const [loadingTerrain, setLoadingTerrain] = useState(isEdit)
  const effectiveFarmId = routeFarmId || currentTerrain?.farmId
  const { farm, terrains: existingTerrains } = useFarm(effectiveFarmId)

  const [terrainName, setTerrainName] = useState('')
  const [drawnGeoJson, setDrawnGeoJson] = useState(null)
  const [areaSqM, setAreaSqM] = useState(0)
  const [areaHa, setAreaHa] = useState(0)
  const [saving, setSaving] = useState(false)
  const featureGroupRef = useRef(null)
  const initializedRef = useRef(false)

  useEffect(() => {
    const featureGroup = featureGroupRef.current
    if (!featureGroup) return

    featureGroup.clearLayers()

    if (!drawnGeoJson) return

    const editableLayer = L.geoJSON(drawnGeoJson, {
      style: { color: '#4ade80', weight: 3, fillOpacity: 0.2 },
    })

    editableLayer.eachLayer((layer) => {
      featureGroup.addLayer(layer)
    })
  }, [drawnGeoJson])

  useEffect(() => {
    if (!isEdit) return

    const loadTerrain = async () => {
      try {
        setLoadingTerrain(true)
        const data = await terrainApi.getById(terrainId)
        setCurrentTerrain(data)
      } catch (err) {
        toast.error('Error cargando terreno')
        navigate('/farms')
      } finally {
        setLoadingTerrain(false)
      }
    }

    loadTerrain()
  }, [isEdit, navigate, terrainId])

  useEffect(() => {
    if (!isEdit || !currentTerrain || initializedRef.current) return

    try {
      setTerrainName(currentTerrain.name || '')
      const parsedGeo = JSON.parse(currentTerrain.geoJson)
      setDrawnGeoJson(parsedGeo)
      setAreaSqM(currentTerrain.areaSqMeters || turf.area(parsedGeo))
      setAreaHa(currentTerrain.areaHectares || (turf.area(parsedGeo) / 10000))
      initializedRef.current = true
    } catch {
      toast.error('Error leyendo la geometría del terreno')
    }
  }, [currentTerrain, isEdit])

  const center = (farm?.centerLat && farm?.centerLng)
    ? [farm.centerLat, farm.centerLng]
    : [8.75, -75.88]

  function handleCreated(e) {
    const geoJson = e.layer.toGeoJSON()
    const areaM2 = turf.area(geoJson)
    setDrawnGeoJson(geoJson)
    setAreaSqM(areaM2)
    setAreaHa(areaM2 / 10000)
  }

  function handleEdited(e) {
    e.layers.eachLayer((layer) => {
      const geoJson = layer.toGeoJSON()
      const areaM2 = turf.area(geoJson)
      setDrawnGeoJson(geoJson)
      setAreaSqM(areaM2)
      setAreaHa(areaM2 / 10000)
    })
  }

  function handleDeleted() {
    setDrawnGeoJson(null)
    setAreaSqM(0)
    setAreaHa(0)
  }

  async function handleSave() {
    const liveGeoJson = getEditableGeoJson(featureGroupRef.current)
    const geoJsonToSave = liveGeoJson || drawnGeoJson

    if (!geoJsonToSave) { toast.error('Dibuja el polígono del terreno en el mapa'); return }

    const areaSqMetersToSave = turf.area(geoJsonToSave)
    const areaHectaresToSave = areaSqMetersToSave / 10000

    setSaving(true)
    try {
      const payload = {
        name: terrainName.trim() || `Terreno ${existingTerrains.length + 1}`,
        geoJson: JSON.stringify(geoJsonToSave),
        areaSqMeters: areaSqMetersToSave,
        areaHectares: areaHectaresToSave,
      }

      if (isEdit) {
        const updatedTerrain = await terrainApi.update(terrainId, payload)
        toast.success('Terreno actualizado')
        navigate(`/terrains/${updatedTerrain.id}/parcels`, {
          state: {
            terrainEdited: true,
            terrainName: updatedTerrain.name,
            outOfBoundsParcelIds: updatedTerrain.outOfBoundsParcelIds || [],
            outOfBoundsParcelNames: updatedTerrain.outOfBoundsParcelNames || [],
          },
        })
        return
      }

      const terrain = await terrainApi.create({
        ...payload,
        farmId: parseInt(routeFarmId),
      })
      toast.success(`Terreno guardado: ${terrain.areaHectares?.toFixed(2)} ha`)
      navigate(`/terrains/${terrain.id}/parcels`)
    } catch (err) {
      toast.error(err?.response?.data?.error || err?.response?.data?.message || 'Error guardando terreno')
    } finally {
      setSaving(false)
    }
  }

  if (loadingTerrain) {
    return <Spinner page label="Cargando terreno..." />
  }

  const visibleTerrains = isEdit
    ? existingTerrains.filter(t => t.id !== Number(terrainId))
    : existingTerrains

  return (
    <div>
      <div className="page-header">
        <div className="breadcrumb">
          <Link to="/farms">Mis Fincas</Link>
          <span>›</span>
          <span>{farm?.name || '...'}</span>
          <span>›</span>
          <span>{isEdit ? 'Editar Terreno' : 'Crear Terreno'}</span>
        </div>
        <h2>{isEdit ? 'Editar Terreno' : 'Crear Terreno'}</h2>
        <p>{isEdit ? 'Ajusta el contorno y luego revisa los potreros del terreno' : 'Dibuja el contorno del terreno sobre la imagen satelital'}</p>
      </div>

      <div className="two-col">
        {/* Map Column */}
        <div className="col-main">
          <div className="map-container">
            <MapContainer center={center} zoom={15} style={{ height: '100%', width: '100%' }}>
              <MapCenter lat={center[0]} lng={center[1]} />
              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                attribution="Tiles &copy; Esri" maxZoom={19}
              />
              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"
                maxZoom={19}
              />
              {visibleTerrains.map((t, i) => {
                try {
                  return (
                    <GeoJSON key={t.id} data={JSON.parse(t.geoJson)}
                      style={{ color: TERRAIN_COLORS[i % TERRAIN_COLORS.length], weight: 2, fillOpacity: 0.15, dashArray: '5,5' }} />
                  )
                } catch { return null }
              })}
              <FeatureGroup ref={featureGroupRef}>
                <EditControl
                  position="topright"
                  onCreated={handleCreated}
                  onEdited={handleEdited}
                  onDeleted={handleDeleted}
                  edit={{
                    remove: false,
                  }}
                  draw={{
                    polygon: drawnGeoJson ? false : { allowIntersection: false, shapeOptions: { color: '#4ade80', weight: 3, fillOpacity: 0.2 } },
                    rectangle: false, circle: false, circlemarker: false, marker: false, polyline: false,
                  }}
                />
              </FeatureGroup>
            </MapContainer>
          </div>

          {drawnGeoJson && (
            <div className="area-display">
              <div className="area-stat">
                <span className="label">Área</span>
                <span className="value">{areaHa.toFixed(2)}<span className="unit">ha</span></span>
              </div>
              <div className="area-stat">
                <span className="label">Metros²</span>
                <span className="value">
                  {areaSqM.toLocaleString('es-CO', { maximumFractionDigits: 0 })}
                  <span className="unit">m²</span>
                </span>
              </div>
            </div>
          )}
        </div>

        {/* Side Panel */}
        <div className="col-side">
          <div className="card" style={{ background: '#162616', borderColor: 'rgba(255,255,255,0.06)' }}>
            <h3 style={panelTitleStyle}>
              Datos del Terreno
            </h3>
            <div className="form-group mb-16">
              <label style={fieldLabelStyle}>Nombre del Terreno</label>
              <input
                style={{ color: '#edf6eb' }}
                value={terrainName}
                onChange={(e) => setTerrainName(e.target.value)}
                placeholder="Ej: Terreno Norte"
              />
            </div>
            <div style={helperCardStyle}>
              <strong style={helperTitleStyle}>Instrucciones:</strong>
              1. Usa el icono de polígono (▭) en el mapa<br />
              2. Haz clic para definir cada vértice<br />
              3. Cierra el polígono haciendo clic en el primer punto<br />
              4. El área se calcula automáticamente
            </div>
            <button
              className="action-btn action-btn--primary"
              style={{ width: '100%' }}
              onClick={handleSave}
              disabled={!drawnGeoJson || saving}
            >
              {saving ? <><span className="spinner" /> Guardando...</> : isEdit ? '✓ Guardar Cambios' : '✓ Guardar Terreno'}
            </button>
            <button className="action-btn mt-16" style={{ width: '100%' }} onClick={() => navigate(isEdit ? `/terrains/${terrainId}/parcels` : '/farms')}>
              ← {isEdit ? 'Volver a Potreros' : 'Volver a Fincas'}
            </button>
          </div>

          {visibleTerrains.length > 0 && (
            <div className="card">
              <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 12 }}>
                Terrenos Existentes
              </h3>
              {visibleTerrains.map((t, i) => (
                <div
                  key={t.id}
                  className="parcel-item"
                  style={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/terrains/${t.id}/parcels`)}
                >
                  <div className="info">
                    <span className="name" style={{ color: TERRAIN_COLORS[i % TERRAIN_COLORS.length] }}>
                      {t.name || `Terreno ${t.id}`}
                    </span>
                    <span className="area">{t.areaHectares?.toFixed(2)} ha</span>
                  </div>
                  <span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>
                    {t.parcelCount} potrero{t.parcelCount !== 1 ? 's' : ''}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
