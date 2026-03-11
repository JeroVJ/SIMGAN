import { useState, useEffect } from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { Sidebar, MobileHeader } from './components'

import FarmsPage         from './pages/FarmsPage'
import CreateFarmPage    from './pages/CreateFarmPage'
import CreateTerrainPage from './pages/CreateTerrainPage'
import FarmDashboardPage from './pages/FarmDashboardPage'
import ParcelsPage       from './pages/ParcelsPage'
import RotationPage      from './pages/RotationPage'
import NdviDashboardPage from './pages/NdviDashboardPage'
import LotesPage         from './pages/LotesPage'
import LoteDetailPage    from './pages/LoteDetailPage'

export default function App() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const location = useLocation()

  // Close the mobile drawer whenever the user navigates
  useEffect(() => {
    setSidebarOpen(false)
  }, [location.pathname])

  // Prevent body scroll while the mobile drawer is open
  useEffect(() => {
    document.body.style.overflow = sidebarOpen ? 'hidden' : ''
    return () => { document.body.style.overflow = '' }
  }, [sidebarOpen])

  return (
    <div className="app-layout">
      {/* Mobile-only top bar */}
      <MobileHeader onMenuClick={() => setSidebarOpen(true)} />

      {/* Sidebar (fixed on desktop, drawer on mobile) */}
      <Sidebar
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      {/* Main scrollable content */}
      <main className="main-content">
        <div className="page-container">
          <Routes>
            <Route path="/"                              element={<Navigate to="/farms" replace />} />
            <Route path="/farms"                         element={<FarmsPage />} />
            <Route path="/farms/new"                     element={<CreateFarmPage />} />
            <Route path="/farms/:farmId"                 element={<FarmDashboardPage />} />
            <Route path="/farms/:farmId/terrain/new"     element={<CreateTerrainPage />} />
            <Route path="/terrains/:terrainId/parcels"   element={<ParcelsPage />} />
            <Route path="/terrains/:terrainId/rotation"  element={<RotationPage />} />
            <Route path="/terrains/:terrainId/ndvi"      element={<NdviDashboardPage />} />
            <Route path="/terrains/:terrainId/lotes"     element={<LotesPage />} />
            <Route path="/lotes/:loteId"                 element={<LoteDetailPage />} />
          </Routes>
        </div>
      </main>
    </div>
  )
}
