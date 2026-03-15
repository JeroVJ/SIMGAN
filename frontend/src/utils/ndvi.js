/**
 * NDVI utilities
 *
 * Pure functions for NDVI health classification and colour mapping.
 * Returns hex colours (not CSS vars) so they work inside Recharts tooltips,
 * GeoJSON styles, and any context where CSS custom properties are unsupported.
 */

/**
 * Hex colour representing the health of an NDVI value.
 *
 * Thresholds (tropical pasture context):
 *   ≥ 0.60 → Excellent  (green)
 *   ≥ 0.40 → Good       (lime)
 *   ≥ 0.25 → Fair       (amber)
 *   <  0.25 → Critical  (red)
 *
 * @param {number|null} ndvi
 * @returns {string} hex colour
 */
export function getHealthColor(ndvi) {
  if (ndvi == null) return '#5c7a5c'
  if (ndvi >= 0.60) return '#4ade80'
  if (ndvi >= 0.40) return '#84cc16'
  if (ndvi >= 0.25) return '#f59e0b'
  return '#ef4444'
}

/**
 * Human-readable health label for an NDVI value.
 *
 * @param {number|null} ndvi
 * @returns {string}
 */
export function getHealthLabel(ndvi) {
  if (ndvi == null) return '—'
  if (ndvi >= 0.60) return 'Excelente'
  if (ndvi >= 0.40) return 'Bueno'
  if (ndvi >= 0.25) return 'Regular'
  return 'Crítico'
}

/**
 * Map parcel-status enum to the CSS class suffix used by .status-badge.
 *
 * @param {string} status - 'DISPONIBLE' | 'EN_USO' | 'EN_DESCANSO'
 * @returns {string}
 */
export function getStatusClass(status) {
  switch (status) {
    case 'DISPONIBLE':  return 'disponible'
    case 'EN_USO':      return 'en-uso'
    case 'EN_DESCANSO': return 'en-descanso'
    default:            return 'disponible'
  }
}

/**
 * Group an NDVI timeline array into { date → parcelValues } records
 * suitable for Recharts multi-line charts.
 *
 * @param {Array} timeline - [{ date, parcelName, parcelId, meanNdvi, biomassKgPerHa }]
 * @returns {Array<object>} sorted by date ascending
 */
export function groupTimelineByDate(timeline) {
  if (!timeline?.length) return []

  const grouped = {}
  for (const p of timeline) {
    if (!grouped[p.date]) grouped[p.date] = { date: p.date }
    grouped[p.date][p.parcelName || `P${p.parcelId}`] = p.meanNdvi
    grouped[p.date][`bio_${p.parcelName || p.parcelId}`] = p.biomassKgPerHa
  }

  return Object.values(grouped).sort((a, b) => a.date.localeCompare(b.date))
}
