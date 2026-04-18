import { useState } from 'react'
import toast from 'react-hot-toast'
import {
  Leaf, Eye, EyeOff, Loader2, ArrowRight, ArrowLeft,
  MapPin, Satellite, Activity, Beef, MailCheck,
} from 'lucide-react'
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
  { label: 'Fuerte',   color: 'var(--color-primary)' },
]

function FieldError({ msg }) {
  if (!msg) return null
  return (
    <span style={{ fontSize: 12, color: 'var(--color-danger)', marginTop: 4 }}>
      {msg}
    </span>
  )
}

// ── Login ─────────────────────────────────────────────────────────────────────
function LoginForm({ onLogin, onForgot }) {
  const [f, setF] = useState({ email: '', password: '' })
  const [e, setE] = useState({})
  const [show, setShow] = useState(false)
  const [loading, setLoading] = useState(false)
  const set = k => ev => setF(p => ({ ...p, [k]: ev.target.value }))

  function validate() {
    const err = {}
    if (!f.email) err.email = 'El correo es requerido'
    else if (!/\S+@\S+\.\S+/.test(f.email)) err.email = 'Correo inválido'
    if (!f.password) err.password = 'La contraseña es requerida'
    setE(err)
    return !Object.keys(err).length
  }

  async function submit(e) {
    e?.preventDefault()
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
      <div className="form-group" style={{ marginBottom: 16 }}>
        <label>Correo electrónico</label>
        <input
          type="email"
          placeholder="tu@correo.com"
          value={f.email}
          onChange={set('email')}
          style={e.email ? { borderColor: 'var(--color-danger)' } : {}}
        />
        <FieldError msg={e.email} />
      </div>

      <div className="form-group" style={{ marginBottom: 14 }}>
        <label>Contraseña</label>
        <div style={{ position: 'relative' }}>
          <input
            type={show ? 'text' : 'password'}
            placeholder="Tu contraseña"
            value={f.password}
            onChange={set('password')}
            style={{ width: '100%', paddingRight: 42, ...(e.password ? { borderColor: 'var(--color-danger)' } : {}) }}
          />
          <button
            type="button"
            onClick={() => setShow(s => !s)}
            aria-label={show ? 'Ocultar contraseña' : 'Ver contraseña'}
            style={{
              position: 'absolute',
              right: 8,
              top: '50%',
              transform: 'translateY(-50%)',
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: 'var(--color-text-muted)',
              padding: 6,
              display: 'inline-flex',
              alignItems: 'center',
              borderRadius: 6,
            }}
          >
            {show ? <EyeOff size={15} strokeWidth={1.9} /> : <Eye size={15} strokeWidth={1.9} />}
          </button>
        </div>
        <FieldError msg={e.password} />
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 22 }}>
        <button type="button" onClick={onForgot} style={{
          background: 'none', border: 'none', cursor: 'pointer',
          color: 'var(--color-text-secondary)', fontSize: 12.5, fontWeight: 500,
          fontFamily: 'inherit', padding: 0,
        }}>
          ¿Olvidaste tu contraseña?
        </button>
      </div>

      <button
        type="submit"
        className="comp-btn comp-btn--primary"
        style={{ width: '100%', justifyContent: 'center' }}
        disabled={loading}
      >
        {loading ? (
          <>
            <Loader2 size={15} className="oh-spin" /> Ingresando...
          </>
        ) : (
          <>
            Ingresar
            <ArrowRight size={14} strokeWidth={2} />
          </>
        )}
      </button>
    </form>
  )
}

