import { useState, useEffect } from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'

import api, { authApi } from './services/api'

import { Sidebar, MobileHeader } from './components'

import FarmsPage         from './pages/FarmsPage'
import CreateFarmPage    from './pages/CreateFarmPage'
import CreateTerrainPage from './pages/CreateTerrainPage'
import FarmDashboardPage from './pages/FarmDashboardPage'
import ParcelsPage       from './pages/ParcelsPage'
import SensorPage        from './pages/SensorPage'
import SensorSessionPage from './pages/SensorSessionPage'
import RotationPage      from './pages/RotationPage'
import NdviDashboardPage from './pages/NdviDashboardPage'
import CalibrationNdviOptimPage from './pages/CalibrationNdviOptimPage'
import CalibrationNdviAlertPage from './pages/CalibrationNdviAlertPage'
import CalibrationBiomassPage from './pages/CalibrationBiomassPage'
import LotesPage         from './pages/LotesPage'
import LoteDetailPage    from './pages/LoteDetailPage'
import AuthPage          from './pages/AuthPages'


// Inyectar token en cada request
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})


export default function App() {

  const [user, setUser] = useState(undefined)
  const [sidebarOpen, setSidebarOpen] = useState(false)

  const location = useLocation()


  useEffect(() => {

    const token = localStorage.getItem('token')

    if (!token) {
      setUser(null)
      return
    }

    api.get('/auth/me')
      .then(({ data }) => {
        setUser(data)
        localStorage.setItem('user', JSON.stringify(data))
      })
      .catch(() => {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        setUser(null)
      })

  }, [])


  function handleLogin(data) {

    const ganadero = data.ganadero ?? data

    setUser(ganadero)

    localStorage.setItem('user', JSON.stringify(ganadero))
  }


  function handleLogout() {

    authApi.logout()
      .catch(() => {})
      .finally(() => {

        localStorage.removeItem('token')
        localStorage.removeItem('user')

        setUser(null)

      })

  }


  // cerrar sidebar en navegación
  useEffect(() => {
    setSidebarOpen(false)
  }, [location.pathname])


  // bloquear scroll cuando sidebar móvil está abierto
  useEffect(() => {

    document.body.style.overflow = sidebarOpen ? 'hidden' : ''

    return () => {
      document.body.style.overflow = ''
    }

  }, [sidebarOpen])


  // Verificando token
  if (user === undefined) {
    return (
      <div style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center'
      }}>
        <div className="spinner" />
      </div>
    )
  }


  // Sin sesión
  if (!user) {
    return (
      <>
        <Toaster position="top-right" />
        <AuthPage onLogin={handleLogin} />
      </>
    )
  }


  // Con sesión
  return (

    <>

      <Toaster position="top-right" />

      <div className="app-layout">

        <MobileHeader onMenuClick={() => setSidebarOpen(true)} />

        <Sidebar
          open={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
          user={user}
          onLogout={handleLogout}
        />

        <main className="main-content">

          <div className="page-container">

            <Routes>

              <Route path="/" element={<Navigate to="/farms" replace />} />

              <Route path="/farms" element={<FarmsPage />} />

              <Route path="/farms/new" element={<CreateFarmPage />} />

              <Route path="/farms/:farmId" element={<FarmDashboardPage />} />

              <Route path="/farms/:farmId/terrain/new" element={<CreateTerrainPage />} />

              <Route path="/terrains/:terrainId/parcels" element={<ParcelsPage />} />

              <Route path="/parcels/:parcelId/sensors" element={<SensorPage />} />

              <Route path="/sensors/:sensorId/session" element={<SensorSessionPage />} />

              <Route path="/terrains/:terrainId/rotation" element={<RotationPage />} />

              <Route path="/terrains/:terrainId/ndvi" element={<NdviDashboardPage />} />

              <Route path="/terrains/:terrainId/ndvi/calibration-optim" element={<CalibrationNdviOptimPage />} />

              <Route path="/terrains/:terrainId/ndvi/calibration-alert" element={<CalibrationNdviAlertPage />} />

              <Route path="/terrains/:terrainId/ndvi/calibration-biomass" element={<CalibrationBiomassPage />} />

              <Route path="/terrains/:terrainId/lotes" element={<LotesPage />} />

              <Route path="/lotes/:loteId" element={<LoteDetailPage />} />

            </Routes>

          </div>

        </main>

      </div>

    </>

  )
}