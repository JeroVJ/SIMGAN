import { NavLink, useMatch } from 'react-router-dom'
import { LayoutGrid, Plus, Satellite, X, LogOut, LayoutDashboard, Beef, Activity, RotateCcw } from 'lucide-react'

const NAV_ITEMS = [
  { to: '/farms',     end: true,  label: 'Mis Fincas',   Icon: LayoutGrid },
  { to: '/farms/new', end: false, label: 'Nueva Finca',  Icon: Plus       },
]

const TERRAIN_TABS = [
  { key: 'parcels',  label: 'Parcelas',  Icon: LayoutDashboard },
  { key: 'lotes',    label: 'Ganado',    Icon: Beef            },
  { key: 'ndvi',     label: 'NDVI',      Icon: Activity        },
  { key: 'rotation', label: 'Rotación',  Icon: RotateCcw       },
]

export default function Sidebar({ open, onClose, user, onLogout }) {
  const terrainMatch = useMatch('/terrains/:terrainId/:section')
  const terrainId    = terrainMatch?.params?.terrainId
  const activeSection = terrainMatch?.params?.section

  return (
    <>
      <div
        className={`sidebar-overlay${open ? ' sidebar-overlay--visible' : ''}`}
        onClick={onClose}
        aria-hidden="true"
      />

      <aside className={`sidebar${open ? ' sidebar--open' : ''}`}>

        <button className="sidebar__close-btn" onClick={onClose} aria-label="Cerrar menú">
          <X size={18} strokeWidth={2} />
        </button>

        {/* Brand */}
        <div className="sidebar-brand">
          <div className="sidebar-brand__icon-wrap">
            <Satellite size={16} strokeWidth={1.75} />
          </div>
          <div className="sidebar-brand__text">
            <span className="sidebar-brand__name">SIMGAN</span>
          </div>
        </div>

        {/* Primary nav */}
        <nav className="sidebar-nav" aria-label="Navegación principal">
          <p className="sidebar-nav__section-label">Navegación</p>
          <div className="sidebar-nav__divider" />
          {NAV_ITEMS.map(({ to, end, label, Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              onClick={onClose}
              className={({ isActive }) =>
                `sidebar-nav__item${isActive ? ' sidebar-nav__item--active' : ''}`
              }
            >
              <span className="sidebar-nav__icon"><Icon size={16} strokeWidth={1.75} /></span>
              <span className="sidebar-nav__label">{label}</span>
            </NavLink>
          ))}
        </nav>

        {/* Context nav: shown when inside a terrain */}
        {terrainId && (
          <nav className="sidebar-context" aria-label="Terreno activo">
            <span className="sidebar-context__label">Terreno activo</span>
            {TERRAIN_TABS.map(({ key, label, Icon }) => (
              <NavLink
                key={key}
                to={`/terrains/${terrainId}/${key}`}
                onClick={onClose}
                className={({ isActive }) =>
                  `sidebar-context__item${isActive ? ' sidebar-context__item--active' : ''}`
                }
              >
                <Icon size={14} strokeWidth={2} />
                {label}
              </NavLink>
            ))}
          </nav>
        )}

        {/* Footer */}
        <footer className="sidebar-footer">
          <div className="sidebar-footer__stack">
            <span>Planet Labs</span>
            <span className="sidebar-footer__dot" />
            <span>Sentinel-2</span>
            <span className="sidebar-footer__dot" />
            <span>NDVI</span>
          </div>
          <span className="sidebar-footer__version">SIMGAN v0.2</span>

          {user && (
            <div style={{ marginTop: 16, paddingTop: 12, borderTop: '1px solid var(--oh-border)' }}>
              <div style={{ fontSize: 12, color: 'var(--oh-text-muted)', marginBottom: 8, lineHeight: 1.5 }}>
                <strong style={{ color: 'var(--oh-text-sec)', fontWeight: 600 }}>
                  {user.nombreCompleto && user.apellidoCompleto
                    ? `${user.nombreCompleto} ${user.apellidoCompleto}`
                    : user.correo || 'Usuario'}
                </strong>
                {user.correo && (
                  <div style={{ fontSize: 11, color: 'var(--oh-text-muted)', marginTop: 2 }}>{user.correo}</div>
                )}
              </div>
              <button
                onClick={onLogout}
                style={{
                  width: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 6,
                  padding: '7px 12px',
                  background: 'transparent',
                  color: '#ef4444',
                  border: '1px solid rgba(239,68,68,0.25)',
                  borderRadius: 'var(--oh-r-md)',
                  fontSize: 13,
                  cursor: 'pointer',
                  fontWeight: 600,
                  transition: 'all 0.15s ease',
                }}
                onMouseEnter={e => {
                  e.currentTarget.style.background = 'rgba(239,68,68,0.1)'
                  e.currentTarget.style.borderColor = 'rgba(239,68,68,0.4)'
                }}
                onMouseLeave={e => {
                  e.currentTarget.style.background = 'transparent'
                  e.currentTarget.style.borderColor = 'rgba(239,68,68,0.25)'
                }}
              >
                <LogOut size={14} strokeWidth={2} />
                Cerrar sesión
              </button>
            </div>
          )}
        </footer>
      </aside>
    </>
  )
}
