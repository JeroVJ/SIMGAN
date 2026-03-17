import { NavLink } from 'react-router-dom'
import { LayoutGrid, Plus, Satellite, X, LogOut } from 'lucide-react'

/**
 * Sidebar
 *
 * Props:
 *   open    — whether the drawer is open (mobile only)
 *   onClose — callback to close the drawer
 *   user    — current logged-in user object
 *   onLogout — callback to handle logout
 *
 * On desktop the sidebar is always visible (CSS handles this).
 * On mobile it renders as an off-canvas drawer controlled by `open`.
 */

const NAV_ITEMS = [
  {
    to: '/farms',
    end: true,
    label: 'Mis Fincas',
    Icon: LayoutGrid,
  },
  {
    to: '/farms/new',
    end: false,
    label: 'Nueva Finca',
    Icon: Plus,
  },
]

export default function Sidebar({ open, onClose, user, onLogout }) {
  return (
    <>
      {/* ── Mobile backdrop ───────────────────────────────────── */}
      <div
        className={`sidebar-overlay${open ? ' sidebar-overlay--visible' : ''}`}
        onClick={onClose}
        aria-hidden="true"
      />

      {/* ── Sidebar panel ─────────────────────────────────────── */}
      <aside className={`sidebar${open ? ' sidebar--open' : ''}`}>

        {/* Mobile close button */}
        <button
          className="sidebar__close-btn"
          onClick={onClose}
          aria-label="Cerrar menú"
        >
          <X size={18} strokeWidth={2} />
        </button>

        {/* ── Brand ─────────────────────────────────────────── */}
        <div className="sidebar-brand">
          <div className="sidebar-brand__icon-wrap">
            <Satellite size={16} strokeWidth={1.75} />
          </div>
          <div className="sidebar-brand__text">
            <span className="sidebar-brand__name">SIMGAN</span>
            <span className="sidebar-brand__tagline">
             
              
            </span>
          </div>
        </div>

        {/* ── Primary navigation ────────────────────────────── */}
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
              <span className="sidebar-nav__icon">
                <Icon size={16} strokeWidth={1.75} />
              </span>
              <span className="sidebar-nav__label">{label}</span>
            </NavLink>
          ))}
        </nav>

        {/* ── Footer ────────────────────────────────────────── */}
        <footer className="sidebar-footer">
          <div className="sidebar-footer__stack">
            <span>Planet Labs</span>
            <span className="sidebar-footer__dot" />
            <span>Sentinel-2</span>
            <span className="sidebar-footer__dot" />
            <span>NDVI</span>
          </div>
          <span className="sidebar-footer__version">SIMGAN v0.2</span>

          {/* User Profile & Logout */}
          {user && (
            <div style={{ marginTop: 16, paddingTop: 12, borderTop: '1px solid var(--color-border)' }}>
              <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
                <strong>Conectado como:</strong><br />
                {user.nombreCompleto && user.apellidoCompleto 
                  ? `${user.nombreCompleto} ${user.apellidoCompleto}`
                  : user.correo || 'Usuario'}
              </div>
              <button
                onClick={onLogout}
                style={{
                  width: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 6,
                  padding: '8px 12px',
                  background: '#ef4444',
                  color: 'white',
                  border: 'none',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: 13,
                  cursor: 'pointer',
                  fontWeight: 500,
                  transition: 'background 0.2s'
                }}
                onMouseEnter={(e) => e.target.style.background = '#dc2626'}
                onMouseLeave={(e) => e.target.style.background = '#ef4444'}
              >
                <LogOut size={14} strokeWidth={2} />
                Cerrar Sesión
              </button>
            </div>
          )}
        </footer>

      </aside>
    </>
  )
}
