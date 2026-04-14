/**
 * Card
 *
 * Props:
 *   title      — string shown in the card header (omit for header-less card)
 *   subtitle   — secondary text below the title in the header
 *   headerRight— JSX rendered on the right side of the header
 *   padding    — 'sm' | 'md' | 'lg'  (default 'md')
 *   hoverable  — adds lift-on-hover effect (default false)
 *   className  — extra classes appended to root
 *   children   — card body content
 */
export default function Card({
  title,
  subtitle,
  headerRight,
  padding = 'md',
  hoverable = false,
  className = '',
  children,
}) {
  const classes = [
    'comp-card',
    `comp-card--pad-${padding}`,
    hoverable && 'comp-card--hoverable',
    className,
  ]
    .filter(Boolean)
    .join(' ')

  const hasHeader = title || headerRight

  return (
    <div className={classes}>
      {hasHeader && (
        <div className="comp-card__header">
          <div className="comp-card__header-left">
            {title && <h3 className="comp-card__title">{title}</h3>}
            {subtitle && <p className="comp-card__subtitle">{subtitle}</p>}
          </div>
          {headerRight && (
            <div className="comp-card__header-right">{headerRight}</div>
          )}
        </div>
      )}

      {children}
    </div>
  )
}
