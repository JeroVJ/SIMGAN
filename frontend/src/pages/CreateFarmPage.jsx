import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import api from '../services/api'

export default function CreateFarmPage() {
  const [f, setF] = useState({
    name: '',
    department: '',
    municipality: '',
    centerLat: '',
    centerLng: ''
  })
  const [e, setE] = useState({})
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const set = k => ev => setF(p => ({ ...p, [k]: ev.target.value }))

  function validate() {
    const err = {}
    if (!f.name.trim()) err.name = 'El nombre es obligatorio'
    if (!f.department.trim()) err.department = 'El departamento es obligatorio'
    if (!f.municipality.trim()) err.municipality = 'El municipio es obligatorio'
    if (!f.centerLat || isNaN(f.centerLat)) err.centerLat = 'Latitud inválida'
    if (!f.centerLng || isNaN(f.centerLng)) err.centerLng = 'Longitud inválida'
    setE(err)
    return !Object.keys(err).length
  }

  async function submit() {
    if (!validate()) return
    setLoading(true)
    try {
      const user = JSON.parse(localStorage.getItem('user'))
      const token = localStorage.getItem('token')
      if (!user || !token) {
        toast.error('Sesión expirada. Por favor, inicia sesión nuevamente.')
        navigate('/')
        return
      }
      await api.post('/farms', {
        name: f.name,
        ganaderoId: user.id,
        department: f.department,
        municipality: f.municipality,
        centerLat: parseFloat(f.centerLat),
        centerLng: parseFloat(f.centerLng)
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
    <div style={{ padding: 20 }}>
      <h1>Crear Nueva Finca</h1>

      <div className="form-group">
        <label>Nombre de la Finca</label>
        <input
          type="text"
          placeholder="Mi Finca"
          value={f.name}
          onChange={set('name')}
          style={e.name ? { borderColor: 'var(--color-danger)' } : {}}
        />
        {e.name && <span style={{ fontSize: 12, color: 'var(--color-danger)' }}>{e.name}</span>}
      </div>

      <div className="form-group">
        <label>Departamento</label>
        <input
          type="text"
          placeholder="Cundinamarca"
          value={f.department}
          onChange={set('department')}
          style={e.department ? { borderColor: 'var(--color-danger)' } : {}}
        />
        {e.department && <span style={{ fontSize: 12, color: 'var(--color-danger)' }}>{e.department}</span>}
      </div>

      <div className="form-group">
        <label>Municipio</label>
        <input
          type="text"
          placeholder="Bogotá"
          value={f.municipality}
          onChange={set('municipality')}
          style={e.municipality ? { borderColor: 'var(--color-danger)' } : {}}
        />
        {e.municipality && <span style={{ fontSize: 12, color: 'var(--color-danger)' }}>{e.municipality}</span>}
      </div>

      <div className="form-grid" style={{ gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <div className="form-group">
          <label>Latitud Centro</label>
          <input
            type="number"
            step="0.000001"
            placeholder="4.6097"
            value={f.centerLat}
            onChange={set('centerLat')}
            style={e.centerLat ? { borderColor: 'var(--color-danger)' } : {}}
          />
          {e.centerLat && <span style={{ fontSize: 12, color: 'var(--color-danger)' }}>{e.centerLat}</span>}
        </div>
        <div className="form-group">
          <label>Longitud Centro</label>
          <input
            type="number"
            step="0.000001"
            placeholder="-74.0817"
            value={f.centerLng}
            onChange={set('centerLng')}
            style={e.centerLng ? { borderColor: 'var(--color-danger)' } : {}}
          />
          {e.centerLng && <span style={{ fontSize: 12, color: 'var(--color-danger)' }}>{e.centerLng}</span>}
        </div>
      </div>

      <button
        className="btn btn-primary"
        style={{ width: '100%', justifyContent: 'center' }}
        onClick={submit}
        disabled={loading}
      >
        {loading ? 'Creando...' : 'Crear Finca'}
      </button>
    </div>
  )
}