import { useState, useEffect, useCallback, useMemo } from 'react'
import toast from 'react-hot-toast'
import { terrainApi, parcelApi, loteApi, ndviApi } from '../services/api'
import { estimateGrazingDays, buildGrazingAlerts } from '../utils/grazing'

/**
 * useTerrain(terrainId)
 *
 * Central hook for any page that works within a single terrain context.
 * Loads terrain details, parcels, lotes, and optional NDVI comparison data.
 * Derives enriched per-parcel info and grazing alerts from the raw data.
 * Exposes all parcel and lote mutation actions.
 *
 * Used by: ParcelsPage, RotationPage, LotesPage
 *
 * Returns:
 *   terrain        {object|null}
 *   parcels        {Array}
 *   lotes          {Array}
 *   ndviComparison {Array|null}   — raw NDVI comparison array (may be null if no data)
 *   parcelInfo     {object}       — { [parcelId]: { ndvi, biomass, lote, grazingDays } }
 *   grazingAlerts  {Array}        — pre-computed alerts for occupied parcels
 *   loading        {boolean}
 *   error          {Error|null}
 *   reload         {Function}
 *
 *   Parcel actions:
 *     addParcel(payload)                → creates parcel, refreshes parcels list
 *     updateParcelStatus(id, status)    → patch status; guards against locked parcels
 *     deleteParcel(id)                  → guards against locked parcels
 *
 *   Lote actions:
 *     createLote(payload)               → creates lote for this terrain
 *     assignParcel(loteId, parcelId)    → assign lote to parcel, full reload
 *     unassignParcel(loteId)            → remove lote from its parcel, full reload
 *     closeLote(loteId, data)           → close lote with exit date, full reload
 *     deleteLote(loteId)                → delete lote, refreshes lotes list
 */