// ── Forgot Password ───────────────────────────────────────────────────────────
function ForgotPasswordForm({ onBack }) {
  const [email, setEmail] = useState('')
  const [err, setErr] = useState('')
  const [loading, setLoading] = useState(false)
  const [sent, setSent] = useState(false)

  async function submit(e) {
    e?.preventDefault()
    if (!email) { setErr('El correo es requerido'); return }
    if (!/\S+@\S+\.\S+/.test(email)) { setErr('Correo inválido'); return }
    setErr('')
    setLoading(true)
    try {
      await authApi.forgotPassword(email.trim())
      setSent(true)
    } catch {
      setSent(true)
    } finally {
      setLoading(false)
    }
  }

  if (sent) {
    return (
      <div style={{ textAlign: 'center', padding: '8px 0 4px' }}>
        <div style={{
          width: 48, height: 48, margin: '0 auto 16px',
          borderRadius: '50%',
          background: 'rgba(74, 222, 128, 0.12)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: 'var(--color-primary)',
        }}>
          <MailCheck size={22} strokeWidth={1.9} />
        </div>
        <p style={{ fontSize: 14, color: 'var(--color-text)', marginBottom: 8, fontWeight: 600 }}>
          Revisa tu correo
        </p>
        <p style={{ fontSize: 13, color: 'var(--color-text-secondary)', lineHeight: 1.6, marginBottom: 22 }}>
          Si <strong>{email}</strong> pertenece a una cuenta, recibirás un enlace
          para restablecer tu contraseña. El enlace expira en 30 minutos.
        </p>
        <button
          type="button"
          onClick={onBack}
          className="comp-btn comp-btn--primary"
          style={{ width: '100%', justifyContent: 'center' }}
        >
          <ArrowLeft size={14} strokeWidth={2} /> Volver a ingresar
        </button>
      </div>
    )
  }

  return (
    <form onSubmit={submit}>
      <div className="form-group" style={{ marginBottom: 16 }}>
        <label>Correo electrónico</label>
        <input
          type="email"
          placeholder="tu@correo.com"
          value={email}
          onChange={ev => { setEmail(ev.target.value); if (err) setErr('') }}
          autoFocus
          style={err ? { borderColor: 'var(--color-danger)' } : {}}
        />
        <FieldError msg={err} />
      </div>
      <p style={{ fontSize: 12.5, color: 'var(--color-text-secondary)', marginBottom: 18, lineHeight: 1.55 }}>
        Te enviaremos un enlace para restablecer tu contraseña. Válido por 30 minutos.
      </p>
      <button
        type="submit"
        className="comp-btn comp-btn--primary"
        style={{ width: '100%', justifyContent: 'center', marginBottom: 10 }}
        disabled={loading}
      >
        {loading
          ? <><Loader2 size={15} className="oh-spin" /> Enviando...</>
          : <>Enviar enlace <ArrowRight size={14} strokeWidth={2} /></>}
      </button>
      <button
        type="button"
        onClick={onBack}
        style={{
          width: '100%', textAlign: 'center',
          background: 'none', border: 'none', cursor: 'pointer',
          color: 'var(--color-text-secondary)', fontSize: 12.5, fontWeight: 500,
          fontFamily: 'inherit', padding: '8px 0',
        }}
      >
        ← Volver a ingresar
      </button>
    </form>
  )
}

