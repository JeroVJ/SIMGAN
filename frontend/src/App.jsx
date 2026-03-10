import { useState, useEffect } from 'react'
import { Routes, Route, NavLink, Navigate } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import FarmsPage from './pages/FarmsPage'
import CreateFarmPage from './pages/CreateFarmPage'
import CreateTerrainPage from './pages/CreateTerrainPage'
import ParcelsPage from './pages/ParcelsPage'
import RotationPage from './pages/RotationPage'
import NdviDashboardPage from './pages/NdviDashboardPage'
import AuthPage          from './pages/AuthPages'
import api, { authApi } from './services/api'

// Inyecta el token en cada request
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export default function App() {
  const [user, setUser] = useState(undefined) // undefined = verificando, null = sin sesión

  useEffect(() => {
    const token = localStorage.getItem('token')
    if (!token) { setUser(null); return }

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
      .catch(() => {}) // Ignore errors, logout anyway
      .finally(() => {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        setUser(null)
      })
  }

  // Cargando token
  if (user === undefined) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--color-bg)' }}>
        <div className="spinner" />
      </div>
    )
  }

  // Sin sesión → AuthPage
  if (!user) {
    return (
      <>
        <Toaster position="top-right" toastOptions={{ style: { background: 'var(--color-surface)', color: 'var(--color-text)', border: '1px solid var(--color-border)' } }} />
        <AuthPage onLogin={handleLogin} />
      </>
    )
  }

  // Con sesión → app completa
  return (
    <>
      <Toaster position="top-right" toastOptions={{ style: { background: 'var(--color-surface)', color: 'var(--color-text)', border: '1px solid var(--color-border)' } }} />

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
            {user?.nombreCompleto && (
              <div style={{ marginBottom: 10, color: 'var(--color-text-secondary)', fontSize: 13 }}>
                👤 {user.nombreCompleto} {user.apellidoCompleto}
              </div>
            )}
            <button
              onClick={handleLogout}
              className="btn btn-secondary btn-sm"
              style={{ width: '100%', justifyContent: 'center', marginBottom: 12 }}
            >
              Cerrar sesión
            </button>
            <strong>SIMGAN v0.1</strong><br />
            NDVI Analytics · Planet Labs<br />
            <span style={{ fontSize: 10, opacity: 0.6 }}>Sentinel-2 (ESA) — planificado</span>
          </div>
        </aside>

        <main className="main-content">
          <Routes>
            <Route path="/"                             element={<Navigate to="/farms" replace />} />
            <Route path="/farms"                        element={<FarmsPage />} />
            <Route path="/farms/new"                    element={<CreateFarmPage />} />
            <Route path="/farms/:farmId/terrain/new"    element={<CreateTerrainPage />} />
            <Route path="/terrains/:terrainId/parcels"  element={<ParcelsPage />} />
            <Route path="/terrains/:terrainId/rotation" element={<RotationPage />} />
            <Route path="/terrains/:terrainId/ndvi"     element={<NdviDashboardPage />} />
            <Route path="*"                             element={<Navigate to="/farms" replace />} />
          </Routes>
        </main>
      </div>
    </>
  )
}  