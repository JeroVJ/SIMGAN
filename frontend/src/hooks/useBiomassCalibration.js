import { useState, useEffect, useCallback } from 'react'
import toast from 'react-hot-toast'
import { biomassCalibrationApi } from '../services/api'

export function useBiomassCalibration(terrainId) {
  const [status, setStatus]         = useState(null)
  const [loading, setLoading]       = useState(true)
  const [calibrating, setCalibrating] = useState(false)

  const loadStatus = useCallback(async () => {
    if (!terrainId) return
    setLoading(true)
    try {
      const data = await biomassCalibrationApi.getStatus(terrainId)
      setStatus(data)
    } catch {
      toast.error('Error cargando estado de calibración de biomasa')
    } finally {
      setLoading(false)
    }
  }, [terrainId])

  useEffect(() => {
    loadStatus()
  }, [loadStatus])

  const calibrateParcel = useCallback(async (parcelId, points) => {
    setCalibrating(true)
    try {
      const result = await biomassCalibrationApi.calibrateParcel(terrainId, parcelId, points)
      if (result.error) {
        toast.error(result.error)
      } else {
        toast.success(result.message || 'Calibración de biomasa completada')
      }
      await loadStatus()
      return result
    } catch (err) {
      const msg = err.response?.data?.error || err.message
      toast.error('Error en calibración: ' + msg)
      return { error: msg }
    } finally {
      setCalibrating(false)
    }
  }, [terrainId, loadStatus])

  return { status, loading, calibrating, calibrateParcel, reload: loadStatus }
}
