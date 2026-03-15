import { NavLink } from 'react-router-dom'
import { LayoutGrid, Plus, Satellite, X } from 'lucide-react'

/**
 * Sidebar
 *
 * Props:
 *   open    — whether the drawer is open (mobile only)
 *   onClose — callback to close the drawer
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

export default function Sidebar({ open, onClose }) {
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
              Sistema de Gestión Ganadera
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
        </footer>

      </aside>
    </>
  )
}
