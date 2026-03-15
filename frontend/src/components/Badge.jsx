/**
 * Badge
 *
 * A small pill label for status indicators and urgency levels.
 *
 * Props:
 *   variant  — controls color theme:
 *
 *     Parcel status  : 'disponible' | 'en-uso' | 'en-descanso'
 *     Semantic       : 'success' | 'warning' | 'danger' | 'info' | 'neutral'
 *     Urgency        : 'urgente' | 'alta' | 'media' | 'baja'
 *     Closed/archived: 'closed'
 *
 *   dot      — show the leading colored dot (default: true for status variants,
 *              false for urgency variants; pass explicitly to override)
 *   className— extra classes
 *   children — badge label text
 */

const STATUS_VARIANTS = new Set([
  'disponible',
  'en-uso',
  'en-descanso',
  'success',
  'warning',
  'danger',
  'info',
  'neutral',
])

const URGENCY_VARIANTS = new Set(['urgente', 'alta', 'media', 'baja'])

export default function Badge({
  variant = 'neutral',
  dot,
  className = '',
  children,
}) {
  // Default dot behaviour: show for status/semantic, hide for urgency
  const showDot =
    dot !== undefined
      ? dot
      : STATUS_VARIANTS.has(variant) && !URGENCY_VARIANTS.has(variant)

  const classes = [
    'comp-badge',
    `comp-badge--${variant}`,
    showDot && 'comp-badge--has-dot',
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return <span className={classes}>{children}</span>
}
