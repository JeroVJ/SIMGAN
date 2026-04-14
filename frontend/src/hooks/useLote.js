import { useState, useEffect, useCallback } from 'react'
import toast from 'react-hot-toast'
import { loteApi } from '../services/api'

/**
 * useLote(loteId)
 *
 * Loads a single lote (with its ganado and parcel history) and exposes
 * CRUD actions for the individual animals inside it.
 *
 * Used by: LoteDetailPage
 *
 * Returns:
 *   lote            {object|null}
 *   loading         {boolean}
 *   error           {Error|null}
 *   reload          {Function}
 *
 *   Ganado actions:
 *     addGanado(data)           → POST single animal, reload
 *     addGanadoBatch(items)     → POST batch, reload
 *     updateGanado(id, data)    → PUT single animal, reload
 *     deleteGanado(id)          → DELETE single animal, reload
 */
export function useLote(loteId) {
  const [lote, setLote]       = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState(null)

  const reload = useCallback(async () => {
    if (!loteId) return
    setLoading(true)
    setError(null)
    try {
      const data = await loteApi.getById(loteId)
      setLote(data)
    } catch (err) {
      setError(err)
      toast.error('Error cargando lote')
    } finally {
      setLoading(false)
    }
  }, [loteId])

  useEffect(() => {
    reload()
  }, [reload])

  const addGanado = useCallback(
    async (data) => {
      try {
        await loteApi.addGanado(loteId, data)
        toast.success(`${data.numeracion} agregado`)
        await reload()
      } catch (err) {
        toast.error(err.response?.data?.message || 'Error agregando ganado')
        throw err
      }
    },
    [loteId, reload]
  )

  const addGanadoBatch = useCallback(
    async (items) => {
      try {
        await loteApi.addGanadoBatch(loteId, items)
        toast.success(`${items.length} animales agregados`)
        await reload()
      } catch (err) {
        toast.error(err.response?.data?.message || 'Error')
        throw err
      }
    },
    [loteId, reload]
  )

  const updateGanado = useCallback(
    async (ganadoId, data) => {
      try {
        await loteApi.updateGanado(ganadoId, data)
        toast.success('Actualizado')
        await reload()
      } catch {
        toast.error('Error actualizando')
        throw new Error('update failed')
      }
    },
    [reload]
  )

  const deleteGanado = useCallback(
    async (ganadoId) => {
      try {
        await loteApi.deleteGanado(ganadoId)
        toast.success('Eliminado')
        await reload()
      } catch {
        toast.error('Error')
        throw new Error('delete failed')
      }
    },
    [reload]
  )

  return {
    lote, loading, error, reload,
    addGanado, addGanadoBatch, updateGanado, deleteGanado,
  }
}