export function useTerrain(terrainId) {
  const [terrain, setTerrain] = useState(null)
  const [parcels, setParcels] = useState([])
  const [lotes, setLotes] = useState([])
  const [ndviComparison, setNdviComparison] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // ── Data loading ───────────────────────────────────────────────────────────

  const reload = useCallback(async () => {
    if (!terrainId) return
    setLoading(true)
    setError(null)
    try {
      const [t, p, l] = await Promise.all([
        terrainApi.getById(terrainId),
        parcelApi.getByTerrain(terrainId),
        loteApi.getByTerrain(terrainId),
      ])
      setTerrain(t)
      setParcels(p)
      setLotes(l)
      // NDVI comparison is optional — silently ignore if unavailable
      try {
        setNdviComparison(await ndviApi.getComparison(terrainId))
      } catch {
        setNdviComparison(null)
      }
    } catch (err) {
      setError(err)
      toast.error('Error cargando terreno')
    } finally {
      setLoading(false)
    }
  }, [terrainId])

  useEffect(() => {
    reload()
  }, [reload])

  // ── Derived data ───────────────────────────────────────────────────────────

  /**
   * Enriched per-parcel information: merges NDVI data and the occupying lote
   * into a single lookup object.
   */
  const parcelInfo = useMemo(() => {
    const info = {}
    for (const p of parcels) {
      const ndviRow = Array.isArray(ndviComparison)
        ? ndviComparison.find(n => n.parcelId === p.id)
        : null
      const occupyingLote = lotes.find(
        l => l.currentParcelId === p.id && !l.fechaSalida
      )
      const biomass = ndviRow?.biomassKgPerHa ?? null
      info[p.id] = {
        ndvi:        ndviRow?.latestNdvi ?? ndviRow?.avgNdvi ?? null,
        biomass,
        lote:        occupyingLote ?? null,
        grazingDays: occupyingLote
          ? estimateGrazingDays(p, occupyingLote, biomass)
          : null,
      }
    }
    return info
  }, [parcels, ndviComparison, lotes])

  /** Pre-computed grazing alerts for all occupied parcels. */
  const grazingAlerts = useMemo(
    () => buildGrazingAlerts(parcels, parcelInfo),
    [parcels, parcelInfo]
  )

  // ── Parcel actions ─────────────────────────────────────────────────────────

  const addParcel = useCallback(
    async (payload) => {
      try {
        const parcel = await parcelApi.create(payload)
        toast.success('Parcela guardada')
        setParcels(await parcelApi.getByTerrain(terrainId))
        return parcel
      } catch (err) {
        toast.error('Error guardando parcela')
        throw err
      }
    },
    [terrainId]
  )

  const updateParcelStatus = useCallback(
    async (parcelId, status) => {
      const info = parcelInfo[parcelId]
      if (info?.lote) {
        toast.error(
          `No se puede cambiar: parcela en uso por "${info.lote.name}". Retire el lote primero.`
        )
        return
      }
      try {
        await parcelApi.updateStatus(parcelId, status)
        setParcels(await parcelApi.getByTerrain(terrainId))
        toast.success('Estado actualizado')
      } catch (err) {
        toast.error(err.response?.data?.message || 'Error actualizando estado')
        throw err
      }
    },
    [terrainId, parcelInfo]
  )

  const deleteParcel = useCallback(
    async (parcelId) => {
      const info = parcelInfo[parcelId]
      if (info?.lote) {
        toast.error(`No se puede eliminar: parcela en uso por "${info.lote.name}"`)
        return
      }
      try {
        await parcelApi.delete(parcelId)
        setParcels(await parcelApi.getByTerrain(terrainId))
        toast.success('Parcela eliminada')
      } catch (err) {
        toast.error('Error eliminando parcela')
        throw err
      }
    },
    [terrainId, parcelInfo]
  )

  // ── Lote actions ───────────────────────────────────────────────────────────

  const createLote = useCallback(
    async (payload) => {
      try {
        const lote = await loteApi.create({
          ...payload,
          terrainId: Number(terrainId),
        })
        toast.success('Lote creado')
        setLotes(await loteApi.getByTerrain(terrainId))
        return lote
      } catch (err) {
        toast.error(err.response?.data?.message || 'Error creando lote')
        throw err
      }
    },
    [terrainId]
  )

  const assignParcel = useCallback(
    async (loteId, parcelId) => {
      try {
        await loteApi.assignParcel(loteId, parcelId)
        toast.success('Parcela asignada')
        await reload()
      } catch (err) {
        toast.error(err.response?.data?.message || 'Error asignando parcela')
        throw err
      }
    },
    [reload]
  )

  const unassignParcel = useCallback(
    async (loteId) => {
      try {
        await loteApi.unassignParcel(loteId)
        toast.success('Lote retirado')
        await reload()
      } catch (err) {
        toast.error(err.response?.data?.message || 'Error retirando lote')
        throw err
      }
    },
    [reload]
  )

  const closeLote = useCallback(
    async (loteId, data) => {
      try {
        await loteApi.close(loteId, data)
        toast.success('Lote cerrado')
        await reload()
      } catch (err) {
        toast.error(err.response?.data?.message || 'Error cerrando lote')
        throw err
      }
    },
    [reload]
  )

  const deleteLote = useCallback(
    async (loteId) => {
      try {
        await loteApi.delete(loteId)
        toast.success('Eliminado')
        setLotes(await loteApi.getByTerrain(terrainId))
      } catch (err) {
        toast.error('Error eliminando lote')
        throw err
      }
    },
    [terrainId]
  )

  return {
    // Raw data
    terrain, parcels, lotes, ndviComparison,
    // Derived
    parcelInfo, grazingAlerts,
    // Meta
    loading, error, reload,
    // Parcel mutations
    addParcel, updateParcelStatus, deleteParcel,
    // Lote mutations
    createLote, assignParcel, unassignParcel, closeLote, deleteLote,
  }
}
