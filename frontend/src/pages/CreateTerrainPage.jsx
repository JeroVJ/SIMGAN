import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { MapContainer, TileLayer, FeatureGroup, GeoJSON, useMap } from 'react-leaflet'
import { EditControl } from 'react-leaflet-draw'
import * as turf from '@turf/turf'
import { farmApi, terrainApi } from '../services/api'
import toast from 'react-hot-toast'
import L from 'leaflet'

// Fix Leaflet default marker icons
delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png'
})

// Component to recenter map when farm data loads
function MapCenter({ lat, lng }) {
  const map = useMap()
  useEffect(() => {
    if (lat && lng) {
      map.setView([lat, lng], 15)
    }
  }, [lat, lng, map])
  return null
}

export default function CreateTerrainPage() {
  const { farmId } = useParams()
  const navigate = useNavigate()

  const [farm, setFarm] = useState(null)
  const [existingTerrains, setExistingTerrains] = useState([])
  const [terrainName, setTerrainName] = useState('')
  const [drawnGeoJson, setDrawnGeoJson] = useState(null)
  const [areaSqM, setAreaSqM] = useState(0)
  const [areaHa, setAreaHa] = useState(0)
  const [loading, setLoading] = useState(false)
  const featureGroupRef = useRef(null)

  // Default center: Colombia (Córdoba region - cattle country)
  const [center, setCenter] = useState([8.75, -75.88])

  useEffect(() => {
    loadFarm()
    loadTerrains()
  }, [farmId])

  async function loadFarm() {
    try {
      const data = await farmApi.getById(farmId)
      setFarm(data)
      if (data.centerLat && data.centerLng) {
        setCenter([data.centerLat, data.centerLng])
      }
    } catch {
      toast.error('Finca no encontrada')
      navigate('/farms')
    }
  }

  async function loadTerrains() {
    try {
      const data = await terrainApi.getByFarm(farmId)
      setExistingTerrains(data)
    } catch {
      // ignore
    }
  }

  function handleCreated(e) {
    const layer = e.layer
    const geoJson = layer.toGeoJSON()

    // Calculate area with turf.js
    const areaM2 = turf.area(geoJson)
    const areaHectares = areaM2 / 10000

    setDrawnGeoJson(geoJson)
    setAreaSqM(areaM2)
    setAreaHa(areaHectares)
  }

  function handleEdited(e) {
    const layers = e.layers
    layers.eachLayer((layer) => {
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
    if (!drawnGeoJson) {
      toast.error('Dibuja el polígono del terreno en el mapa')
      return
    }

    setLoading(true)
    try {
      const payload = {
        name: terrainName.trim() || `Terreno ${existingTerrains.length + 1}`,
        farmId: parseInt(farmId),
        geoJson: JSON.stringify(drawnGeoJson),
        areaSqMeters: areaSqM,
        areaHectares: areaHa
      }

      const terrain = await terrainApi.create(payload)
      toast.success(`Terreno guardado: ${terrain.areaHectares?.toFixed(2)} ha`)
      navigate(`/terrains/${terrain.id}/parcels`)
    } catch (err) {
      toast.error('Error guardando terreno')
    } finally {
      setLoading(false)
    }
  }

  // Parcel colors for existing terrains
  const terrainColors = ['#4ade80', '#3b82f6', '#f59e0b', '#ef4444', '#a855f7']

  return (
    <div>
      <div className="page-header">
        <div className="breadcrumb">
          <a href="/farms">Mis Fincas</a>
          <span>›</span>
          <span>{farm?.name || '...'}</span>
          <span>›</span>
          <span>Crear Terreno</span>
        </div>
        <h2>Crear Terreno</h2>
        <p>Dibuja el contorno del terreno sobre la imagen satelital</p>
      </div>

      <div className="two-col">
        {/* Map Column */}
        <div className="col-main">
          <div className="map-container">
            <MapContainer center={center} zoom={15} style={{ height: '100%', width: '100%' }}>
              <MapCenter lat={center[0]} lng={center[1]} />

              {/* Esri World Imagery - Satellite tiles */}
              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                attribution="Tiles &copy; Esri"
                maxZoom={19}
              />

              {/* Labels overlay */}
              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"
                maxZoom={19}
              />

              {/* Existing terrains */}
              {existingTerrains.map((t, i) => {
                try {
                  const geo = JSON.parse(t.geoJson)
                  return (
                    <GeoJSON
                      key={t.id}
                      data={geo}
                      style={{
                        color: terrainColors[i % terrainColors.length],
                        weight: 2,
                        fillOpacity: 0.15,
                        dashArray: '5,5'
                      }}
                    />
                  )
                } catch {
                  return null
                }
              })}

              {/* Drawing controls */}
              <FeatureGroup ref={featureGroupRef}>
                <EditControl
                  position="topright"
                  onCreated={handleCreated}
                  onEdited={handleEdited}
                  onDeleted={handleDeleted}
                  draw={{
                    polygon: {
                      allowIntersection: false,
                      shapeOptions: {
                        color: '#4ade80',
                        weight: 3,
                        fillOpacity: 0.2
                      }
                    },
                    rectangle: false,
                    circle: false,
                    circlemarker: false,
                    marker: false,
                    polyline: false
                  }}
                />
              </FeatureGroup>
            </MapContainer>
          </div>

          {/* Area display */}
          {drawnGeoJson && (
            <div className="area-display">
              <div className="area-stat">
                <span className="label">Área</span>
                <span className="value">
                  {areaHa.toFixed(2)}
                  <span className="unit">ha</span>
                </span>
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
          <div className="card">
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 20, marginBottom: 16 }}>
              Datos del Terreno
            </h3>

            <div className="form-group mb-16">
              <label>Nombre del Terreno</label>
              <input
                value={terrainName}
                onChange={(e) => setTerrainName(e.target.value)}
                placeholder="Ej: Potrero Norte"
              />
            </div>

            <div style={{ padding: '12px 16px', background: 'var(--color-bg)', borderRadius: 'var(--radius-sm)', fontSize: 13, color: 'var(--color-text-secondary)', marginBottom: 16 }}>
              <strong style={{ color: 'var(--color-text)' }}>Instrucciones:</strong><br />
              1. Usa el icono de polígono (▭) en el mapa<br />
              2. Haz clic para definir cada vértice<br />
              3. Cierra el polígono haciendo clic en el primer punto<br />
              4. El área se calcula automáticamente
            </div>

            <button
              className="btn btn-primary"
              style={{ width: '100%' }}
              onClick={handleSave}
              disabled={!drawnGeoJson || loading}
            >
              {loading ? <><span className="spinner" /> Guardando...</> : '✓ Guardar Terreno'}
            </button>

            <button
              className="btn btn-secondary mt-16"
              style={{ width: '100%' }}
              onClick={() => navigate('/farms')}
            >
              ← Volver a Fincas
            </button>
          </div>

          {/* Existing terrains */}
          {existingTerrains.length > 0 && (
            <div className="card">
              <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 12 }}>
                Terrenos Existentes
              </h3>
              {existingTerrains.map((t, i) => (
                <div
                  key={t.id}
                  className="parcel-item"
                  style={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/terrains/${t.id}/parcels`)}
                >
                  <div className="info">
                    <span className="name" style={{ color: terrainColors[i % terrainColors.length] }}>
                      {t.name || `Terreno ${t.id}`}
                    </span>
                    <span className="area">{t.areaHectares?.toFixed(2)} ha</span>
                  </div>
                  <span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>
                    {t.parcelCount} parcela{t.parcelCount !== 1 ? 's' : ''}
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
