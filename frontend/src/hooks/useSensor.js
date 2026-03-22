import { useState, useEffect, useCallback } from 'react'
import toast from 'react-hot-toast'
import { sensorApi } from '../services/api'

/**
 * useSensor(parcelId)
 *
 * Hook to manage sensors on a parcel.
 * Loads sensors, creates new sensors, updates and deletes them.
 *
 * Returns:
 *   sensors        {Array}
 *   loading        {boolean}
 *   error          {Error|null}
 *   reload         {Function}
 *
 *   Sensor actions:
 *     addSensor(payload)     → creates sensor for parcel
 *     updateSensor(id, data) → updates sensor data
 *     deleteSensor(id)       → deletes sensor
 */
export function useSensor(parcelId, { enabled = true } = {}) {
  const [sensors, setSensors] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // ── Data loading ───────────────────────────────────────────────────────────

  const reload = useCallback(async () => {
    if (!parcelId || !enabled) {
      setSensors([])
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    try {
      const data = await sensorApi.getByParcel(parcelId)
      setSensors(data)
    } catch (err) {
      setError(err)
      toast.error('Error cargando sensores')
    } finally {
      setLoading(false)
    }
  }, [parcelId, enabled])

  useEffect(() => {
    reload()
  }, [reload])

  // ── Sensor actions ─────────────────────────────────────────────────────────

  const addSensor = useCallback(
    async (payload) => {
      if (!enabled) {
        throw new Error('IoT deshabilitado para esta finca')
      }
      try {
        const sensor = await sensorApi.create({
          ...payload,
          parcelId: Number(parcelId),
        })
        toast.success('Sensor añadido')
        await reload()
        return sensor
      } catch (err) {
        toast.error(err.response?.data?.message || 'Error creando sensor')
        throw err
      }
    },
    [enabled, parcelId, reload]
  )

  const updateSensor = useCallback(
    async (sensorId, data) => {
      if (!enabled) {
        throw new Error('IoT deshabilitado para esta finca')
      }
      try {
        const updated = await sensorApi.update(sensorId, data)
        toast.success('Sensor actualizado')
        await reload()
        return updated
      } catch (err) {
        toast.error('Error actualizando sensor')
        throw err
      }
    },
    [enabled, reload]
  )

  const deleteSensor = useCallback(
    async (sensorId) => {
      if (!enabled) {
        throw new Error('IoT deshabilitado para esta finca')
      }
      try {
        await sensorApi.delete(sensorId)
        toast.success('Sensor eliminado')
        await reload()
      } catch (err) {
        toast.error('Error eliminando sensor')
        throw err
      }
    },
    [enabled, reload]
  )

  return {
    sensors,
    loading,
    error,
    reload,
    addSensor,
    updateSensor,
    deleteSensor,
  }
}
