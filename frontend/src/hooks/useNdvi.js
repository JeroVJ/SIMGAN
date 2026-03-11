import { useState, useEffect, useCallback, useMemo } from 'react'
import toast from 'react-hot-toast'
import { ndviApi } from '../services/api'
import { groupTimelineByDate } from '../utils/ndvi'

/**
 * useNdvi(terrainId)
 *
 * Loads all NDVI analytics data for a single terrain and exposes actions
 * for running analysis and managing alerts.
 *
 * Used by: NdviDashboardPage
 *
 * Returns:
 *   dashboard        {object|null}  — full dashboard response
 *   comparison       {Array}        — per-parcel comparison ranking
 *   recommendations  {Array}        — rotation recommendations
 *   history          {Array}        — rotation history entries
 *   loading          {boolean}
 *   analyzing        {boolean}      — true while analyze() is running
 *   selectedParcel   {number|null}  — currently selected parcel id
 *   parcelTimeline   {Array}        — timeline for selectedParcel
 *   timelineByDate   {Array}        — dashboard timeline grouped by date (Recharts format)
 *   parcelNames      {Array}        — parcel name strings for chart lines
 *   reload           {Function}
 *   analyze          {Function}     — trigger NDVI satellite analysis
 *   selectParcel     {Function(id)} — load per-parcel timeline
 *   acknowledgeAlert {Function(id)} — mark an alert as acknowledged
 */
export function useNdvi(terrainId) {
  const [dashboard, setDashboard]           = useState(null)
  const [comparison, setComparison]         = useState([])
  const [recommendations, setRecommendations] = useState([])
  const [history, setHistory]               = useState([])
  const [loading, setLoading]               = useState(true)
  const [analyzing, setAnalyzing]           = useState(false)
  const [selectedParcel, setSelectedParcel] = useState(null)
  const [parcelTimeline, setParcelTimeline] = useState([])

  const reload = useCallback(async () => {
    if (!terrainId) return
    setLoading(true)
    try {
      const [dash, comp, recs, hist] = await Promise.all([
        ndviApi.getDashboard(terrainId),
        ndviApi.getComparison(terrainId),
        ndviApi.getRecommendations(terrainId),
        ndviApi.getRotationHistory(terrainId),
      ])
      setDashboard(dash)
      setComparison(comp)
      setRecommendations(recs)
      setHistory(hist)
    } catch {
      toast.error('Error cargando dashboard NDVI')
    } finally {
      setLoading(false)
    }
  }, [terrainId])

  useEffect(() => {
    reload()
  }, [reload])

  const analyze = useCallback(async () => {
    setAnalyzing(true)
    try {
      const result = await ndviApi.analyze(terrainId)
      if (result.message)      toast.success(result.message, { duration: 6000 })
      if (result.planetNote)   toast(result.planetNote,   { icon: '🛰️', duration: 4000 })
      if (result.sentinelNote) toast(result.sentinelNote, { icon: '🌍', duration: 4000 })
      if (result.error)        toast.error(result.error)
      await reload()
    } catch (err) {
      toast.error('Error ejecutando análisis: ' + (err.response?.data?.error || err.message))
    } finally {
      setAnalyzing(false)
    }
  }, [terrainId, reload])

  const selectParcel = useCallback(async (parcelId) => {
    setSelectedParcel(parcelId)
    try {
      const timeline = await ndviApi.getParcelTimeline(parcelId)
      setParcelTimeline(timeline)
    } catch {
      setParcelTimeline([])
    }
  }, [])

  const acknowledgeAlert = useCallback(async (alertId) => {
    try {
      await ndviApi.acknowledgeAlert(alertId)
      toast.success('Alerta reconocida')
      await reload()
    } catch {
      toast.error('Error')
    }
  }, [reload])

  const timelineByDate = useMemo(
    () => groupTimelineByDate(dashboard?.timeline),
    [dashboard?.timeline]
  )

  const parcelNames = useMemo(
    () => dashboard?.parcels?.map(p => p.parcelName) ?? [],
    [dashboard?.parcels]
  )

  return {
    dashboard, comparison, recommendations, history,
    loading, analyzing,
    selectedParcel, parcelTimeline,
    timelineByDate, parcelNames,
    reload, analyze, selectParcel, acknowledgeAlert,
  }
}
