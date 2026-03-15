/**
 * Spinner
 *
 * Props:
 *   size    — 'sm' | 'md' | 'lg'  (default 'md')
 *   label   — accessible text shown beneath the spinner (default 'Cargando...')
 *   page    — true  → full-page centered layout (min-height: 60vh)
 *             false → inline centered block with padding (default)
 *
 * Usage — full-page:
 *   <Spinner page />
 *
 * Usage — inside a card section:
 *   <Spinner label="Cargando parcelas..." />
 *
 * Usage — bare spinner (inside a button, etc.):
 *   <Spinner size="sm" label={null} />
 */
export default function Spinner({
  size = 'md',
  label = 'Cargando...',
  page = false,
}) {
  const spinnerEl = (
    <span
      className={`comp-spinner comp-spinner--${size}`}
      role="status"
      aria-label={label ?? undefined}
    />
  )

  if (label === null) {
    return spinnerEl
  }

  const wrapClass = page ? 'comp-spinner-page' : 'comp-spinner-inline'

  return (
    <div className={wrapClass}>
      {spinnerEl}
      <span className="comp-spinner-label">{label}</span>
    </div>
  )
}
