import { NavLink, Link } from 'react-router-dom'
import { Map, Users, Activity, Shuffle, ChevronLeft, Sliders, TrendingUp } from 'lucide-react'

/**
 * Quick-switch tabs for a single terrain.
 * Lets the user jump between Potreros / Ganado / NDVI / Pastoreo
 * without returning to the farm dashboard.
 *
 * Props:
 *   terrainId - number|string
 *   farmId    - optional, used to render a back-link to the farm dashboard
 *   farmName  - optional, shown in breadcrumb
 *   terrainName - optional, shown as the section title
 *   areaHa    - optional, shown as a pill
 */
const TABS = [
  { path: 'parcels',       label: 'Potreros',     Icon: Map },
  { path: 'lotes',         label: 'Ganado',       Icon: Users },
  { path: 'ndvi',          label: 'NDVI & Salud', Icon: Activity, end: true },
  { path: 'ndvi/timeline', label: 'Línea NDVI',   Icon: TrendingUp },
  { path: 'rotation',      label: 'Pastoreo',     Icon: Shuffle },
]

export default function TerrainTabs({
  terrainId,
  farmId,
  farmName,
  terrainName,
  areaHa,
  extraAction = null,
}) {
  if (!terrainId) return null

  return (
    <div className="terrain-context">
      {/* Breadcrumb */}
      <div className="breadcrumb">
        <Link to="/farms">Fincas</Link>
        <span className="sep">/</span>
        {farmId && farmName ? (
          <>
            <Link to={`/farms/${farmId}`}>{farmName}</Link>
            <span className="sep">/</span>
          </>
        ) : farmId ? (
          <>
            <Link to={`/farms/${farmId}`}>Finca</Link>
            <span className="sep">/</span>
          </>
        ) : null}
        <span>{terrainName || 'Terreno'}</span>
      </div>

      {/* Title + meta + action */}
      <div className="terrain-context__title-row">
        <div className="terrain-context__title-block">
          <h1 className="terrain-context__title">
            {terrainName || 'Terreno'}
          </h1>
          <div className="terrain-context__meta">
            {areaHa != null && (
              <span className="terrain-context__meta-pill">
                <Map size={12} strokeWidth={2} />
                {Number(areaHa).toFixed(2)} ha
              </span>
            )}
            {farmName && (
              <span className="terrain-context__meta-pill">
                {farmName}
              </span>
            )}
          </div>
        </div>
        {extraAction}
      </div>

      {/* Tabs */}
      <div className="terrain-tabs" role="tablist" aria-label="Secciones del terreno">
        {TABS.map(({ path, label, Icon, end }) => {
          const to = `/terrains/${terrainId}/${path}`
          return (
            <NavLink
              key={path}
              to={to}
              end={end}
              className={({ isActive }) =>
                `terrain-tab${isActive ? ' terrain-tab--active' : ''}`
              }
            >
              <Icon size={14} strokeWidth={1.9} className="terrain-tab__icon" />
              {label}
            </NavLink>
          )
        })}
      </div>
    </div>
  )
}
