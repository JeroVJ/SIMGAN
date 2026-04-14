import { useState, useEffect, useCallback } from 'react'
import toast from 'react-hot-toast'
import { farmApi, terrainApi } from '../services/api'

/**
 * useFarms
 *
 * Manages the full farm list and their associated terrain previews.
 * Used by: FarmsPage
 *
 * Returns:
 *   farms          {Array}    — all farm objects
 *   terrainsByFarm {object}   — { [farmId]: Terrain[] }
 *   loading        {boolean}
 *   error          {Error|null}
 *   reload         {Function} — manually re-fetch everything
 *   deleteFarm     {Function(id)} — delete a farm, then reload
 */
export function useFarms() {
  const [farms, setFarms] = useState([])
  const [terrainsByFarm, setTerrainsByFarm] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await farmApi.getAll()
      setFarms(data)

      // Load terrains for every farm concurrently
      const pairs = await Promise.all(
        data.map(async (farm) => {
          try {
            const terrains = await terrainApi.getByFarm(farm.id)
            return [farm.id, terrains]
          } catch {
            return [farm.id, []]
          }
        })
      )
      setTerrainsByFarm(Object.fromEntries(pairs))
    } catch (err) {
      setError(err)
      toast.error('Error cargando fincas')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  const deleteFarm = useCallback(
    async (id) => {
      try {
        await farmApi.delete(id)
        toast.success('Finca eliminada')
        await reload()
      } catch (err) {
        toast.error('Error eliminando finca')
        throw err
      }
    },
    [reload]
  )

  return { farms, terrainsByFarm, loading, error, reload, deleteFarm }
}
