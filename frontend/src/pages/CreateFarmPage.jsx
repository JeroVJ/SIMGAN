import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { farmApi } from '../services/api'
import toast from 'react-hot-toast'

const DEPARTMENTS = [
  'Amazonas', 'Antioquia', 'Arauca', 'Atlántico', 'Bogotá D.C.', 'Bolívar',
  'Boyacá', 'Caldas', 'Caquetá', 'Casanare', 'Cauca', 'Cesar', 'Chocó',
  'Córdoba', 'Cundinamarca', 'Guainía', 'Guaviare', 'Huila', 'La Guajira',
  'Magdalena', 'Meta', 'Nariño', 'Norte de Santander', 'Putumayo', 'Quindío',
  'Risaralda', 'San Andrés', 'Santander', 'Sucre', 'Tolima', 'Valle del Cauca',
  'Vaupés', 'Vichada'
]

export default function CreateFarmPage() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [form, setForm] = useState({
    name: '',
    owner: '',
    department: '',
    municipality: '',
    centerLat: '',
    centerLng: ''
  })

  function handleChange(e) {
    setForm({ ...form, [e.target.name]: e.target.value })
  }

  async function handleSubmit(e) {
    e.preventDefault()

    if (!form.name.trim() || !form.owner.trim()) {
      toast.error('Nombre y propietario son obligatorios')
      return
    }

    setLoading(true)
    try {
      const payload = {
        name: form.name.trim(),
        owner: form.owner.trim(),
        department: form.department || null,
        municipality: form.municipality.trim() || null,
        centerLat: form.centerLat ? parseFloat(form.centerLat) : null,
        centerLng: form.centerLng ? parseFloat(form.centerLng) : null
      }

      const farm = await farmApi.create(payload)
      toast.success(`Finca "${farm.name}" creada exitosamente`)
      navigate(`/farms/${farm.id}/terrain/new`)
    } catch (err) {
      toast.error('Error creando finca')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div className="breadcrumb">
          <a href="/farms">Mis Fincas</a>
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
                required
              />
            </div>

            <div className="form-group">
              <label>Propietario *</label>
              <input
                name="owner"
                value={form.owner}
                onChange={handleChange}
                placeholder="Ej: Carlos Rodríguez"
                required
              />
            </div>

            <div className="form-group">
              <label>Departamento</label>
              <select name="department" value={form.department} onChange={handleChange}>
                <option value="">Seleccionar...</option>
                {DEPARTMENTS.map(d => (
                  <option key={d} value={d}>{d}</option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label>Municipio</label>
              <input
                name="municipality"
                value={form.municipality}
                onChange={handleChange}
                placeholder="Ej: Montería"
              />
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
            </div>
          </div>

          <div className="flex gap-12 mt-24">
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? <><span className="spinner" /> Guardando...</> : '✓ Crear Finca'}
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => navigate('/farms')}>
              Cancelar
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
