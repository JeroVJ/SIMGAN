import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { MapContainer, TileLayer, FeatureGroup, GeoJSON, useMap } from 'react-leaflet'
import { EditControl } from 'react-leaflet-draw'
import * as turf from '@turf/turf'
import { farmApi, terrainApi } from '../services/api'
import { useFarm } from '../hooks'
import toast from 'react-hot-toast'
import L from 'leaflet'

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

export default function CreateTerrainPage() {
  const { farmId } = useParams()
  const navigate = useNavigate()
  const { farm, terrains: existingTerrains } = useFarm(farmId)

  const [terrainName, setTerrainName] = useState('')
  const [drawnGeoJson, setDrawnGeoJson] = useState(null)
  const [areaSqM, setAreaSqM] = useState(0)
  const [areaHa, setAreaHa] = useState(0)
  const [saving, setSaving] = useState(false)
  const featureGroupRef = useRef(null)

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
    if (!drawnGeoJson) { toast.error('Dibuja el polígono del terreno en el mapa'); return }
    setSaving(true)
    try {
      const terrain = await terrainApi.create({
        name: terrainName.trim() || `Terreno ${existingTerrains.length + 1}`,
        farmId: parseInt(farmId),
        geoJson: JSON.stringify(drawnGeoJson),
        areaSqMeters: areaSqM,
        areaHectares: areaHa,
      })
      toast.success(`Terreno guardado: ${terrain.areaHectares?.toFixed(2)} ha`)
      navigate(`/terrains/${terrain.id}/parcels`)
    } catch {
      toast.error('Error guardando terreno')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div className="breadcrumb">
          <Link to="/farms">Mis Fincas</Link>
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
              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                attribution="Tiles &copy; Esri" maxZoom={19}
              />
              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"
                maxZoom={19}
              />
              {existingTerrains.map((t, i) => {
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
                  draw={{
                    polygon: { allowIntersection: false, shapeOptions: { color: '#4ade80', weight: 3, fillOpacity: 0.2 } },
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
          <div className="card">
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 20, marginBottom: 16 }}>
              Datos del Terreno
            </h3>
            <div className="form-group mb-16">
              <label>Nombre del Terreno</label>
              <input
                value={terrainName}
                onChange={(e) => setTerrainName(e.target.value)}
                placeholder="Ej: Terreno Norte"
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
              className="action-btn action-btn--primary"
              style={{ width: '100%' }}
              onClick={handleSave}
              disabled={!drawnGeoJson || saving}
            >
              {saving ? <><span className="spinner" /> Guardando...</> : '✓ Guardar Terreno'}
            </button>
            <button className="action-btn mt-16" style={{ width: '100%' }} onClick={() => navigate('/farms')}>
              ← Volver a Fincas
            </button>
          </div>

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
