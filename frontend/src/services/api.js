import axios from 'axios'

// In dev: use the relative '/api' path (vite proxies to backend).
// In prod: set VITE_API_URL to your backend base URL, e.g.
//   https://simgan-api.onrender.com/api
const baseURL = import.meta.env.VITE_API_URL || '/api'

const api = axios.create({
  baseURL,
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
  revokeAll: () => api.post('/auth/revoke-all').then(r => r.data),
  forgotPassword: (email) => api.post('/auth/forgot-password', { email }).then(r => r.data),
  resetPassword: (token, password) => api.post('/auth/reset-password', { token, password }).then(r => r.data),
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
  delete: (id) => api.delete(`/parcels/${id}`),
  getRotationPlan: (terrainId, loteId, tipoAnimal, numeroAnimales) =>
    api.get(`/parcels/terrain/${terrainId}/rotation-plan`, {
      params: { loteId, tipoAnimal, numeroAnimales },
    }).then(r => r.data),
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

  // Ejecutar análisis
  analyze: (terrainId, startDate, endDate, biomassMethod) => {
    const params = {}
    if (startDate) params.startDate = startDate
    if (endDate) params.endDate = endDate
    if (biomassMethod) params.biomassMethod = biomassMethod
    return api.post(`/ndvi/analyze/${terrainId}`, null, { params }).then(r => r.data)
  },

  // Estado Planet Labs
  getPlanetStatus: () => api.get('/ndvi/planet/status').then(r => r.data),

  // Programación automática de análisis NDVI
  getSchedule: (terrainId) => api.get(`/ndvi/schedule/${terrainId}`).then(r => r.data),
  configureSchedule: (terrainId, days) => api.post(`/ndvi/schedule/${terrainId}`, null, { params: { days } }).then(r => r.data),

  // Estimación de pastoreo
  getGrazingEstimate: (terrainId) => api.get(`/ndvi/grazing-estimate/${terrainId}`).then(r => r.data)
}

// ===== CALIBRACIÓN NDVI =====
export const calibrationApi = {
  getStatus: (terrainId, type = 'OPTIM') =>
    api.get(`/ndvi/calibration/status/${terrainId}`, { params: { type } }).then(r => r.data),
  searchScenes: (terrainId, calibrationDate) =>
    api.get(`/ndvi/calibration/scenes/${terrainId}`, { params: { calibrationDate } }).then(r => r.data),
  runCalibration: (terrainId, calibrationDate, type = 'OPTIM', sceneId = null) =>
    api.post(`/ndvi/calibration/${terrainId}`, null, { params: { calibrationDate, type, ...(sceneId ? { sceneId } : {}) } }).then(r => r.data),
}

// ===== CALIBRACIÓN AUTOMÁTICA NDVI (12 meses) =====
export const autoCalibrationApi = {
  start: (terrainId) => api.post(`/ndvi/calibration/auto/${terrainId}`).then(r => r.data),
  status: (terrainId) => api.get(`/ndvi/calibration/auto/${terrainId}/status`).then(r => r.data),
}

// ===== MONITOREO POR POTRERO =====
export const monitoringApi = {
  get: (parcelId) => api.get(`/ndvi/monitoring/${parcelId}`).then(r => r.data),
  toggle: (parcelId, enabled) => api.put(`/ndvi/monitoring/${parcelId}`, { enabled }).then(r => r.data),
  fetchNow: (parcelId) => api.post(`/ndvi/monitoring/${parcelId}/fetch-now`).then(r => r.data),
}

// ===== CALIBRACIÓN BIOMASA =====
export const biomassCalibrationApi = {
  getStatus: (terrainId) =>
    api.get(`/ndvi/biomass-calibration/status/${terrainId}`).then(r => r.data),
  calibrateParcel: (terrainId, parcelId, points) =>
    api.post(`/ndvi/biomass-calibration/${terrainId}/${parcelId}`, { terrainId, parcelId, points }).then(r => r.data),
}

// ===== LOTES (GANADO) =====
export const loteApi = {
  // Lotes
  getByTerrain: (terrainId) => api.get(`/lotes/terrain/${terrainId}`).then(r => r.data),
  getActive: (terrainId) => api.get(`/lotes/terrain/${terrainId}/active`).then(r => r.data),
  getById: (id) => api.get(`/lotes/${id}`).then(r => r.data),
  create: (data) => api.post('/lotes', data).then(r => r.data),
  close: (id, data) => api.patch(`/lotes/${id}/close`, data).then(r => r.data),
  delete: (id) => api.delete(`/lotes/${id}`),

  // Asignación de parcela
  assignParcel: (loteId, parcelId) => api.post(`/lotes/${loteId}/assign-parcel`, { parcelId }).then(r => r.data),
  unassignParcel: (loteId) => api.post(`/lotes/${loteId}/unassign-parcel`).then(r => r.data),
  saveRotationAssignment: (loteId, entries) =>
    api.post(`/lotes/${loteId}/rotation-assignment`, { entries }).then(r => r.data),

  // Ganado
  getGanado: (loteId) => api.get(`/lotes/${loteId}/ganado`).then(r => r.data),
  addGanado: (loteId, data) => api.post(`/lotes/${loteId}/ganado`, data).then(r => r.data),
  addGanadoBatch: (loteId, data) => api.post(`/lotes/${loteId}/ganado/batch`, data).then(r => r.data),
  updateGanado: (ganadoId, data) => api.put(`/lotes/ganado/${ganadoId}`, data).then(r => r.data),
  deleteGanado: (ganadoId) => api.delete(`/lotes/ganado/${ganadoId}`)
}

// ===== REPORTS =====
export const reportApi = {
  /**
   * Downloads a PDF terrain report. Returns a Blob.
   */
  downloadTerrainReport: (terrainId) =>
    api.get(`/reports/terrain/${terrainId}`, { responseType: 'blob' }).then(r => r.data),
}

// ===== ALERTS =====
export const alertApi = {
  getByTerrain: (terrainId) => api.get(`/alerts/terrain/${terrainId}`).then(r => r.data),
}

// ===== SENSORES =====
export const sensorApi = {
  getByParcel: (parcelId) => api.get(`/sensors/parcel/${parcelId}`).then(r => r.data),
  getLastClassificationByParcel: (parcelId) => api.get(`/sensors/parcel/${parcelId}/last-classification`).then(r => r.data),
  getById: (id) => api.get(`/sensors/${id}`).then(r => r.data),
  create: (data) => api.post('/sensors', data).then(r => r.data),
  update: (id, data) => api.patch(`/sensors/${id}`, data).then(r => r.data),
  delete: (id) => api.delete(`/sensors/${id}`),
  
  // Conexión del sensor
  connectSensor: (id) => api.post(`/sensors/${id}/connect`).then(r => r.data),
  disconnectSensor: (id) => api.post(`/sensors/${id}/disconnect`).then(r => r.data),
  getSensorStatus: (id) => api.get(`/sensors/${id}/status`).then(r => r.data),
  
  // Configuración MQTT
  updateSensorConfig: (id, config) => api.patch(`/sensors/${id}/config`, config).then(r => r.data)
}

export default api
