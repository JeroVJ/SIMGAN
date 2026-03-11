/**
 * Grazing utilities
 *
 * Pure functions shared by useTerrain, LotesPage, ParcelsPage, and RotationPage.
 * Centralises the biomass-to-grazing-days model so it only lives in one place.
 *
 * Biomass model: kg MS/ha = max(0, (NDVI - 0.1) × 12,000)
 * Based on Brachiaria/Estrella pasture literature for Colombian tropics.
 */

/**
 * Estimate how many grazing days remain for a lote on a given parcel,
 * given the current biomass reading.
 *
 * Assumptions:
 *   - 30% residual biomass is left ungrazed (pasture recovery floor)
 *   - Dry-matter intake ≈ 2.5% of live weight per head per day
 *
 * @param {object} parcel  - { areaHectares }
 * @param {object} lote    - { cabezas, pesoPromedioActual }
 * @param {number|null} biomassKgPerHa
 * @returns {number|null}
 */
export function estimateGrazingDays(parcel, lote, biomassKgPerHa) {
  if (!parcel || !lote || biomassKgPerHa == null) return null
  if (!lote.cabezas || lote.cabezas === 0) return null
  if (!lote.pesoPromedioActual || lote.pesoPromedioActual === 0) return null

  const areaHa = parcel.areaHectares || 0
  if (areaHa === 0) return null

  const totalBiomassKg   = biomassKgPerHa * areaHa
  const availableBiomass = Math.max(0, totalBiomassKg * 0.70) // leave 30% residual
  const dailyIntakePerHead = lote.pesoPromedioActual * 0.025  // 2.5% of live weight
  const dailyIntakeTotal   = dailyIntakePerHead * lote.cabezas

  if (dailyIntakeTotal === 0) return null

  return Math.max(0, Math.floor(availableBiomass / dailyIntakeTotal))
}

/**
 * Total dry-matter consumption for a lote per day (kg MS/day).
 *
 * @param {object} lote - { pesoPromedioActual, cabezas }
 * @returns {number|null}
 */
export function getDailyConsumption(lote) {
  if (!lote?.pesoPromedioActual || !lote?.cabezas) return null
  return Math.round(lote.pesoPromedioActual * 0.025 * lote.cabezas * 10) / 10
}

/**
 * Tailwind-free hex colour for a biomass value.
 * Used by map tooltips and inline chart elements that cannot use CSS vars.
 *
 * @param {number|null} biomass - kg MS / ha
 * @returns {string} hex colour
 */
export function getBiomassColor(biomass) {
  if (biomass == null) return '#888888'
  if (biomass < 1000)  return '#ef4444'
  if (biomass < 2500)  return '#f59e0b'
  if (biomass < 5000)  return '#84cc16'
  return '#4ade80'
}

/**
 * Human-readable label for a biomass value.
 *
 * @param {number|null} biomass
 * @returns {string|null}
 */
export function getBiomassLabel(biomass) {
  if (biomass == null) return null
  if (biomass < 1000)  return 'Degradado'
  if (biomass < 2500)  return 'Bajo'
  if (biomass < 5000)  return 'Bueno'
  return 'Excelente'
}

/**
 * Build grazing alerts for all parcels based on current parcelInfo.
 * Shared between useTerrain (pre-computed) and pages that need re-computation.
 *
 * @param {Array}  parcels    - parcel objects
 * @param {object} parcelInfo - { [parcelId]: { biomass, ndvi, lote, grazingDays } }
 * @returns {Array<{ parcelName, loteName, severity, message }>}
 */
export function buildGrazingAlerts(parcels, parcelInfo) {
  const alerts = []

  for (const p of parcels) {
    const info = parcelInfo[p.id]
    if (!info?.lote) continue

    const { biomass, ndvi, grazingDays, lote } = info

    if (biomass != null && biomass < 1000) {
      alerts.push({
        parcelName: p.name,
        loteName: lote.name,
        severity: 'critical',
        message: `Pasto degradado en ${p.name}. Se recomienda retirar "${lote.name}" y poner en descanso.`,
      })
    } else if (biomass != null && biomass < 2500) {
      alerts.push({
        parcelName: p.name,
        loteName: lote.name,
        severity: 'warning',
        message: `Pasto bajo en ${p.name}. Considere rotar "${lote.name}" pronto.`,
      })
    }

    if (ndvi != null && ndvi < 0.25 && biomass == null) {
      alerts.push({
        parcelName: p.name,
        loteName: lote.name,
        severity: 'critical',
        message: `NDVI critico (${ndvi.toFixed(2)}) en ${p.name}. Se recomienda poner en descanso.`,
      })
    }

    if (grazingDays != null && grazingDays <= 3) {
      alerts.push({
        parcelName: p.name,
        loteName: lote.name,
        severity: grazingDays === 0 ? 'critical' : 'warning',
        message:
          grazingDays === 0
            ? `Sin pasto disponible en ${p.name}. Retire "${lote.name}" inmediatamente.`
            : `Solo ${grazingDays} dia(s) de pasto restante en ${p.name} para "${lote.name}".`,
      })
    }
  }

  return alerts
}
