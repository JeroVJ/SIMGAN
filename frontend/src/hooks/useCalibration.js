import { useState, useEffect, useCallback } from 'react'
import toast from 'react-hot-toast'
import { calibrationApi } from '../services/api'

export function useCalibration(terrainId, type = 'OPTIM') {
  const [status, setStatus]         = useState(null)
  const [loading, setLoading]       = useState(true)
  const [calibrating, setCalibrating] = useState(false)
  const [scenes, setScenes]         = useState([])
  const [searchingScenes, setSearchingScenes] = useState(false)

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

  const searchScenes = useCallback(async (calibrationDate) => {
    setSearchingScenes(true)
    setScenes([])
    try {
      const data = await calibrationApi.searchScenes(terrainId, calibrationDate)
      setScenes(data)
      if (!data || data.length === 0) {
        toast.error('No se encontraron imágenes disponibles para esa fecha (±2 días)')
      }
      return data
    } catch (err) {
      const msg = err.response?.data?.error || err.message
      toast.error('Error buscando escenas: ' + msg)
      return []
    } finally {
      setSearchingScenes(false)
    }
  }, [terrainId])

  const calibrate = useCallback(async (calibrationDate, sceneId = null) => {
    setCalibrating(true)
    try {
      const result = await calibrationApi.runCalibration(terrainId, calibrationDate, type, sceneId)
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

  return { status, loading, calibrating, calibrate, reload: loadStatus, scenes, searchingScenes, searchScenes }
}
