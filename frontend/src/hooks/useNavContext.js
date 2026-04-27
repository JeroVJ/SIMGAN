import { useEffect, useState } from 'react'
import { useLocation, useParams, matchPath } from 'react-router-dom'
import { farmApi, terrainApi } from '../services/api'

/**
 * Parses the current URL and derives the active farm + terrain context.
 * Also loads list of farms + terrains for the sidebar selector.
 *
 * Used by: Sidebar (contextual navigation)
 *
 * Returns:
 *   farms, terrainsByFarm, currentFarmId, currentTerrainId, loading
 */
export function useNavContext() {
  const location = useLocation()
  const [farms, setFarms] = useState([])
  const [terrainsByFarm, setTerrainsByFarm] = useState({})
  const [terrainToFarm, setTerrainToFarm] = useState({})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const data = await farmApi.getAll()
        if (cancelled) return
        setFarms(data)

        const entries = await Promise.all(
          data.map(async (f) => {
            try {
              const ts = await terrainApi.getByFarm(f.id)
              return [f.id, ts]
            } catch {
              return [f.id, []]
            }
          })
        )
        if (cancelled) return
        const map = Object.fromEntries(entries)
        setTerrainsByFarm(map)

        // Build reverse index: terrainId → farmId
        const rev = {}
        for (const [farmId, ts] of entries) {
          for (const t of ts) rev[String(t.id)] = farmId
        }
        setTerrainToFarm(rev)
      } catch {
        // silently ignore — sidebar context is non-critical
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => { cancelled = true }
  }, [])

  // Derive context from URL
  const pathname = location.pathname
  let currentFarmId = null
  let currentTerrainId = null

  const farmMatch =
    matchPath('/farms/:farmId/*', pathname) ||
    matchPath('/farms/:farmId', pathname)
  if (farmMatch && farmMatch.params.farmId && farmMatch.params.farmId !== 'new') {
    currentFarmId = farmMatch.params.farmId
  }

  const terrainMatch =
    matchPath('/terrains/:terrainId/*', pathname) ||
    matchPath('/terrains/:terrainId', pathname)
  if (terrainMatch) {
    currentTerrainId = terrainMatch.params.terrainId
    if (!currentFarmId && terrainToFarm[currentTerrainId]) {
      currentFarmId = String(terrainToFarm[currentTerrainId])
    }
  }

  const loteMatch = matchPath('/lotes/:loteId', pathname)
  const parcelMatch = matchPath('/parcels/:parcelId/*', pathname)

  return {
    farms,
    terrainsByFarm,
    currentFarmId: currentFarmId != null ? String(currentFarmId) : null,
    currentTerrainId: currentTerrainId != null ? String(currentTerrainId) : null,
    currentLoteId: loteMatch?.params.loteId ?? null,
    currentParcelId: parcelMatch?.params.parcelId ?? null,
    loading,
  }
}
