import { useEffect, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import Spinner from '../components/Spinner'
import OperationProgress from '../components/OperationProgress'
import { useCalibration } from '../hooks'

export default function CalibrationNdviAlertPage() {
  const { terrainId } = useParams()
  const navigate = useNavigate()
  const { status, loading, calibrating, calibrate, scenes, searchingScenes, searchScenes } = useCalibration(terrainId, 'ALERT')
  const { status: optimStatus, loading: optimLoading } = useCalibration(terrainId, 'OPTIM')

  const today = new Date().toISOString().slice(0, 10)
  const [calibrationDate, setCalibrationDate] = useState('')
  const [selectedScene, setSelectedScene] = useState(null)

  const handleSearchScenes = async () => {
    setSelectedScene(null)
    await searchScenes(calibrationDate)
  }

  const handleCalibrate = () => {
    calibrate(calibrationDate, selectedScene)
  }

  useEffect(() => {
    if (!optimLoading && optimStatus && !optimStatus.calibrated) {
      navigate(`/terrains/${terrainId}/ndvi/calibration-optim`, { replace: true })
    }
  }, [optimLoading, optimStatus, terrainId, navigate])

  if (loading || optimLoading) {
    return <Spinner page label="Verificando calibración umbral de alerta..." />
  }

  const isCalibrated = status?.calibrated

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div className="breadcrumb">
          <Link to="/farms">Fincas</Link>
          <span>›</span>
          <Link to={`/terrains/${terrainId}/parcels`}>Terreno</Link>
          <span>›</span>
          <span>Calibración Umbral Alerta</span>
        </div>
        <div className="flex justify-between items-center">
          <div>
            <h2> Calibración NDVI — Umbral de Alerta</h2>
            <p>
              Se promedia el NDVI de todos los potreros del terreno para obtener un solo umbral de alerta.
            </p>
          </div>
        </div>
        <OperationProgress
          active={searchingScenes || calibrating}
          title={searchingScenes ? 'Buscando imagenes satelitales' : 'Calibracion umbral de alerta en curso'}
          expectedSeconds={searchingScenes ? 18 : 180}
          hint={!searchingScenes ? 'La calibracion procesa una imagen Sentinel. Puede tardar entre 2 y 4 minutos.' : undefined}
        />
      </div>

      {/* Already calibrated — show results + continue button */}
      {isCalibrated ? (
        <div>
          <div className="card mb-24" style={{ borderLeft: '4px solid #ef4444' }}>
            <div className="card-header">
              <h3>Umbral de Alerta Configurado</h3>
            </div>
            <p style={{ color: 'var(--color-text-secondary)', marginBottom: 16 }}>
              Este terreno ya tiene un umbral de alerta NDVI calibrado. Los valores de abajo se usan como
              la línea de «Alerta» en las gráficas de análisis.
            </p>

            <div className="ndvi-parcel-grid">
              {status.calibrations?.map(cal => (
                <div key={cal.id} className="ndvi-parcel-card" style={{ borderLeftColor: '#ef4444' }}>
                  <div className="flex justify-between items-center">
                    <h4>{cal.parcelName || 'Terreno (global)'}</h4>
                    <span style={{ fontSize: 11, color: 'var(--color-text-muted)' }}>
                      {cal.source} · {cal.calibrationDate}
                    </span>
                  </div>
                  <div className="ndvi-parcel-stats">
                    <div>
                      <span className="label">NDVI Umbral</span>
                      <span className="value" style={{ color: '#ef4444' }}>
                        {cal.referenceNdvi?.toFixed(3) || '—'}
                      </span>
                    </div>
                    <div>
                      <span className="label">Pasto</span>
                      <span className="value">{cal.pastureType || '—'}</span>
                    </div>
                  </div>
                  {cal.sceneId && (
                    <div style={{ fontSize: 11, color: 'var(--color-text-muted)', marginTop: 8 }}>
                      Escena: {cal.sceneId} · {cal.pixelCount} píxeles
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Re-calibration form */}
          <div className="card mb-24">
            <div className="card-header">
              <h3>Recalibrar con otra fecha</h3>
            </div>
            <p style={{ color: 'var(--color-text-secondary)', marginBottom: 16 }}>
              Si deseas ajustar el umbral de alerta, selecciona una nueva fecha y busca las imágenes disponibles.
            </p>
            <div className="ndvi-analysis-controls" style={{ justifyContent: 'flex-start' }}>
              <div className="ndvi-date-field">
                <label htmlFor="calibrationDate" className="ndvi-date-label">
                  Nueva fecha de calibración
                </label>
                <input
                  className="ndvi-date-input"
                  id="calibrationDate"
                  type="date"
                  value={calibrationDate}
                  max={today}
                  onChange={(e) => { setCalibrationDate(e.target.value); setSelectedScene(null) }}
                  disabled={calibrating || searchingScenes}
                />
                <p className="ndvi-date-hint">Se buscarán imágenes en un rango de ±2 días.</p>
              </div>
              <button
                className="action-btn action-btn--primary"
                onClick={handleSearchScenes}
                disabled={searchingScenes || !calibrationDate || calibrating}
                style={{ alignSelf: 'flex-end', marginBottom: 24 }}
              >
                {searchingScenes
                  ? <><span className="spinner" /> Buscando...</>
                  : ' Buscar Imágenes'}
              </button>
            </div>

            {/* Scene picker */}
            {scenes.length > 0 && (
              <div style={{ marginTop: 16 }}>
                <h4 style={{ marginBottom: 12, fontFamily: 'var(--font-display)' }}>
                  Imágenes disponibles ({scenes.length})
                </h4>
                <div style={{ display: 'grid', gap: 8 }}>
                  {scenes.map(scene => (
                    <label
                      key={scene.sceneId}
                      style={{
                        display: 'flex', alignItems: 'center', gap: 12,
                        padding: '12px 16px', borderRadius: 8,
                        border: selectedScene === scene.sceneId ? '2px solid #ef4444' : '1px solid var(--color-border)',
                        background: selectedScene === scene.sceneId ? 'rgba(239,68,68,0.08)' : 'var(--color-bg-card)',
                        cursor: 'pointer', transition: 'all 0.15s'
                      }}
                    >
                      <input
                        type="radio"
                        name="scene"
                        value={scene.sceneId}
                        checked={selectedScene === scene.sceneId}
                        onChange={() => setSelectedScene(scene.sceneId)}
                      />
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 600, fontSize: 13 }}>{scene.sceneId}</div>
                        <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginTop: 2 }}>
                           {scene.date} &nbsp;·&nbsp; Nubosidad: <strong style={{ color: scene.cloudCoverPercent > 20 ? '#f59e0b' : '#ef4444' }}>{scene.cloudCoverPercent?.toFixed(1)}%</strong> &nbsp;·&nbsp; {scene.source}
                        </div>
                      </div>
                    </label>
                  ))}
                </div>
                <button
                  className="action-btn action-btn--primary"
                  onClick={handleCalibrate}
                  disabled={calibrating || !selectedScene}
                  style={{ marginTop: 16 }}
                >
                  {calibrating
                    ? <><span className="spinner" /> Recalibrando...</>
                    : 'Recalibrar con imagen seleccionada'}
                </button>
              </div>
            )}
          </div>

          <div className="flex gap-12">
            <button
              className="action-btn action-btn--primary"
              onClick={() => navigate(`/terrains/${terrainId}/ndvi/calibration-biomass`)}
            >
              Continuar → Calibrar Biomasa
            </button>
            <button
              className="action-btn"
              onClick={() => navigate(`/terrains/${terrainId}/ndvi/calibration-optim`)}
            >
              ← Calibración Óptima
            </button>
          </div>
        </div>
      ) : (
        /* Not calibrated — show form */
        <div>
          <div className="card mb-24">
            <div className="card-header">
              <h3>Configurar Fecha de Umbral de Alerta</h3>
            </div>
            <p style={{ color: 'var(--color-text-secondary)', marginBottom: 20 }}>
              Ingresa una fecha en la que el terreno y sus potreros estaban en <strong>condiciones degradadas o de alerta</strong>.
              El sistema buscará imágenes satelitales disponibles de <strong>Sentinel y Planet</strong> (máx 30% nubosidad) cerca de esa fecha
              para que puedas elegir cuál utilizar.
            </p>

            <div className="ndvi-analysis-controls" style={{ justifyContent: 'flex-start' }}>
              <div className="ndvi-date-field">
                <label htmlFor="calibrationDate" className="ndvi-date-label">
                  Fecha de calibración
                </label>
                <input
                  className="ndvi-date-input"
                  id="calibrationDate"
                  type="date"
                  value={calibrationDate}
                  max={today}
                  onChange={(e) => { setCalibrationDate(e.target.value); setSelectedScene(null) }}
                  disabled={calibrating || searchingScenes}
                />
                <p className="ndvi-date-hint">Se buscarán imágenes en un rango de ±2 días.</p>
              </div>

              <button
                className="action-btn action-btn--primary"
                onClick={handleSearchScenes}
                disabled={searchingScenes || !calibrationDate || calibrating}
                style={{ alignSelf: 'flex-end', marginBottom: 24 }}
              >
                {searchingScenes
                  ? <><span className="spinner" /> Buscando...</>
                  : ' Buscar Imágenes Disponibles'}
              </button>
            </div>

            {/* Scene picker */}
            {scenes.length > 0 && (
              <div style={{ marginTop: 16 }}>
                <h4 style={{ marginBottom: 12, fontFamily: 'var(--font-display)' }}>
                  Imágenes disponibles ({scenes.length})
                </h4>
                <p style={{ color: 'var(--color-text-secondary)', fontSize: 13, marginBottom: 12 }}>
                  Selecciona la imagen que deseas usar para la calibración:
                </p>
                <div style={{ display: 'grid', gap: 8 }}>
                  {scenes.map(scene => (
                    <label
                      key={scene.sceneId}
                      style={{
                        display: 'flex', alignItems: 'center', gap: 12,
                        padding: '12px 16px', borderRadius: 8,
                        border: selectedScene === scene.sceneId ? '2px solid #ef4444' : '1px solid var(--color-border)',
                        background: selectedScene === scene.sceneId ? 'rgba(239,68,68,0.08)' : 'var(--color-bg-card)',
                        cursor: 'pointer', transition: 'all 0.15s'
                      }}
                    >
                      <input
                        type="radio"
                        name="scene"
                        value={scene.sceneId}
                        checked={selectedScene === scene.sceneId}
                        onChange={() => setSelectedScene(scene.sceneId)}
                      />
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 600, fontSize: 13 }}>{scene.sceneId}</div>
                        <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginTop: 2 }}>
                           {scene.date} &nbsp;·&nbsp; Nubosidad: <strong style={{ color: scene.cloudCoverPercent > 20 ? '#f59e0b' : '#ef4444' }}>{scene.cloudCoverPercent?.toFixed(1)}%</strong> &nbsp;·&nbsp; {scene.source}
                        </div>
                      </div>
                    </label>
                  ))}
                </div>
                <button
                  className="action-btn action-btn--primary"
                  onClick={handleCalibrate}
                  disabled={calibrating || !selectedScene}
                  style={{ marginTop: 16 }}
                >
                  {calibrating
                    ? <><span className="spinner" /> Calibrando...</>
                    : ' Ejecutar Calibración de Alerta'}
                </button>
              </div>
            )}
          </div>

          <div className="card mb-24" style={{ borderLeft: '4px solid #ef4444' }}>
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 12 }}>
              ¿Cómo funciona?
            </h3>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: 13, lineHeight: 1.7 }}>
              <p><strong>1.</strong> Seleccionas una fecha donde el pasto estaba en <strong>condiciones degradadas o de alerta</strong>.</p>
              <p><strong>2.</strong> El sistema busca imágenes disponibles en Sentinel y Planet (≤30% nubosidad).</p>
              <p><strong>3.</strong> Eliges la imagen que deseas utilizar según fecha y nubosidad.</p>
              <p><strong>4.</strong> Se calcula el NDVI promedio como <strong>umbral de alerta</strong>: cuando el NDVI caiga por debajo de este valor, el sistema genera una alerta automáticamente.</p>
            </div>
          </div>

          <button
            className="action-btn"
            onClick={() => navigate(`/terrains/${terrainId}/ndvi/calibration-optim`)}
          >
            ← Volver a Calibración Óptima
          </button>
        </div>
      )}
    </div>
  )
}
