import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../services/api'
import toast from 'react-hot-toast'

const DEPARTMENTS = [
  'Amazonas','Antioquia','Arauca','Atlántico','Bogotá D.C.','Bolívar',
  'Boyacá','Caldas','Caquetá','Casanare','Cauca','Cesar','Chocó',
  'Córdoba','Cundinamarca','Guainía','Guaviare','Huila','La Guajira',
  'Magdalena','Meta','Nariño','Norte de Santander','Putumayo','Quindío',
  'Risaralda','San Andrés','Santander','Sucre','Tolima','Valle del Cauca',
  'Vaupés','Vichada'
]

const SOIL_TYPES = ['Arenosa', 'Limosa', 'Arcillosa', 'Franco Arcillosa']
const PASTURE_TYPES = ['Brachiaria humidicola']

export default function CreateFarmPage() {

  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)

  const [form, setForm] = useState({
    name: '',
    owner: '',
    department: '',
    municipality: '',
    centerLat: '',
    centerLng: '',
    isHomogeneous: false,
    soilType: '',
    pastureType: ''
  })

  const [errors, setErrors] = useState({})

  function handleChange(e) {
    const { name, type, value, checked } = e.target
    setForm(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }))
  }

  function validate() {
    const err = {}

    if (!form.name.trim()) err.name = 'El nombre es obligatorio'
    if (!form.department.trim()) err.department = 'El departamento es obligatorio'
    if (!form.municipality.trim()) err.municipality = 'El municipio es obligatorio'

    if (!form.centerLat || isNaN(form.centerLat))
      err.centerLat = 'Latitud inválida'

    if (!form.centerLng || isNaN(form.centerLng))
      err.centerLng = 'Longitud inválida'

    if (form.isHomogeneous) {
      if (!form.soilType) err.soilType = 'Selecciona el tipo de suelo'
      if (!form.pastureType) err.pastureType = 'Selecciona el tipo de pasto'
    }

    setErrors(err)
    return Object.keys(err).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()

    if (!validate()) return

    setLoading(true)

    try {
      const user = JSON.parse(localStorage.getItem('user'))
      const token = localStorage.getItem('token')

      if (!user || !token) {
        toast.error('Sesión expirada. Por favor inicia sesión nuevamente.')
        navigate('/')
        return
      }

      await api.post('/farms', {
        name: form.name,
        ganaderoId: user.id,
        department: form.department,
        municipality: form.municipality,
        centerLat: parseFloat(form.centerLat),
        centerLng: parseFloat(form.centerLng),
        isHomogeneous: form.isHomogeneous,
        soilType: form.isHomogeneous ? form.soilType : null,
        pastureType: form.isHomogeneous ? form.pastureType : null
      })

      toast.success('Finca creada exitosamente')
      navigate('/farms')

    } catch (err) {
      const msg = err.response?.data?.message || 'Error creando finca'
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div className="breadcrumb">
          <Link to="/farms">Mis Fincas</Link>
          <span>›</span>
          <span>Crear Finca</span>
        </div>
        <h2>Crear Finca</h2>
        <p>Registra una nueva finca ganadera en el sistema</p>
      </div>

      <div className="card" style={{ maxWidth: 680 }}>
        <form onSubmit={handleSubmit}>

          <div className="form-grid">
            <div className="form-group">
              <label>Nombre de la Finca *</label>
              <input
                name="name"
                value={form.name}
                onChange={handleChange}
                placeholder="Ej: Hacienda Los Robles"
              />
              {errors.name && <span style={{ color: 'red', fontSize: '12px' }}>{errors.name}</span>}
            </div>

            <div className="form-group">
              <label>Propietario *</label>
              <input
                name="owner"
                value={form.owner}
                onChange={handleChange}
                placeholder="Ej: Carlos Rodríguez"
              />
            </div>

            <div className="form-group">
              <label>Departamento</label>
              <select
                name="department"
                value={form.department}
                onChange={handleChange}
              >
                <option value="">Seleccionar...</option>
                {DEPARTMENTS.map(d => (
                  <option key={d} value={d}>{d}</option>
                ))}
              </select>
              {errors.department && <span style={{ color: 'red', fontSize: '12px' }}>{errors.department}</span>}
            </div>

            <div className="form-group">
              <label>Municipio</label>
              <input
                name="municipality"
                value={form.municipality}
                onChange={handleChange}
                placeholder="Ej: Montería"
              />
              {errors.municipality && <span style={{ color: 'red', fontSize: '12px' }}>{errors.municipality}</span>}
            </div>

            <div className="form-group">
              <label>Latitud (centro GPS)</label>
              <input
                name="centerLat"
                type="number"
                step="any"
                value={form.centerLat}
                onChange={handleChange}
                placeholder="Ej: 8.7479"
              />
              {errors.centerLat && <span style={{ color: 'red', fontSize: '12px' }}>{errors.centerLat}</span>}
            </div>

            <div className="form-group">
              <label>Longitud (centro GPS)</label>
              <input
                name="centerLng"
                type="number"
                step="any"
                value={form.centerLng}
                onChange={handleChange}
                placeholder="Ej: -75.8814"
              />
              {errors.centerLng && <span style={{ color: 'red', fontSize: '12px' }}>{errors.centerLng}</span>}
            </div>
          </div>

          <div style={{ marginTop: '24px', paddingTop: '24px', borderTop: '1px solid #e5e7eb' }}>
            <div style={{ marginBottom: '16px' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  name="isHomogeneous"
                  checked={form.isHomogeneous}
                  onChange={handleChange}
                  style={{ width: '18px', height: '18px', cursor: 'pointer' }}
                />
                <span style={{ fontWeight: '500' }}>¿La finca es homogénea?</span>
              </label>
              <p style={{ fontSize: '12px', color: '#6b7280', marginTop: '4px' }}>
                Si es homogénea, especifica el tipo de suelo y pasto que se aplicará a todos los potreros
              </p>
            </div>

            {form.isHomogeneous && (
              <div className="form-grid" style={{ gap: '16px' }}>
                <div className="form-group">
                  <label>Tipo de Suelo *</label>
                  <select
                    name="soilType"
                    value={form.soilType}
                    onChange={handleChange}
                  >
                    <option value="">Seleccionar tipo de suelo...</option>
                    {SOIL_TYPES.map(soil => (
                      <option key={soil} value={soil}>{soil}</option>
                    ))}
                  </select>
                  {errors.soilType && <span style={{ color: 'red', fontSize: '12px' }}>{errors.soilType}</span>}
                </div>

                <div className="form-group">
                  <label>Tipo de Pasto *</label>
                  <select
                    name="pastureType"
                    value={form.pastureType}
                    onChange={handleChange}
                  >
                    <option value="">Seleccionar tipo de pasto...</option>
                    {PASTURE_TYPES.map(pasture => (
                      <option key={pasture} value={pasture}>{pasture}</option>
                    ))}
                  </select>
                  {errors.pastureType && <span style={{ color: 'red', fontSize: '12px' }}>{errors.pastureType}</span>}
                </div>
              </div>
            )}
          </div>

          <div className="flex gap-12 mt-24">
            <button
              type="submit"
              className="action-btn action-btn--primary"
              disabled={loading}
            >
              {loading
                ? <><span className="spinner" /> Guardando...</>
                : '✓ Crear Finca'}
            </button>

            <button
              type="button"
              className="action-btn"
              onClick={() => navigate('/farms')}
            >
              Cancelar
            </button>
          </div>

        </form>
      </div>
    </div>
  )
}