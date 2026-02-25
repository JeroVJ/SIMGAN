import { Routes, Route, NavLink, Navigate } from 'react-router-dom'
import FarmsPage from './pages/FarmsPage'
import CreateFarmPage from './pages/CreateFarmPage'
import CreateTerrainPage from './pages/CreateTerrainPage'
import ParcelsPage from './pages/ParcelsPage'
import RotationPage from './pages/RotationPage'
import NdviDashboardPage from './pages/NdviDashboardPage'

export default function App() {
  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <h1>SIMGAN</h1>
          <p>Gestión Ganadera</p>
        </div>

        <nav className="sidebar-nav">
          <NavLink to="/farms" className={({ isActive }) => isActive ? 'active' : ''}>
            <span className="nav-icon">🏠</span>
            Mis Fincas
          </NavLink>
          <NavLink to="/farms/new" className={({ isActive }) => isActive ? 'active' : ''}>
            <span className="nav-icon">➕</span>
            Crear Finca
          </NavLink>
        </nav>

        <div className="sidebar-footer">
          <strong>SIMGAN v0.1</strong><br />
          NDVI Analytics · Planet Labs<br />
          <span style={{ fontSize: 10, opacity: 0.6 }}>Sentinel-2 (ESA) — planificado</span>
        </div>
      </aside>

      <main className="main-content">
        <Routes>
          <Route path="/" element={<Navigate to="/farms" replace />} />
          <Route path="/farms" element={<FarmsPage />} />
          <Route path="/farms/new" element={<CreateFarmPage />} />
          <Route path="/farms/:farmId/terrain/new" element={<CreateTerrainPage />} />
          <Route path="/terrains/:terrainId/parcels" element={<ParcelsPage />} />
          <Route path="/terrains/:terrainId/rotation" element={<RotationPage />} />
          <Route path="/terrains/:terrainId/ndvi" element={<NdviDashboardPage />} />
        </Routes>
      </main>
    </div>
  )
}
