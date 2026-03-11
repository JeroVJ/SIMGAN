import { forwardRef } from 'react'

/**
 * Button
 *
 * Props:
 *   variant  — 'default' | 'primary' | 'outline' | 'ghost' | 'danger' | 'accent' | 'warning'
 *   size     — 'sm' | 'md' | 'lg'
 *   icon     — JSX node rendered before the label
 *   iconRight— JSX node rendered after the label
 *   loading  — shows spinner and disables button
 *   disabled — disables the button
 *   type     — HTML button type (default 'button')
 *   className— extra classes appended to root
 *   onClick  — click handler
 *   children — button label
 *
 * All other props are forwarded to the <button> element.
 */
const Button = forwardRef(function Button(
  {
    children,
    variant = 'default',
    size = 'md',
    icon,
    iconRight,
    loading = false,
    disabled = false,
    type = 'button',
    className = '',
    onClick,
    ...rest
  },
  ref
) {
  const isIconOnly = !children && (icon || iconRight)

  const classes = [
    'comp-btn',
    `comp-btn--${variant}`,
    size !== 'md' && `comp-btn--${size}`,
    loading && 'comp-btn--loading',
    isIconOnly && 'comp-btn--icon-only',
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <button
      ref={ref}
      type={type}
      className={classes}
      disabled={disabled || loading}
      onClick={onClick}
      {...rest}
    >
      {loading ? (
        <span className="comp-btn__spinner" aria-hidden="true" />
      ) : (
        icon && <span className="comp-btn__icon">{icon}</span>
      )}

      {children && <span>{children}</span>}

      {!loading && iconRight && (
        <span className="comp-btn__icon">{iconRight}</span>
      )}
    </button>
  )
})

export default Button
