import { NavLink, useParams } from 'react-router-dom'
import { LayoutGrid, Beef, Activity, RotateCcw } from 'lucide-react'

const TABS = [
  { path: id => `/terrains/${id}/parcels`,  label: 'Parcelas', Icon: LayoutGrid },
  { path: id => `/terrains/${id}/lotes`,    label: 'Ganado',   Icon: Beef       },
  { path: id => `/terrains/${id}/ndvi`,     label: 'NDVI',     Icon: Activity   },
  { path: id => `/terrains/${id}/rotation`, label: 'Rotación', Icon: RotateCcw  },
]

export default function TerrainNav({ name }) {
  const { terrainId } = useParams()

  return (
    <nav className="terrain-subnav">
      {name && <span className="terrain-subnav__name">{name}</span>}
      <div className="terrain-subnav__tabs">
        {TABS.map(({ path, label, Icon }) => (
          <NavLink
            key={label}
            to={path(terrainId)}
            className={({ isActive }) =>
              `terrain-subnav__tab${isActive ? ' terrain-subnav__tab--active' : ''}`
            }
          >
            <Icon size={14} strokeWidth={2} />
            <span>{label}</span>
          </NavLink>
        ))}
      </div>
    </nav>
  )
}
