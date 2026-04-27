import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { EditControl } from 'react-leaflet-draw'
import { FeatureGroup, GeoJSON, MapContainer, TileLayer, useMap } from 'react-leaflet'
import * as turf from '@turf/turf'
import L from 'leaflet'
import toast from 'react-hot-toast'

import Spinner from '../components/Spinner'
import api, { parcelApi, terrainApi } from '../services/api'

const SOIL_TYPES = ['Arenosa', 'Limosa', 'Arcillosa', 'Franco Arcillosa']
const PASTURE_TYPES = ['Brachiaria humidicola']

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

export default function EditParcelPage() {
  const { parcelId } = useParams()
  const navigate = useNavigate()

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [parcel, setParcel] = useState(null)
  const [terrain, setTerrain] = useState(null)
  const [farm, setFarm] = useState(null)
  const [siblings, setSiblings] = useState([])
  const [parcelName, setParcelName] = useState('')
  const [drawnParcel, setDrawnParcel] = useState(null)
  const [drawnArea, setDrawnArea] = useState({ sqm: 0, ha: 0 })
  const [soilType, setSoilType] = useState('')
  const [pastureType, setPastureType] = useState('')
  const featureGroupRef = useRef(null)

  useEffect(() => {
    const featureGroup = featureGroupRef.current
    if (!featureGroup) return

    featureGroup.clearLayers()

    if (!drawnParcel) return

    const editableLayer = L.geoJSON(drawnParcel, {
      style: { color: '#f59e0b', weight: 3, fillOpacity: 0.3 },
    })

    editableLayer.eachLayer((layer) => {
      featureGroup.addLayer(layer)
    })
  }, [drawnParcel])

  useEffect(() => {
    const loadData = async () => {
      try {
        setLoading(true)
        const parcelData = await parcelApi.getById(parcelId)
        const terrainData = await terrainApi.getById(parcelData.terrainId)
        const [farmRes, terrainParcels] = await Promise.all([
          api.get(`/farms/${terrainData.farmId}`),
          parcelApi.getByTerrain(parcelData.terrainId),
        ])

        setParcel(parcelData)
        setTerrain(terrainData)
        setFarm(farmRes.data)
        setSiblings(terrainParcels.filter(item => item.id !== parcelData.id))
        setParcelName(parcelData.name || '')
        setSoilType(parcelData.soilType || '')
        setPastureType(parcelData.pastureType || '')

        const parsedGeo = JSON.parse(parcelData.geoJson)
        const areaSqMeters = parcelData.areaSqMeters || turf.area(parsedGeo)
        setDrawnParcel(parsedGeo)
        setDrawnArea({ sqm: areaSqMeters, ha: parcelData.areaHectares || areaSqMeters / 10000 })
      } catch (err) {
        toast.error('Error cargando potrero')
        navigate('/farms')
      } finally {
        setLoading(false)
      }
    }

    loadData()
  }, [navigate, parcelId])

  function handleCreated(e) {
    const geoJson = e.layer.toGeoJSON()
    const area = turf.area(geoJson)
    setDrawnParcel(geoJson)
    setDrawnArea({ sqm: area, ha: area / 10000 })
  }

  function handleEdited(e) {
    e.layers.eachLayer((layer) => {
      const geoJson = layer.toGeoJSON()
      const area = turf.area(geoJson)
      setDrawnParcel(geoJson)
      setDrawnArea({ sqm: area, ha: area / 10000 })
    })
  }

  function handleDeleted() {
    setDrawnParcel(null)
    setDrawnArea({ sqm: 0, ha: 0 })
  }

  async function handleSave() {
    const liveGeoJson = getEditableGeoJson(featureGroupRef.current)
    const geoJsonToSave = liveGeoJson || drawnParcel

    if (!geoJsonToSave) {
      toast.error('Dibuja o edita el potrero en el mapa')
      return
    }

    const areaSqMetersToSave = turf.area(geoJsonToSave)
    const areaHectaresToSave = areaSqMetersToSave / 10000

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
      await parcelApi.update(parcel.id, {
        name: parcelName.trim() || parcel.name,
        geoJson: JSON.stringify(geoJsonToSave),
        areaSqMeters: areaSqMetersToSave,
        areaHectares: areaHectaresToSave,
        soilType: farm?.isHomogeneous ? null : soilType,
        pastureType: farm?.isHomogeneous ? null : pastureType,
      })
      toast.success('Potrero actualizado')
      navigate(`/terrains/${terrain.id}/parcels`)
    } catch (err) {
      toast.error(err?.response?.data?.error || err?.response?.data?.message || 'Error actualizando potrero')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <Spinner page label="Cargando potrero..." />
  }

  const terrainGeoJson = (() => {
    try {
      return JSON.parse(terrain.geoJson)
    } catch {
      return null
    }
  })()

  return (
    <div className="page-container">
      <div className="page-header">
        <div className="breadcrumb">
          <Link to="/farms">Fincas</Link>
          <span>›</span>
          <Link to={`/farms/${terrain.farmId}`}>{terrain.farmName}</Link>
          <span>›</span>
          <Link to={`/terrains/${terrain.id}/parcels`}>{terrain.name}</Link>
          <span>›</span>
          <span>Editar Potrero</span>
        </div>
        <h2>Editar Potrero</h2>
        <p>{parcel?.name} — ajusta nombre y geometría sin modificar el terreno</p>
      </div>

      <div className="two-col">
        <div className="col-main">
          <div className="map-container">
            <MapContainer center={[4.6, -74.1]} zoom={15} style={{ height: '100%', width: '100%' }}>
              {terrainGeoJson && <FitBounds geoJson={terrainGeoJson} />}
              <TileLayer
                url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                attribution="Tiles © Esri"
                maxZoom={19}
              />

              {terrainGeoJson && (
                <GeoJSON
                  data={terrainGeoJson}
                  style={{ color: '#ffffff', weight: 3, fillOpacity: 0.05, dashArray: '8,4' }}
                />
              )}

              {siblings.map((item) => {
                try {
                  return (
                    <GeoJSON
                      key={item.id}
                      data={JSON.parse(item.geoJson)}
                      style={{ color: '#3b82f6', weight: 2, fillOpacity: 0.1, dashArray: '4,4' }}
                    />
                  )
                } catch {
                  return null
                }
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
                    rectangle: false,
                    circle: false,
                    circlemarker: false,
                    marker: false,
                    polyline: false,
                    polygon: drawnParcel ? false : {
                      allowIntersection: false,
                      shapeOptions: { color: '#f59e0b', weight: 3, fillOpacity: 0.3 },
                    },
                  }}
                />
              </FeatureGroup>
            </MapContainer>
          </div>
        </div>

        <div className="col-side">
          <div className="card" style={{ background: '#162616', borderColor: 'rgba(255,255,255,0.06)' }}>
            <h3 style={panelTitleStyle}>
              Datos del Potrero
            </h3>
            <div className="form-group mb-16">
              <label style={fieldLabelStyle}>Nombre del Potrero</label>
              <input
                style={{ color: '#edf6eb' }}
                value={parcelName}
                onChange={(e) => setParcelName(e.target.value)}
                placeholder="Ej: Potrero A"
              />
            </div>

            {farm && !farm.isHomogeneous && (
              <>
                <div className="form-group mb-16">
                  <label style={fieldLabelStyle}>Tipo de Suelo *</label>
                  <select style={{ color: '#edf6eb' }} value={soilType} onChange={(e) => setSoilType(e.target.value)}>
                    <option value="">Seleccionar...</option>
                    {SOIL_TYPES.map(soil => <option key={soil} value={soil}>{soil}</option>)}
                  </select>
                </div>
                <div className="form-group mb-16">
                  <label style={fieldLabelStyle}>Tipo de Pasto *</label>
                  <select style={{ color: '#edf6eb' }} value={pastureType} onChange={(e) => setPastureType(e.target.value)}>
                    <option value="">Seleccionar...</option>
                    {PASTURE_TYPES.map(pasture => <option key={pasture} value={pasture}>{pasture}</option>)}
                  </select>
                </div>
              </>
            )}

            {drawnParcel && (
              <div style={helperCardStyle}>
                <strong style={helperTitleStyle}>Área actual:</strong>
                <div style={{ fontSize: 14, fontWeight: 600 }}>
                  {drawnArea.ha.toFixed(2)} <span style={{ fontSize: 12 }}>ha</span>
                </div>
                <div style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>
                  {drawnArea.sqm.toLocaleString('es-CO', { maximumFractionDigits: 0 })} m²
                </div>
              </div>
            )}

            <div style={helperCardStyle}>
              <strong style={helperTitleStyle}>Regla:</strong>
              El potrero editado debe quedar completamente dentro del terreno.
            </div>

            <button className="action-btn action-btn--primary" style={{ width: '100%' }} onClick={handleSave} disabled={saving || !drawnParcel}>
              {saving ? <><span className="spinner" /> Guardando...</> : '✓ Guardar Cambios'}
            </button>
            <button className="action-btn mt-16" style={{ width: '100%' }} onClick={() => navigate(`/terrains/${terrain.id}/parcels`)}>
              ← Volver a Potreros
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}