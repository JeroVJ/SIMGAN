import { useState } from 'react'
import toast from 'react-hot-toast'
import { Eye, EyeOff, Satellite, Activity, MapPin, Bell } from 'lucide-react'
import { authApi } from '../services/api'

// ── Helpers ─────────────────────────────────────────────────────────────────

function pwStrength(pw) {
  if (!pw) return 0
  let s = 0
  if (pw.length >= 8)          s++
  if (/[A-Z]/.test(pw))        s++
  if (/[0-9]/.test(pw))        s++
  if (/[^A-Za-z0-9]/.test(pw)) s++
  return s
}

const STRENGTH_COLOR = [null, '#ef4444', '#f59e0b', '#22c55e', '#22c55e']
const STRENGTH_LABEL = [null, 'Débil', 'Regular', 'Buena', 'Fuerte']

// ── Shared field component ───────────────────────────────────────────────────

function Field({ label, error, children }) {
  return (
    <div className="auth-field">
      <label className="auth-field__label">{label}</label>
      {children}
      {error && <span className="auth-field__error">{error}</span>}
    </div>
  )
}

// ── Login Form ───────────────────────────────────────────────────────────────

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

  async function submit(ev) {
    ev?.preventDefault()
    if (!validate()) return
    setLoading(true)
    try {
      const data = await authApi.login(f.email, f.password)
      if (data.token) {
        localStorage.setItem('token', data.token)
        localStorage.setItem('user', JSON.stringify(data.ganadero))
      }
      toast.success('Bienvenido de vuelta')
      onLogin(data)
    } catch (err) {
      const msg = err.response?.data?.message || 'Credenciales incorrectas'
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={submit}>
      <Field label="Correo electrónico" error={e.email}>
        <input
          className={`auth-field__input${e.email ? ' auth-field__input--error' : ''}`}
          type="email"
          placeholder="tu@correo.com"
          value={f.email}
          onChange={set('email')}
          autoComplete="email"
        />
      </Field>

      <Field label="Contraseña" error={e.password}>
        <div className="auth-field__input-wrap">
          <input
            className={`auth-field__input${e.password ? ' auth-field__input--error' : ''}`}
            type={show ? 'text' : 'password'}
            placeholder="Tu contraseña"
            value={f.password}
            onChange={set('password')}
            autoComplete="current-password"
          />
          <button type="button" className="auth-field__toggle" onClick={() => setShow(s => !s)} tabIndex={-1}>
            {show ? <EyeOff size={15} /> : <Eye size={15} />}
          </button>
        </div>
      </Field>

      <button type="submit" className="auth-cta" disabled={loading}>
        {loading
          ? <><span className="spinner" style={{ borderTopColor: '#000', width: 16, height: 16 }} /> Ingresando...</>
          : 'Continuar'}
      </button>
    </form>
  )
}

// ── Signup Form ──────────────────────────────────────────────────────────────

