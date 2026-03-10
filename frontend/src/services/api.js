import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' }
})

// Add token to requests
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Handle 401 responses
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/'
    }
    return Promise.reject(error)
  }
)

// ===== AUTH ENDPOINTS =====
export const authApi = {
  login: (email, password) => api.post('/auth/login', { email, password }).then(r => r.data),
  register: (firstName, lastName, email, password) => api.post('/auth/register', { firstName, lastName, email, password }).then(r => r.data),
  me: () => api.get('/auth/me').then(r => r.data),
  logout: () => api.post('/auth/logout').then(r => r.data),
  getSessions: () => api.get('/auth/sessions').then(r => r.data),
  revokeAll: () => api.post('/auth/revoke-all').then(r => r.data)
}

// ===== FARMS =====
export const farmApi = {
  getAll: () => api.get('/farms').then(r => r.data),
  getById: (id) => api.get(`/farms/${id}`).then(r => r.data),
  create: (data) => api.post('/farms', data).then(r => r.data),
  delete: (id) => api.delete(`/farms/${id}`)
}

// ===== TERRAINS =====
export const terrainApi = {
  getByFarm: (farmId) => api.get(`/terrains/farm/${farmId}`).then(r => r.data),
  getById: (id) => api.get(`/terrains/${id}`).then(r => r.data),
  create: (data) => api.post('/terrains', data).then(r => r.data),
  delete: (id) => api.delete(`/terrains/${id}`)
}

// ===== PARCELS =====
export const parcelApi = {
  getByTerrain: (terrainId) => api.get(`/parcels/terrain/${terrainId}`).then(r => r.data),
  create: (data) => api.post('/parcels', data).then(r => r.data),
  updateStatus: (id, status) => api.patch(`/parcels/${id}/status`, { status }).then(r => r.data),
  delete: (id) => api.delete(`/parcels/${id}`)
}

// ===== NDVI / ANALYTICS =====
export const ndviApi = {
  // Dashboard completo
  getDashboard: (terrainId) => api.get(`/ndvi/dashboard/${terrainId}`).then(r => r.data),

  // Timeline
  getTimeline: (terrainId, start, end) => {
    const params = {}
    if (start) params.start = start
    if (end) params.end = end
    return api.get(`/ndvi/timeline/${terrainId}`, { params }).then(r => r.data)
  },

  // Timeline por parcela
  getParcelTimeline: (parcelId) => api.get(`/ndvi/parcel/${parcelId}/timeline`).then(r => r.data),

  // Comparación de parcelas
  getComparison: (terrainId) => api.get(`/ndvi/comparison/${terrainId}`).then(r => r.data),

  // Recomendaciones de rotación
  getRecommendations: (terrainId) => api.get(`/ndvi/recommendations/${terrainId}`).then(r => r.data),

  // Historial de rotación
  getRotationHistory: (terrainId) => api.get(`/ndvi/history/${terrainId}`).then(r => r.data),

  // Alertas
  getAlerts: (terrainId) => api.get(`/ndvi/alerts/${terrainId}`).then(r => r.data),
  acknowledgeAlert: (alertId) => api.patch(`/ndvi/alerts/${alertId}/acknowledge`),

  // Ejecutar análisis
  analyze: (terrainId) => api.post(`/ndvi/analyze/${terrainId}`).then(r => r.data),

  // Estado Planet Labs
  getPlanetStatus: () => api.get('/ndvi/planet/status').then(r => r.data)
}

export default api
