import { useState, useEffect, useCallback } from 'react'
import toast from 'react-hot-toast'
import { calibrationApi } from '../services/api'

export function useCalibration(terrainId, type = 'OPTIM') {
  const [status, setStatus]         = useState(null)
  const [loading, setLoading]       = useState(true)
  const [calibrating, setCalibrating] = useState(false)

  const loadStatus = useCallback(async () => {
    if (!terrainId) return
    setLoading(true)
    try {
      const data = await calibrationApi.getStatus(terrainId, type)
      setStatus(data)
    } catch {
      toast.error('Error cargando estado de calibración')
    } finally {
      setLoading(false)
    }
  }, [terrainId, type])

  useEffect(() => {
    loadStatus()
  }, [loadStatus])

  const calibrate = useCallback(async (calibrationDate) => {
    setCalibrating(true)
    try {
      const result = await calibrationApi.runCalibration(terrainId, calibrationDate, type)
      if (result.error) {
        toast.error(result.error)
      } else {
        toast.success(result.message || 'Calibración completada')
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
  }, [terrainId, type, loadStatus])

  return { status, loading, calibrating, calibrate, reload: loadStatus }
}
