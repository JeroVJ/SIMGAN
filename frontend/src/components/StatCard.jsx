/**
 * StatCard
 *
 * A metric display card with an optional icon, large numeric/text value,
 * a label above the value, and optional sub-text below.
 *
 * Props:
 *   label    — uppercase descriptor (e.g. 'Terrenos')
 *   value    — primary metric (string or number)
 *   sub      — secondary text below the value (e.g. 'registrados')
 *   icon     — JSX node rendered in the top-left icon badge
 *   accent   — 'green' | 'amber' | 'blue' | 'red'
 *              tints both the icon background and the value text
 *   className— extra classes appended to root
 */
export default function StatCard({
  label,
  value,
  sub,
  icon,
  accent,
  className = '',
}) {
  const iconClass = [
    'comp-stat-card__icon',
    accent && `comp-stat-card__icon--${accent}`,
  ]
    .filter(Boolean)
    .join(' ')

  const valueClass = [
    'comp-stat-card__value',
    accent && `comp-stat-card__value--${accent}`,
  ]
    .filter(Boolean)
    .join(' ')

  const rootClass = [
    'comp-stat-card',
    accent && `comp-stat-card--${accent}`,
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={rootClass}>
      {icon && <div className={iconClass}>{icon}</div>}
      {label && <span className="comp-stat-card__label">{label}</span>}
      <span className={valueClass}>{value}</span>
      {sub && <span className="comp-stat-card__sub">{sub}</span>}
    </div>
  )
}
