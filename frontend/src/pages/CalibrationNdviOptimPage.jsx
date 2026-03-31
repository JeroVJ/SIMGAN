import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import Spinner from '../components/Spinner'
import { useCalibration } from '../hooks'
import { getHealthColor } from '../utils/ndvi'

export default function CalibrationNdviOptimPage() {
  const { terrainId } = useParams()
  const navigate = useNavigate()
  const { status, loading, calibrating, calibrate } = useCalibration(terrainId, 'OPTIM')

  const today = new Date().toISOString().slice(0, 10)
  const [calibrationDate, setCalibrationDate] = useState('')

  if (loading) return <Spinner page label="Verificando calibración NDVI óptima..." />

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
          <span>Calibración NDVI Óptimo</span>
        </div>
        <div className="flex justify-between items-center">
          <div>
            <h2> Calibración NDVI — Valor Óptimo</h2>
            <p>
              {status?.homogeneous
                ? 'Finca homogénea — se calibra todo el terreno con una sola referencia.'
                : `Finca no homogénea — calibración por potrero (${status?.calibratedParcels || 0}/${status?.totalParcels || 0} calibrados).`}
            </p>
          </div>
        </div>
      </div>

      {/* Already calibrated — show results + continue button */}
      {isCalibrated ? (
        <div>
          <div className="card mb-24" style={{ borderLeft: '4px solid #4ade80' }}>
            <div className="card-header">
              <h3>Calibración Óptima Completa</h3>
            </div>
            <p style={{ color: 'var(--color-text-secondary)', marginBottom: 16 }}>
              Este terreno ya tiene una referencia NDVI óptima. Los valores de abajo se usan como
              la línea de «Óptimo» en las gráficas de análisis.
            </p>

            <div className="ndvi-parcel-grid">
              {status.calibrations?.map(cal => (
                <div key={cal.id} className="ndvi-parcel-card" style={{ borderLeftColor: '#4ade80' }}>
                  <div className="flex justify-between items-center">
                    <h4>{cal.parcelName || 'Terreno (global)'}</h4>
                    <span style={{ fontSize: 11, color: 'var(--color-text-muted)' }}>
                      {cal.source} · {cal.calibrationDate}
                    </span>
                  </div>
                  <div className="ndvi-parcel-stats">
                    <div>
                      <span className="label">NDVI Óptimo</span>
                      <span className="value" style={{ color: getHealthColor(cal.referenceNdvi) }}>
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
              Si deseas ajustar el valor óptimo, selecciona una nueva fecha y vuelve a ejecutar la calibración.
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
                  : 'Recalibrar Óptimo'}
              </button>
            </div>
          </div>

          <div className="flex gap-12">
            <button
              className="action-btn action-btn--primary"
              onClick={() => navigate(`/terrains/${terrainId}/ndvi/calibration-alert`)}
            >
              Continuar → Calibrar Umbral de Alerta
            </button>
            <button
              className="action-btn action-btn--primary"
              onClick={() => navigate(`/terrains/${terrainId}/ndvi/calibration-biomass`)}
              style={{ background: '#16a34a' }}
            >
              Continuar → Calibrar Biomasa
            </button>
            <button
              className="action-btn"
              onClick={() => navigate(`/terrains/${terrainId}/parcels`)}
            >
              ← Potreros
            </button>
          </div>
        </div>
      ) : (
        /* Not calibrated — show form */
        <div>
          <div className="card mb-24">
            <div className="card-header">
              <h3>Configurar Fecha de Calibración Óptima</h3>
            </div>
            <p style={{ color: 'var(--color-text-secondary)', marginBottom: 20 }}>
              Ingresa una fecha en la que el terreno y sus potreros estaban en <strong>buenas condiciones</strong>.
              El sistema buscará imágenes satelitales disponibles (máx 20% nubosidad) cerca de esa fecha
              y calculará el NDVI de referencia óptimo.
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
                  : ' Ejecutar Calibración Óptima'}
              </button>
            </div>
          </div>

          <div className="card mb-24" style={{ borderLeft: '4px solid #3b82f6' }}>
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginBottom: 12 }}>
              ¿Cómo funciona?
            </h3>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: 13, lineHeight: 1.7 }}>
              <p><strong>1.</strong> Seleccionas una fecha donde el pasto estaba en su <strong>mejor estado</strong>.</p>
              <p><strong>2.</strong> El sistema busca imágenes Sentinel-2 con poca nubosidad (≤20%).</p>
              <p><strong>3.</strong> Calcula el NDVI promedio para cada potrero.</p>
              <p><strong>4.</strong> Ese valor se guarda como <strong>referencia óptima</strong> (línea verde en la gráfica).</p>
              {!status?.homogeneous && (
                <p style={{ marginTop: 8, color: '#f59e0b' }}>
                   Como tu finca no es homogénea, se calibrará cada potrero individualmente.
                  Los potreros con el mismo tipo de pasto compartirán la calibración.
                </p>
              )}
            </div>
          </div>

          <button
            className="action-btn"
            onClick={() => navigate(`/terrains/${terrainId}/parcels`)}
          >
            ← Volver a Potreros
          </button>
        </div>
      )}
    </div>
  )
}
