import { useState } from 'react'
import toast from 'react-hot-toast'
import { authApi } from '../services/api'

// ── Helpers ───────────────────────────────────────────────────────────────────

function pwStrength(pw) {
  if (!pw) return 0
  let s = 0
  if (pw.length >= 8)          s++
  if (/[A-Z]/.test(pw))        s++
  if (/[0-9]/.test(pw))        s++
  if (/[^A-Za-z0-9]/.test(pw)) s++
  return s
}

const strMeta = [null,
  { label: 'Débil',    color: 'var(--color-danger)' },
  { label: 'Regular',  color: 'var(--color-warning)' },
  { label: 'Buena',    color: 'var(--color-primary)' },
  { label: '¡Fuerte!', color: 'var(--color-primary)' },
]

// ── Login ─────────────────────────────────────────────────────────────────────

function LoginForm({ onLogin }) {
  const [f, setF]       = useState({ email: '', password: '' })
  const [e, setE]       = useState({})
  const [show, setShow] = useState(false)
  const [loading, setLoading] = useState(false)
  const set = k => ev => setF(p => ({ ...p, [k]: ev.target.value }))

  function validate() {
    const err = {}
    if (!f.email)                            err.email    = 'El correo es requerido'
    else if (!/\S+@\S+\.\S+/.test(f.email)) err.email    = 'Correo inválido'
    if (!f.password)                         err.password = 'La contraseña es requerida'
    setE(err)
    return !Object.keys(err).length
  }

  async function submit() {
    if (!validate()) return
    setLoading(true)
    try {
      const data = await authApi.login(f.email, f.password)
      if (data.token) {
        localStorage.setItem('token', data.token)
        localStorage.setItem('user', JSON.stringify(data.ganadero))
      }
      toast.success('¡Bienvenido de vuelta! 🌾')
      onLogin(data)
    } catch (err) {
      const msg = err.response?.data?.message || 'Credenciales incorrectas'
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <div className="form-group">
        <label>Correo electrónico</label>
        <input
          type="email"
          placeholder="tu@correo.com"
          value={f.email}
          onChange={set('email')}
          style={e.email ? { borderColor: 'var(--color-danger)' } : {}}
        />
        {e.email && <span style={{ fontSize: 12, color: 'var(--color-danger)' }}>{e.email}</span>}
      </div>

      <div className="form-group">
        <label>Contraseña</label>
        <div style={{ position: 'relative' }}>
          <input
            type={show ? 'text' : 'password'}
            placeholder="Tu contraseña"
            value={f.password}
            onChange={set('password')}
            style={{ width: '100%', paddingRight: 40, ...(e.password ? { borderColor: 'var(--color-danger)' } : {}) }}
          />
          <button type="button" onClick={() => setShow(s => !s)} style={{
            position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)',
            background: 'none', border: 'none', cursor: 'pointer',
            color: 'var(--color-text-muted)', fontSize: 15, lineHeight: 1, padding: 2
          }}>
            {show ? 'Ocultar' : 'Ver'}
          </button>
        </div>
        {e.password && <span style={{ fontSize: 12, color: 'var(--color-danger)' }}>{e.password}</span>}
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 20 }}>
        <button type="button" style={{
          background: 'none', border: 'none', cursor: 'pointer',
          color: 'var(--color-primary)', fontSize: 13, fontWeight: 600,
          fontFamily: 'var(--font-body)', padding: 0
        }}>
          ¿Olvidaste tu contraseña?
        </button>
      </div>

      <button
        className="btn btn-primary"
        style={{ width: '100%', justifyContent: 'center' }}
        onClick={submit}
        disabled={loading}
      >
        {loading
          ? <><div className="spinner" style={{ width: 16, height: 16, borderTopColor: 'var(--color-bg)' }} /> Ingresando...</>
          : 'Ingresar'}
      </button>
    </>
  )
}

// ── Sign Up ───────────────────────────────────────────────────────────────────