// ── Sign Up ───────────────────────────────────────────────────────────────────
function SignupForm({ onLogin }) {
  const [f, setF] = useState({ firstName: '', lastName: '', email: '', password: '', confirm: '', terms: false })
  const [e, setE] = useState({})
  const [showPw, setShowPw] = useState(false)
  const [loading, setLoading] = useState(false)
  const set = k => ev => setF(p => ({
    ...p,
    [k]: ev.target.type === 'checkbox' ? ev.target.checked : ev.target.value,
  }))

  const str = pwStrength(f.password)
  const meta = strMeta[str]

  function validate() {
    const err = {}
    if (!f.firstName.trim()) err.firstName = 'Requerido'
    if (!f.lastName.trim()) err.lastName = 'Requerido'
    if (!f.email) err.email = 'Requerido'
    else if (!/\S+@\S+\.\S+/.test(f.email)) err.email = 'Correo inválido'
    if (f.password.length < 8) err.password = 'Mínimo 8 caracteres'
    else if (!/[A-Za-z]/.test(f.password) || !/[0-9]/.test(f.password)) {
      err.password = 'Debe incluir letras y números'
    }
    if (!f.confirm) err.confirm = 'Confirma tu contraseña'
    else if (f.password !== f.confirm) err.confirm = 'No coinciden'
    if (!f.terms) err.terms = 'Acepta los términos'
    setE(err)
    return !Object.keys(err).length
  }

  async function submit(e) {
    e?.preventDefault()
    if (!validate()) return
    setLoading(true)
    try {
      const data = await authApi.register(f.firstName, f.lastName, f.email, f.password)
      if (data.token) {
        localStorage.setItem('token', data.token)
        localStorage.setItem('user', JSON.stringify(data.ganadero))
      }
      toast.success('Cuenta creada')
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
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 14 }}>
        <div className="form-group">
          <label>Nombre</label>
          <input
            placeholder="Juan"
            value={f.firstName}
            onChange={set('firstName')}
            style={e.firstName ? { borderColor: 'var(--color-danger)' } : {}}
          />
          <FieldError msg={e.firstName} />
        </div>
        <div className="form-group">
          <label>Apellido</label>
          <input
            placeholder="Pérez"
            value={f.lastName}
            onChange={set('lastName')}
            style={e.lastName ? { borderColor: 'var(--color-danger)' } : {}}
          />
          <FieldError msg={e.lastName} />
        </div>
      </div>

      <div className="form-group" style={{ marginBottom: 14 }}>
        <label>Correo electrónico</label>
        <input
          type="email"
          placeholder="tu@correo.com"
          value={f.email}
          onChange={set('email')}
          style={e.email ? { borderColor: 'var(--color-danger)' } : {}}
        />
        <FieldError msg={e.email} />
      </div>

      <div className="form-group" style={{ marginBottom: 14 }}>
        <label>Contraseña</label>
        <div style={{ position: 'relative' }}>
          <input
            type={showPw ? 'text' : 'password'}
            placeholder="Mínimo 8 caracteres"
            value={f.password}
            onChange={set('password')}
            style={{ width: '100%', paddingRight: 42, ...(e.password ? { borderColor: 'var(--color-danger)' } : {}) }}
          />
          <button
            type="button"
            onClick={() => setShowPw(s => !s)}
            aria-label={showPw ? 'Ocultar contraseña' : 'Ver contraseña'}
            style={{
              position: 'absolute',
              right: 8,
              top: '50%',
              transform: 'translateY(-50%)',
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: 'var(--color-text-muted)',
              padding: 6,
              display: 'inline-flex',
              alignItems: 'center',
              borderRadius: 6,
            }}
          >
            {showPw ? <EyeOff size={15} strokeWidth={1.9} /> : <Eye size={15} strokeWidth={1.9} />}
          </button>
        </div>
        {f.password && (
          <div style={{ marginTop: 8 }}>
            <div style={{ display: 'flex', gap: 4 }}>
              {[1, 2, 3, 4].map(i => (
                <div
                  key={i}
                  style={{
                    flex: 1,
                    height: 3,
                    borderRadius: 2,
                    background: i <= str ? meta.color : 'var(--color-border)',
                    transition: 'background .3s',
                  }}
                />
              ))}
            </div>
            <span style={{
              fontSize: 11,
              fontWeight: 600,
              color: meta?.color || 'var(--color-text-muted)',
              marginTop: 4,
              display: 'inline-block',
            }}>
              {meta?.label}
            </span>
          </div>
        )}
        <FieldError msg={e.password} />
      </div>

      <div className="form-group" style={{ marginBottom: 14 }}>
        <label>Confirmar contraseña</label>
        <input
          type="password"
          placeholder="Repite tu contraseña"
          value={f.confirm}
          onChange={set('confirm')}
          style={e.confirm ? { borderColor: 'var(--color-danger)' } : {}}
        />
        <FieldError msg={e.confirm} />
      </div>

      <label style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 10,
        marginBottom: 20,
        cursor: 'pointer',
      }}>
        <input
          type="checkbox"
          checked={f.terms}
          onChange={set('terms')}
          style={{
            width: 15,
            height: 15,
            accentColor: 'var(--color-primary)',
            marginTop: 2,
            flexShrink: 0,
            cursor: 'pointer',
          }}
        />
        <span style={{ fontSize: 12.5, color: 'var(--color-text-secondary)', lineHeight: 1.6 }}>
          Acepto los{' '}
          <span style={{ color: 'var(--color-primary)', fontWeight: 500 }}>
            términos y condiciones
          </span>{' '}
          y la{' '}
          <span style={{ color: 'var(--color-primary)', fontWeight: 500 }}>
            política de privacidad
          </span>.
        </span>
      </label>
      <FieldError msg={e.terms} />

      <button
        type="submit"
        className="comp-btn comp-btn--primary"
        style={{ width: '100%', justifyContent: 'center', marginTop: 4 }}
        disabled={loading}
      >
        {loading ? (
          <>
            <Loader2 size={15} className="oh-spin" /> Creando cuenta...
          </>
        ) : (
          <>
            Crear cuenta
            <ArrowRight size={14} strokeWidth={2} />
          </>
        )}
      </button>
    </form>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────
