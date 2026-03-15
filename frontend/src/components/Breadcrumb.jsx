import { Link } from 'react-router-dom'

/**
 * Breadcrumb
 *
 * Props:
 *   items — array of breadcrumb segments:
 *     { label: string, to?: string }
 *     - When `to` is provided the item renders as a <Link>.
 *     - The last item is always treated as the current page (no link).
 *   className — extra classes appended to the nav element
 *
 * Usage:
 *   <Breadcrumb items={[
 *     { label: 'Fincas', to: '/farms' },
 *     { label: 'El Palmar', to: '/farms/3' },
 *     { label: 'NDVI' },
 *   ]} />
 */
export default function Breadcrumb({ items = [], className = '' }) {
  if (!items.length) return null

  return (
    <nav
      className={`comp-breadcrumb${className ? ` ${className}` : ''}`}
      aria-label="Breadcrumb"
    >
      {items.map((item, index) => {
        const isLast = index === items.length - 1

        return (
          <span key={index} className="comp-breadcrumb__item">
            {!isLast && item.to ? (
              <Link to={item.to} className="comp-breadcrumb__link">
                {item.label}
              </Link>
            ) : (
              <span
                className={
                  isLast
                    ? 'comp-breadcrumb__current'
                    : 'comp-breadcrumb__link'
                }
                aria-current={isLast ? 'page' : undefined}
              >
                {item.label}
              </span>
            )}

            {!isLast && (
              <span className="comp-breadcrumb__sep" aria-hidden="true">
                /
              </span>
            )}
          </span>
        )
      })}
    </nav>
  )
}