function SignupForm({ onLogin }) {
  const [f, setF]   = useState({ firstName: '', lastName: '', email: '', password: '', confirm: '', terms: false })
  const [e, setE]   = useState({})
  const [showPw, setShowPw] = useState(false)
  const [loading, setLoading] = useState(false)
  const set = k => ev => setF(p => ({ ...p, [k]: ev.target.type === 'checkbox' ? ev.target.checked : ev.target.value }))

  const str  = pwStrength(f.password)
  const meta = strMeta[str]

  function validate() {
    const err = {}
    if (!f.firstName.trim())                 err.firstName = 'Requerido'
    if (!f.lastName.trim())                  err.lastName  = 'Requerido'
    if (!f.email)                             err.email     = 'Requerido'
    else if (!/\S+@\S+\.\S+/.test(f.email)) err.email     = 'Correo inválido'
    if (f.password.length < 8)              err.password  = 'Mínimo 8 caracteres'
    if (f.password !== f.confirm)           err.confirm   = 'No coinciden'
    if (!f.terms)                            err.terms     = 'Acepta los términos'
    setE(err)
    return !Object.keys(err).length
  }

  async function submit() {
    if (!validate()) return
    setLoading(true)
    try {
      const data = await authApi.register(f.firstName, f.lastName, f.email, f.password)
      if (data.token) {
        localStorage.setItem('token', data.token)
        localStorage.setItem('user', JSON.stringify(data.ganadero))
      }
      toast.success('¡Cuenta creada exitosamente! 🌱')
      onLogin(data)
    } catch (err) {
      const msg = err.response?.data?.message || 'Error al crear la cuenta'
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <div className="form-grid" style={{ gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <div className="form-group">
          <label>Nombre</label>
          <input placeholder="Juan" value={f.firstName} onChange={set('firstName')}
            style={e.firstName ? { borderColor: 'var(--color-danger)' } : {}} />
          {e.firstName && <span style={{ fontSize: 12, color: 'var(--color-danger)' }}>{e.firstName}</span>}
        </div>
        <div className="form-group">
          <label>Apellido</label>
          <input placeholder="Pérez" value={f.lastName} onChange={set('lastName')}
            style={e.lastName ? { borderColor: 'var(--color-danger)' } : {}} />
          {e.lastName && <span style={{ fontSize: 12, color: 'var(--color-danger)' }}>{e.lastName}</span>}
        </div>
      </div>

      <div className="form-group">
        <label>Correo electrónico</label>
        <input type="email" placeholder="tu@correo.com" value={f.email} onChange={set('email')}
          style={e.email ? { borderColor: 'var(--color-danger)' } : {}} />
        {e.email && <span style={{ fontSize: 12, color: 'var(--color-danger)' }}>{e.email}</span>}
      </div>

      <div className="form-group">
        <label>Contraseña</label>
        <div style={{ position: 'relative' }}>
          <input
            type={showPw ? 'text' : 'password'}
            placeholder="Mínimo 8 caracteres"
            value={f.password}
            onChange={set('password')}
            style={{ width: '100%', paddingRight: 40, ...(e.password ? { borderColor: 'var(--color-danger)' } : {}) }}
          />
          <button type="button" onClick={() => setShowPw(s => !s)} style={{
            position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)',
            background: 'none', border: 'none', cursor: 'pointer',
            color: 'var(--color-text-muted)', fontSize: 15, lineHeight: 1, padding: 2
          }}>
            {showPw ? 'Ocultar' : 'Ver'}
          </button>
        </div>
        {f.password && (
          <>
            <div style={{ display: 'flex', gap: 4, marginTop: 8 }}>
              {[1, 2, 3, 4].map(i => (
                <div key={i} style={{
                  flex: 1, height: 3, borderRadius: 2,
                  background: i <= str ? meta.color : 'var(--color-border)',
                  transition: 'background .3s'
                }} />
              ))}
            </div>
            <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--color-text-muted)', marginTop: 3 }}>{meta?.label}</span>
          </>
        )}
        {e.password && <span style={{ fontSize: 12, color: 'var(--color-danger)' }}>{e.password}</span>}
      </div>

      <div className="form-group">
        <label>Confirmar contraseña</label>
        <input type="password" placeholder="Repite tu contraseña" value={f.confirm} onChange={set('confirm')}
          style={e.confirm ? { borderColor: 'var(--color-danger)' } : {}} />
        {e.confirm && <span style={{ fontSize: 12, color: 'var(--color-danger)' }}>{e.confirm}</span>}
      </div>

      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10, marginBottom: 20 }}>
        <input type="checkbox" checked={f.terms} onChange={set('terms')} style={{
          width: 15, height: 15, accentColor: 'var(--color-primary)',
          marginTop: 2, flexShrink: 0, cursor: 'pointer'
        }} />
        <span style={{ fontSize: 13, color: 'var(--color-text-secondary)', lineHeight: 1.6 }}>
          Acepto los{' '}
          <button type="button" style={{ background: 'none', border: 'none', color: 'var(--color-primary)', fontFamily: 'var(--font-body)', fontSize: 13, fontWeight: 600, cursor: 'pointer', padding: 0 }}>
            términos y condiciones
          </button>{' '}
          y la{' '}
          <button type="button" style={{ background: 'none', border: 'none', color: 'var(--color-primary)', fontFamily: 'var(--font-body)', fontSize: 13, fontWeight: 600, cursor: 'pointer', padding: 0 }}>
            política de privacidad
          </button>
        </span>
      </div>
      {e.terms && <span style={{ fontSize: 12, color: 'var(--color-danger)', display: 'block', marginBottom: 14 }}>{e.terms}</span>}

      <button
        className="btn btn-primary"
        style={{ width: '100%', justifyContent: 'center' }}
        onClick={submit}
        disabled={loading}
      >
        {loading
          ? <><div className="spinner" style={{ width: 16, height: 16, borderTopColor: 'var(--color-bg)' }} /> Creando cuenta...</>
          : ' Crear Cuenta'}
      </button>
    </>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function AuthPage({ onLogin }) {
  const [tab, setTab] = useState('login')

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: 24,
      background: 'var(--color-bg)'
    }}>
      <div className="card" style={{ width: '100%', maxWidth: 440 }}>

        {/* Tabs */}
        <div style={{
          display: 'flex',
          background: 'var(--color-surface-2)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-sm)',
          padding: 4,
          gap: 4,
          marginBottom: 28
        }}>
          {['login', 'signup'].map(t => (
            <button
              key={t}
              type="button"
              onClick={() => setTab(t)}
              style={{
                flex: 1,
                padding: '9px 0',
                border: tab === t ? '1px solid rgba(74,222,128,.2)' : '1px solid transparent',
                borderRadius: 'calc(var(--radius-sm) - 2px)',
                background: tab === t ? 'var(--color-primary-glow)' : 'transparent',
                color: tab === t ? 'var(--color-primary)' : 'var(--color-text-muted)',
                fontFamily: 'var(--font-body)',
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 0.2s'
              }}
            >
              {t === 'login' ? 'Ingresar' : 'Registrarse'}
            </button>
          ))}
        </div>

        {/* Header */}
        <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 22, fontWeight: 400, marginBottom: 4 }}>
          {tab === 'login' ? 'Bienvenido ' : 'Crear cuenta'}
        </h3>
        <p style={{ fontSize: 14, color: 'var(--color-text-secondary)', marginBottom: 24 }}>
          {tab === 'login' ? 'Ingresa tus datos para continuar' : 'Regístrate y empieza a gestionar tus fincas'}
        </p>

        {tab === 'login'
          ? <LoginForm onLogin={onLogin} />
          : <SignupForm onLogin={onLogin} />
        }
      </div>
    </div>
  )
}