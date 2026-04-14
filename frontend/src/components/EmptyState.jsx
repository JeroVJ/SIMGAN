/**
 * EmptyState
 *
 * Props:
 *   icon        — emoji string or JSX node (e.g. '🌾' or <SvgIcon />)
 *   title       — primary heading text
 *   description — supporting body text
 *   action      — optional JSX node (e.g. a <Button>) rendered below description
 *   card        — wraps in a dashed-border card surface (default true)
 *   className   — extra classes appended to root
 */
export default function EmptyState({
  icon,
  title,
  description,
  action,
  card = true,
  className = '',
}) {
  const classes = [
    'comp-empty',
    card && 'comp-empty--card',
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={classes}>
      {icon && <div className="comp-empty__icon">{icon}</div>}
      {title && <h3 className="comp-empty__title">{title}</h3>}
      {description && (
        <p className="comp-empty__description">{description}</p>
      )}
      {action}
    </div>
  )
}
