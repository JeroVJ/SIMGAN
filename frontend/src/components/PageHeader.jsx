import Breadcrumb from './Breadcrumb'

/**
 * PageHeader
 *
 * Renders a breadcrumb trail, a display-font title, a subtitle, and an
 * optional action area (e.g. a primary Button) on the right.
 *
 * Props:
 *   title     — main page title (uses display font)
 *   subtitle  — secondary descriptor line
 *   breadcrumb— array of { label, to? } items forwarded to <Breadcrumb>
 *   action    — JSX node rendered top-right (e.g. <Button variant="primary">)
 *   className — extra classes appended to the root element
 *
 * Usage:
 *   <PageHeader
 *     title="Mis Fincas"
 *     subtitle="Gestiona tus fincas ganaderas y sus terrenos"
 *     breadcrumb={[{ label: 'Fincas' }]}
 *     action={<Button variant="primary" onClick={...}>Nueva Finca</Button>}
 *   />
 */
export default function PageHeader({
  title,
  subtitle,
  breadcrumb,
  action,
  className = '',
}) {
  return (
    <header
      className={`comp-page-header${className ? ` ${className}` : ''}`}
    >
      {breadcrumb?.length > 0 && <Breadcrumb items={breadcrumb} />}

      <div className="comp-page-header__row">
        <div className="comp-page-header__text">
          {title && <h1 className="comp-page-header__title">{title}</h1>}
          {subtitle && (
            <p className="comp-page-header__subtitle">{subtitle}</p>
          )}
        </div>

        {action && (
          <div className="comp-page-header__action">{action}</div>
        )}
      </div>
    </header>
  )
}
