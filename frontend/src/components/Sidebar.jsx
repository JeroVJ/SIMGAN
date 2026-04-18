import { NavLink, Link, useLocation } from 'react-router-dom'
import {
  LayoutGrid, Plus, Leaf, X, LogOut, FileText, Bell,
  Map, Users, Activity, Shuffle, Settings2, Home,
} from 'lucide-react'
import { useNavContext } from '../hooks'

/**
 * Sidebar with contextual navigation.
 *
 * - Top-level: Home (Fincas), Reportes, Alertas
 * - Contextual: when browsing a farm/terrain, exposes quick-jump links
 *   to every section of that farm/terrain (Potreros, Ganado, NDVI,
 *   Pastoreo, Sensores) so the user doesn't have to return to the
 *   dashboard to switch sections.
 */

const TOP_NAV = [
  { to: '/farms',    end: true, label: 'Mis Fincas', Icon: Home },
  { to: '/reportes', end: true, label: 'Reportes',   Icon: FileText },
  { to: '/alertas',  end: true, label: 'Alertas',    Icon: Bell },
]

const TERRAIN_SECTIONS = [
  { key: 'parcels',  path: 'parcels',  label: 'Potreros',     Icon: Map },
  { key: 'lotes',    path: 'lotes',    label: 'Ganado',       Icon: Users },
  { key: 'ndvi',     path: 'ndvi',     label: 'NDVI & Salud', Icon: Activity },
  { key: 'rotation', path: 'rotation', label: 'Pastoreo',     Icon: Shuffle },
]

function initialOf(name) {
  return (name || '?').trim().charAt(0).toUpperCase()
}

export default function Sidebar({ open, onClose, user, onLogout }) {
  const location = useLocation()
  const {
    farms,
    terrainsByFarm,
    currentFarmId,
    currentTerrainId,
  } = useNavContext()

  const currentFarm = farms.find(f => String(f.id) === currentFarmId)
  const currentFarmTerrains = currentFarmId ? terrainsByFarm[currentFarmId] || [] : []
  const currentTerrain = currentFarmTerrains.find(
    t => String(t.id) === currentTerrainId
  )

  return (
    <>
      <div
        className={`sidebar-overlay${open ? ' sidebar-overlay--visible' : ''}`}
        onClick={onClose}
        aria-hidden="true"
      />

      <aside className={`sidebar${open ? ' sidebar--open' : ''}`}>

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
            <Leaf size={18} strokeWidth={2} />
          </div>
          <div className="sidebar-brand__text">
            <span className="sidebar-brand__name">SIMGAN</span>
            <span className="sidebar-brand__tagline">Ganadería Inteligente</span>
          </div>
        </div>

        {/* ── Primary navigation ────────────────────────────── */}
        <nav className="sidebar-nav" aria-label="Navegación principal">
          <p className="sidebar-nav__section-label">General</p>

          {TOP_NAV.map(({ to, end, label, Icon }) => (
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
                <Icon size={16} strokeWidth={1.9} />
              </span>
              <span className="sidebar-nav__label">{label}</span>
            </NavLink>
          ))}

          <Link
            to="/farms/new"
            onClick={onClose}
            className={`sidebar-nav__item${
              location.pathname === '/farms/new' ? ' sidebar-nav__item--active' : ''
            }`}
            style={{ marginTop: 2 }}
          >
            <span className="sidebar-nav__icon">
              <Plus size={16} strokeWidth={2} />
            </span>
            <span className="sidebar-nav__label">Nueva Finca</span>
          </Link>

          {/* ── Contextual: current farm ──────────────────── */}
          {currentFarm && (
            <div className="sidebar-ctx">
              <div className="sidebar-ctx__label">Finca actual</div>

              <Link
                to={`/farms/${currentFarm.id}`}
                onClick={onClose}
                className="sidebar-ctx__farm"
                style={{ textDecoration: 'none' }}
              >
                <span className="sidebar-ctx__farm-icon">
                  {initialOf(currentFarm.name)}
                </span>
                <span className="sidebar-ctx__farm-info">
                  <span className="sidebar-ctx__farm-name">
                    {currentFarm.name || 'Finca'}
                  </span>
                  <span className="sidebar-ctx__farm-meta">
                    {currentFarm.municipality
                      ? `${currentFarm.municipality}`
                      : currentFarm.department || 'Ubicación no registrada'}
                  </span>
                </span>
              </Link>

              {/* Terrains */}
              {currentFarmTerrains.length === 0 ? (
                <Link
                  to={`/farms/${currentFarm.id}/terrain/new`}
                  onClick={onClose}
                  className="sidebar-ctx__terrain"
                  style={{ color: 'var(--color-primary)', textDecoration: 'none' }}
                >
                  <Plus size={14} strokeWidth={2} />
                  Crear primer terreno
                </Link>
              ) : (
                currentFarmTerrains.map(t => {
                  const isActive = String(t.id) === currentTerrainId
                  return (
                    <Link
                      key={t.id}
                      to={`/terrains/${t.id}/parcels`}
                      onClick={onClose}
                      className={`sidebar-ctx__terrain${
                        isActive ? ' sidebar-ctx__terrain--active' : ''
                      }`}
                    >
                      <span className="sidebar-ctx__terrain-dot" />
                      <span style={{
                        flex: 1,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}>
                        {t.name || `Terreno ${t.id}`}
                      </span>
                      {t.areaHectares != null && (
                        <span className="sidebar-ctx__terrain-area">
                          {t.areaHectares.toFixed(1)} ha
                        </span>
                      )}
                    </Link>
                  )
                })
              )}

              {/* Sections within the active terrain */}
              {currentTerrain && (
                <>
                  <div className="sidebar-ctx__label" style={{ marginTop: 10 }}>
                    Secciones del terreno
                  </div>
                  <div className="sidebar-nav__sub" style={{ marginLeft: 0, paddingLeft: 0, borderLeft: 'none' }}>
                    {TERRAIN_SECTIONS.map(({ key, path, label, Icon }) => {
                      const to = `/terrains/${currentTerrain.id}/${path}`
                      const isActive = location.pathname.startsWith(to)
                      return (
                        <Link
                          key={key}
                          to={to}
                          onClick={onClose}
                          className={`sidebar-nav__subitem${
                            isActive ? ' sidebar-nav__subitem--active' : ''
                          }`}
                        >
                          <Icon size={14} strokeWidth={1.9} />
                          {label}
                        </Link>
                      )
                    })}
                  </div>
                </>
              )}
            </div>
          )}
        </nav>

        {/* ── Footer: user ──────────────────────────────────── */}
        <footer className="sidebar-footer">
          {user && (
            <div className="sidebar-footer__user">
              <span className="sidebar-footer__avatar">
                {initialOf(user.nombreCompleto || user.correo)}
              </span>
              <span className="sidebar-footer__user-info">
                <span className="sidebar-footer__user-name">
                  {user.nombreCompleto && user.apellidoCompleto
                    ? `${user.nombreCompleto} ${user.apellidoCompleto}`
                    : user.nombreCompleto || 'Usuario'}
                </span>
                <span className="sidebar-footer__user-email">
                  {user.correo || ''}
                </span>
              </span>
              <button
                onClick={onLogout}
                className="sidebar-footer__logout-btn"
                title="Cerrar sesión"
                aria-label="Cerrar sesión"
              >
                <LogOut size={14} strokeWidth={2} />
              </button>
            </div>
          )}
        </footer>

      </aside>
    </>
  )
}
