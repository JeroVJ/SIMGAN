import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import Spinner from '../components/Spinner'
import { useCalibration } from '../hooks'
import { getHealthColor } from '../utils/ndvi'

export default function CalibrationNdviAlertPage() {
  const { terrainId } = useParams()
  const navigate = useNavigate()
  const { status, loading, calibrating, calibrate } = useCalibration(terrainId, 'ALERT')

  const today = new Date().toISOString().slice(0, 10)
  const [calibrationDate, setCalibrationDate] = useState('')

  if (loading) return <Spinner page label="Verificando calibración umbral de alerta..." />

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
              {status?.homogeneous
                ? 'Finca homogénea — se calibra todo el terreno con un solo umbral.'
                : `Finca no homogénea — calibración por potrero (${status?.calibratedParcels || 0}/${status?.totalParcels || 0} calibrados).`}
            </p>
          </div>
        </div>
      </div>

      {/* Already calibrated — show results + continue button */}
      {isCalibrated ? (
        <div>
          <div className="card mb-24" style={{ borderLeft: '4px solid #ef4444' }}>
            <div className="card-header">
              <h3> Umbral de Alerta Configurado</h3>
            </div>
            <p style={{ color: 'var(--color-text-secondary)', marginBottom: 16 }}>
              Este terreno ya tiene un umbral de alerta NDVI calibrado. Cuando el NDVI caiga
              por debajo de estos valores se generarán alertas.
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
              Si deseas ajustar el umbral de alerta, selecciona una nueva fecha y vuelve a ejecutar la calibración.
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
                  onChange={(e) => setCalibrationDate(e.target.value)}
                  disabled={calibrating}
                />
                <p className="ndvi-date-hint">Se buscarán imágenes en un rango de ±2 días.</p>
              </div>
              <button
                className="action-btn action-btn--primary"
                onClick={() => calibrate(calibrationDate)}
                disabled={calibrating || !calibrationDate}
                style={{ alignSelf: 'flex-end', marginBottom: 24 }}
              >
                {calibrating
                  ? <><span className="spinner" /> Recalibrando...</>
                  : 'Recalibrar Alerta'}
              </button>
            </div>
          </div>

          <div className="flex gap-12">
            <button
              className="action-btn action-btn--primary"
              onClick={() => navigate(`/terrains/${terrainId}/ndvi`)}
            >
              Ir al Dashboard NDVI →
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
              <h3> Configurar Fecha de Umbral de Alerta</h3>
            </div>
            <p style={{ color: 'var(--color-text-secondary)', marginBottom: 20 }}>
              Ingresa una fecha en la que el terreno y sus potreros estaban en <strong>condiciones deficientes o de alerta</strong>.
              El sistema buscará imágenes satelitales disponibles (máx 20% nubosidad) cerca de esa fecha
              y calculará el NDVI que se usará como umbral de alerta.
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
                  onChange={(e) => setCalibrationDate(e.target.value)}
                  disabled={calibrating}
                />
                <p className="ndvi-date-hint">Se buscarán imágenes en un rango de ±2 días.</p>
              </div>

              <button
                className="action-btn action-btn--primary"
                onClick={() => calibrate(calibrationDate)}
                disabled={calibrating || !calibrationDate}
                style={{ alignSelf: 'flex-end', marginBottom: 24 }}
              >
                {calibrating
                  ? <><span className="spinner" /> Calibrando...</>
                  : ' Ejecutar Calibración de Alerta'}
              </button>
            </div>
          </div>

          <div className="card mb-24" style={{ borderLeft: '4px solid #ef4444' }}>
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 12 }}>
              ¿Cómo funciona?
            </h3>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: 13, lineHeight: 1.7 }}>
              <p><strong>1.</strong> Seleccionas una fecha donde el pasto estaba en <strong>mal estado o condiciones críticas</strong>.</p>
              <p><strong>2.</strong> El sistema busca imágenes Sentinel-2 con poca nubosidad (≤20%).</p>
              <p><strong>3.</strong> Calcula el NDVI promedio para cada potrero en ese estado.</p>
              <p><strong>4.</strong> Ese valor se guarda como <strong>umbral de alerta</strong> (línea roja en la gráfica).</p>
              {!status?.homogeneous && (
                <p style={{ marginTop: 8, color: '#f59e0b' }}>
                   Como tu finca no es homogénea, se calibrará cada potrero individualmente.
                  Los potreros con el mismo tipo de pasto compartirán el umbral.
                </p>
              )}
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