function SignupForm({ onLogin }) {
  const [f, setF]   = useState({ firstName: '', lastName: '', email: '', password: '', confirm: '', terms: false })
  const [e, setE]   = useState({})
  const [showPw, setShowPw] = useState(false)
  const [loading, setLoading] = useState(false)
  const set = k => ev => setF(p => ({ ...p, [k]: ev.target.type === 'checkbox' ? ev.target.checked : ev.target.value }))
  const str = pwStrength(f.password)

  function validate() {
    const err = {}
    if (!f.firstName.trim())                 err.firstName = 'Requerido'
    if (!f.lastName.trim())                  err.lastName  = 'Requerido'
    if (!f.email)                             err.email     = 'Requerido'
    else if (!/\S+@\S+\.\S+/.test(f.email)) err.email     = 'Correo inválido'
    if (f.password.length < 8)               err.password  = 'Mínimo 8 caracteres'
    if (f.password !== f.confirm)            err.confirm   = 'Las contraseñas no coinciden'
    if (!f.terms)                            err.terms     = 'Debes aceptar los términos'
    setE(err)
    return !Object.keys(err).length
  }

  async function submit(ev) {
    ev?.preventDefault()
    if (!validate()) return
    setLoading(true)
    try {
      const data = await authApi.register(f.firstName, f.lastName, f.email, f.password)
      if (data.token) {
        localStorage.setItem('token', data.token)
        localStorage.setItem('user', JSON.stringify(data.ganadero))
      }
      toast.success('Cuenta creada exitosamente')
      onLogin(data)
    } catch (err) {
      const msg = err.response?.data?.message || 'Error al crear la cuenta'
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={submit}>
      <div className="auth-grid-2">
        <Field label="Nombre" error={e.firstName}>
          <input
            className={`auth-field__input${e.firstName ? ' auth-field__input--error' : ''}`}
            placeholder="Juan"
            value={f.firstName}
            onChange={set('firstName')}
            autoComplete="given-name"
          />
        </Field>
        <Field label="Apellido" error={e.lastName}>
          <input
            className={`auth-field__input${e.lastName ? ' auth-field__input--error' : ''}`}
            placeholder="Pérez"
            value={f.lastName}
            onChange={set('lastName')}
            autoComplete="family-name"
          />
        </Field>
      </div>

      <Field label="Correo electrónico" error={e.email}>
        <input
          className={`auth-field__input${e.email ? ' auth-field__input--error' : ''}`}
          type="email"
          placeholder="tu@correo.com"
          value={f.email}
          onChange={set('email')}
          autoComplete="email"
        />
      </Field>

      <Field label="Contraseña" error={e.password}>
        <div className="auth-field__input-wrap">
          <input
            className={`auth-field__input${e.password ? ' auth-field__input--error' : ''}`}
            type={showPw ? 'text' : 'password'}
            placeholder="Mínimo 8 caracteres"
            value={f.password}
            onChange={set('password')}
            autoComplete="new-password"
          />
          <button type="button" className="auth-field__toggle" onClick={() => setShowPw(s => !s)} tabIndex={-1}>
            {showPw ? <EyeOff size={15} /> : <Eye size={15} />}
          </button>
        </div>
        {f.password && (
          <div style={{ marginTop: 8 }}>
            <div className="auth-strength-bar">
              {[1, 2, 3, 4].map(i => (
                <div
                  key={i}
                  className="auth-strength-seg"
                  style={{ background: i <= str ? (STRENGTH_COLOR[str] || '#22c55e') : undefined }}
                />
              ))}
            </div>
            {STRENGTH_LABEL[str] && (
              <span style={{ fontSize: 11, fontWeight: 600, color: STRENGTH_COLOR[str], marginTop: 4, display: 'block' }}>
                {STRENGTH_LABEL[str]}
              </span>
            )}
          </div>
        )}
      </Field>

      <Field label="Confirmar contraseña" error={e.confirm}>
        <input
          className={`auth-field__input${e.confirm ? ' auth-field__input--error' : ''}`}
          type="password"
          placeholder="Repite tu contraseña"
          value={f.confirm}
          onChange={set('confirm')}
          autoComplete="new-password"
        />
      </Field>

      <div className="auth-terms">
        <input type="checkbox" checked={f.terms} onChange={set('terms')} id="terms" />
        <label htmlFor="terms" className="auth-terms__text">
          Acepto los{' '}
          <button type="button" className="auth-terms__link">términos y condiciones</button>
          {' '}y la{' '}
          <button type="button" className="auth-terms__link">política de privacidad</button>
        </label>
      </div>
      {e.terms && <span className="auth-field__error" style={{ display: 'block', marginBottom: 12 }}>{e.terms}</span>}

      <button type="submit" className="auth-cta" disabled={loading}>
        {loading
          ? <><span className="spinner" style={{ borderTopColor: '#000', width: 16, height: 16 }} /> Creando cuenta...</>
          : 'Crear cuenta'}
      </button>
    </form>
  )
}

// ── Left panel features ──────────────────────────────────────────────────────

const FEATURES = [
  {
    Icon: Satellite,
    label: 'Analíticas satelitales NDVI',
    desc: 'Monitorea el estado de tus pasturas con imágenes de Planet Labs y Sentinel-2.',
  },
  {
    Icon: Activity,
    label: 'Alertas en tiempo real',
    desc: 'Recibe notificaciones por correo cuando el NDVI de una parcela sea crítico.',
  },
  {
    Icon: MapPin,
    label: 'Gestión geoespacial',
    desc: 'Dibuja y delimita tus terrenos y potreros con precisión en el mapa.',
  },
  {
    Icon: Bell,
    label: 'Reportes PDF automáticos',
    desc: 'Genera informes completos con mapas, estadísticas y recomendaciones.',
  },
]

// ── Page ─────────────────────────────────────────────────────────────────────

export default function AuthPage({ onLogin }) {
  const [tab, setTab] = useState('login')

  return (
    <div className="auth-root">
      {/* Left branding panel */}
      <div className="auth-panel--left">
        <div className="auth-brand">
          <div className="auth-brand__icon">
            <Satellite size={18} strokeWidth={1.75} />
          </div>
          <span className="auth-brand__name">SIMGAN</span>
        </div>

        <div className="auth-hero">
          <h1 className="auth-hero__title">
            Gestión inteligente<br />
            para tu <span>ganadería</span>
          </h1>
          <p className="auth-hero__desc">
            Monitorea tus fincas, analiza el estado de los pastizales con
            tecnología satelital y toma mejores decisiones de pastoreo.
          </p>
          <div className="auth-features">
            {FEATURES.map(({ Icon, label, desc }) => (
              <div key={label} className="auth-feature">
                <div className="auth-feature__icon">
                  <Icon size={14} strokeWidth={2} />
                </div>
                <div className="auth-feature__body">
                  <span className="auth-feature__label">{label}</span>
                  <span className="auth-feature__desc">{desc}</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="auth-footer">
          SIMGAN v0.2 · Planet Labs · Sentinel-2
        </div>
      </div>

      {/* Right form panel */}
      <div className="auth-panel--right">
        <div className="auth-form-wrap">
          <div className="auth-tabs">
            <button
              type="button"
              className={`auth-tab${tab === 'login' ? ' auth-tab--active' : ''}`}
              onClick={() => setTab('login')}
            >
              Ingresar
            </button>
            <button
              type="button"
              className={`auth-tab${tab === 'signup' ? ' auth-tab--active' : ''}`}
              onClick={() => setTab('signup')}
            >
              Registrarse
            </button>
          </div>

          <h2 className="auth-form-wrap__heading">
            {tab === 'login' ? 'Bienvenido de vuelta' : 'Crear cuenta'}
          </h2>
          <p className="auth-form-wrap__sub">
            {tab === 'login'
              ? 'Ingresa tus datos para acceder a tu panel.'
              : 'Regístrate con tu correo y empieza a gestionar tus fincas.'}
          </p>

          {tab === 'login'
            ? <LoginForm key="login" onLogin={onLogin} />
            : <SignupForm key="signup" onLogin={onLogin} />
          }

          <div className="auth-switch">
            {tab === 'login' ? '¿No tienes cuenta?' : '¿Ya tienes cuenta?'}
            <button type="button" onClick={() => setTab(tab === 'login' ? 'signup' : 'login')}>
              {tab === 'login' ? 'Regístrate' : 'Inicia sesión'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
