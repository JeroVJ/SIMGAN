import { useState, useEffect, useCallback, useMemo } from 'react'
import toast from 'react-hot-toast'
import { farmApi, terrainApi, parcelApi, loteApi, ndviApi } from '../services/api'

/**
 * useFarm(farmId)
 *
 * Loads a single farm together with all its terrains and per-terrain aggregated
 * data (parcels, lotes, NDVI dashboard summary).  Also exposes pre-computed
 * aggregate stats across all terrains.
 *
 * Used by: FarmDashboardPage, CreateTerrainPage
 *
 * Returns:
 *   farm        {object|null}
 *   terrains    {Array}
 *   terrainData {object}  — { [terrainId]: { parcels, lotes, ndvi } }
 *   stats       {object}  — { totalParcels, totalHa, activeLotes, totalCabezas }
 *   loading     {boolean}
 *   error       {Error|null}
 *   reload      {Function}
 */
export function useFarm(farmId) {
  const [farm, setFarm] = useState(null)
  const [terrains, setTerrains] = useState([])
  const [terrainData, setTerrainData] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const reload = useCallback(async () => {
    if (!farmId) return
    setLoading(true)
    setError(null)
    try {
      const [f, ts] = await Promise.all([
        farmApi.getById(farmId),
        terrainApi.getByFarm(farmId),
      ])
      setFarm(f)
      setTerrains(ts)

      // Load data for every terrain concurrently
      const entries = await Promise.all(
        ts.map(async (t) => {
          try {
            const [parcels, lotes] = await Promise.all([
              parcelApi.getByTerrain(t.id),
              loteApi.getByTerrain(t.id).catch(() => []),
            ])
            let ndvi = null
            try { ndvi = await ndviApi.getDashboard(t.id) } catch { /* no NDVI yet */ }
            return [t.id, { parcels, lotes, ndvi }]
          } catch {
            return [t.id, { parcels: [], lotes: [], ndvi: null }]
          }
        })
      )
      setTerrainData(Object.fromEntries(entries))
    } catch (err) {
      setError(err)
      toast.error('Error cargando finca')
    } finally {
      setLoading(false)
    }
  }, [farmId])

  useEffect(() => {
    reload()
  }, [reload])

  // Aggregate stats derived from terrainData + terrains
  const stats = useMemo(() => {
    const values = Object.values(terrainData)
    return {
      totalParcels: values.reduce((s, d) => s + d.parcels.length, 0),
      activeLotes:  values.reduce((s, d) => s + d.lotes.filter(l => !l.fechaSalida).length, 0),
      totalCabezas: values.reduce(
        (s, d) => s + d.lotes.reduce((a, l) => a + (l.cabezas || 0), 0),
        0
      ),
      totalHa: terrains.reduce((s, t) => s + (t.areaHectares || 0), 0),
    }
  }, [terrainData, terrains])

  return { farm, terrains, terrainData, stats, loading, error, reload }
}