export default function AuthPage({ onLogin }) {
  const [tab, setTab] = useState('login')
  const forgot = tab === 'forgot'

  return (
    <div className="auth-shell">
      {/* Art side */}
      <div className="auth-shell__art">
        <div className="auth-shell__art-brand">
          <span className="auth-shell__art-logo">
            <Leaf size={22} strokeWidth={2} />
          </span>
          <span className="auth-shell__art-name">SIMGAN</span>
        </div>

        <div className="auth-shell__art-content">
          <h1 className="auth-shell__art-title">
            Ganadería inteligente,<br />desde el satélite al potrero.
          </h1>
          <p className="auth-shell__art-desc">
            Monitorea salud de pasturas con NDVI, gestiona lotes de ganado y
            optimiza rotación — todo desde un panel unificado.
          </p>

          <div className="auth-shell__art-features">
            <div className="auth-shell__art-feature">
              <span className="auth-shell__art-feature-icon">
                <Satellite size={14} strokeWidth={1.9} />
              </span>
              Índices NDVI satelitales
            </div>
            <div className="auth-shell__art-feature">
              <span className="auth-shell__art-feature-icon">
                <MapPin size={14} strokeWidth={1.9} />
              </span>
              Mapeo de potreros
            </div>
            <div className="auth-shell__art-feature">
              <span className="auth-shell__art-feature-icon">
                <Beef size={14} strokeWidth={1.9} />
              </span>
              Gestión de lotes
            </div>
            <div className="auth-shell__art-feature">
              <span className="auth-shell__art-feature-icon">
                <Activity size={14} strokeWidth={1.9} />
              </span>
              Sensores IoT
            </div>
          </div>
        </div>

        <div className="auth-shell__art-footer">
          Planet Labs · Sentinel-2 · NDVI · SIMGAN v0.2
        </div>
      </div>

      {/* Form side */}
      <div className="auth-shell__form">
        <div className="auth-form-container">
          {!forgot && (
            <div className="auth-tabs" role="tablist">
              <button
                type="button"
                role="tab"
                aria-selected={tab === 'login'}
                onClick={() => setTab('login')}
                className={`auth-tab${tab === 'login' ? ' auth-tab--active' : ''}`}
              >
                Ingresar
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={tab === 'signup'}
                onClick={() => setTab('signup')}
                className={`auth-tab${tab === 'signup' ? ' auth-tab--active' : ''}`}
              >
                Registrarse
              </button>
            </div>
          )}

          <h3 className="auth-title">
            {tab === 'login' && 'Bienvenido'}
            {tab === 'signup' && 'Crear cuenta'}
            {tab === 'forgot' && 'Recuperar contraseña'}
          </h3>
          <p className="auth-subtitle">
            {tab === 'login' && 'Ingresa tus datos para continuar.'}
            {tab === 'signup' && 'Regístrate y empieza a gestionar tus fincas.'}
            {tab === 'forgot' && 'Te enviaremos un enlace a tu correo.'}
          </p>

          {tab === 'login' && <LoginForm onLogin={onLogin} onForgot={() => setTab('forgot')} />}
          {tab === 'signup' && <SignupForm onLogin={onLogin} />}
          {tab === 'forgot' && <ForgotPasswordForm onBack={() => setTab('login')} />}
        </div>
      </div>

      <style>{`
        .oh-spin { animation: oh-spin 0.7s linear infinite; }
        @keyframes oh-spin { to { transform: rotate(360deg); } }
      `}</style>
    </div>
  )
}
