import { useState, useMemo } from 'react'
import { useSearchParams, useNavigate, Link } from 'react-router-dom'
import toast from 'react-hot-toast'
import { Leaf, Eye, EyeOff, Loader2, ArrowRight, CheckCircle2, AlertTriangle } from 'lucide-react'
import { authApi } from '../services/api'

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

export default function ResetPasswordPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const token = params.get('token') || ''

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [show, setShow] = useState(false)
  const [loading, setLoading] = useState(false)
  const [done, setDone] = useState(false)
  const [err, setErr] = useState({})

  const str = pwStrength(password)
  const meta = strMeta[str]

  const missingToken = useMemo(() => !token.trim(), [token])

  async function submit(e) {
    e?.preventDefault()
    const errs = {}
    if (password.length < 8) errs.password = 'Mínimo 8 caracteres'
    else if (!/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) {
      errs.password = 'Debe incluir letras y números'
    }
    if (!confirm) errs.confirm = 'Confirma tu contraseña'
    else if (password !== confirm) errs.confirm = 'No coinciden'
    setErr(errs)
    if (Object.keys(errs).length) return

    setLoading(true)
    try {
      await authApi.resetPassword(token, password)
      setDone(true)
      toast.success('Contraseña actualizada')
    } catch (e) {
      const msg = e.response?.data?.message || 'No se pudo restablecer la contraseña'
      toast.error(msg)
      setErr({ form: msg })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: 24,
      background: 'var(--color-bg)',
    }}>
      <div style={{
        width: '100%',
        maxWidth: 420,
        padding: '36px 32px 30px',
        background: 'var(--color-surface)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 22 }}>
          <span style={{
            width: 34, height: 34, borderRadius: 10,
            background: 'var(--color-primary-glow)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            color: 'var(--color-primary)',
          }}>
            <Leaf size={18} strokeWidth={2} />
          </span>
          <span style={{
            fontWeight: 700, letterSpacing: '.2em', fontSize: 13,
            color: 'var(--color-text)',
          }}>
            SIMGAN
          </span>
        </div>

        {missingToken ? (
          <>
            <h3 className="auth-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <AlertTriangle size={18} strokeWidth={2} style={{ color: 'var(--color-danger)' }} />
              Enlace inválido
            </h3>
            <p className="auth-subtitle">
              Este enlace no contiene un token válido. Solicita un nuevo enlace de recuperación.
            </p>
            <Link to="/" className="comp-btn comp-btn--primary" style={{ width: '100%', justifyContent: 'center', marginTop: 8 }}>
              Volver a ingresar
            </Link>
          </>
        ) : done ? (
          <>
            <h3 className="auth-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <CheckCircle2 size={18} strokeWidth={2} style={{ color: 'var(--color-primary)' }} />
              Listo
            </h3>
            <p className="auth-subtitle">
              Tu contraseña se actualizó correctamente. Ahora puedes ingresar.
            </p>
            <button
              onClick={() => navigate('/', { replace: true })}
              className="comp-btn comp-btn--primary"
              style={{ width: '100%', justifyContent: 'center', marginTop: 8 }}
            >
              Ir al inicio de sesión <ArrowRight size={14} strokeWidth={2} />
            </button>
          </>
        ) : (
          <>
            <h3 className="auth-title">Restablecer contraseña</h3>
            <p className="auth-subtitle">
              Elige una contraseña nueva para tu cuenta.
            </p>

            <form onSubmit={submit}>
              <div className="form-group" style={{ marginBottom: 14 }}>
                <label>Nueva contraseña</label>
                <div style={{ position: 'relative' }}>
                  <input
                    type={show ? 'text' : 'password'}
                    placeholder="Mínimo 8 caracteres"
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                    autoFocus
                    style={{ width: '100%', paddingRight: 42, ...(err.password ? { borderColor: 'var(--color-danger)' } : {}) }}
                  />
                  <button
                    type="button"
                    onClick={() => setShow(s => !s)}
                    aria-label={show ? 'Ocultar' : 'Ver'}
                    style={{
                      position: 'absolute', right: 8, top: '50%',
                      transform: 'translateY(-50%)',
                      background: 'transparent', border: 'none', cursor: 'pointer',
                      color: 'var(--color-text-muted)', padding: 6,
                      display: 'inline-flex', alignItems: 'center', borderRadius: 6,
                    }}
                  >
                    {show ? <EyeOff size={15} strokeWidth={1.9} /> : <Eye size={15} strokeWidth={1.9} />}
                  </button>
                </div>

                {password && (
                  <div style={{ marginTop: 8 }}>
                    <div style={{ display: 'flex', gap: 4 }}>
                      {[1, 2, 3, 4].map(i => (
                        <div key={i} style={{
                          flex: 1, height: 3, borderRadius: 2,
                          background: i <= str ? meta.color : 'var(--color-border)',
                          transition: 'background .3s',
                        }} />
                      ))}
                    </div>
                    <span style={{
                      fontSize: 11, fontWeight: 600,
                      color: meta?.color || 'var(--color-text-muted)',
                      marginTop: 4, display: 'inline-block',
                    }}>
                      {meta?.label}
                    </span>
                  </div>
                )}
                {err.password && (
                  <span style={{ fontSize: 12, color: 'var(--color-danger)', marginTop: 4, display: 'block' }}>
                    {err.password}
                  </span>
                )}
              </div>

              <div className="form-group" style={{ marginBottom: 18 }}>
                <label>Confirmar contraseña</label>
                <input
                  type="password"
                  placeholder="Repite tu contraseña"
                  value={confirm}
                  onChange={e => setConfirm(e.target.value)}
                  style={err.confirm ? { borderColor: 'var(--color-danger)' } : {}}
                />
                {err.confirm && (
                  <span style={{ fontSize: 12, color: 'var(--color-danger)', marginTop: 4, display: 'block' }}>
                    {err.confirm}
                  </span>
                )}
              </div>

              {err.form && (
                <div style={{
                  padding: '10px 14px',
                  background: 'rgba(239, 68, 68, 0.08)',
                  border: '1px solid rgba(239, 68, 68, 0.35)',
                  borderRadius: 'var(--radius-sm)',
                  marginBottom: 14,
                  color: 'var(--color-danger)',
                  fontSize: 12.5,
                }}>
                  {err.form}
                </div>
              )}

              <button
                type="submit"
                className="comp-btn comp-btn--primary"
                style={{ width: '100%', justifyContent: 'center' }}
                disabled={loading}
              >
                {loading
                  ? <><Loader2 size={15} className="oh-spin" /> Guardando...</>
                  : <>Actualizar contraseña <ArrowRight size={14} strokeWidth={2} /></>}
              </button>
            </form>

            <div style={{ textAlign: 'center', marginTop: 14 }}>
              <Link to="/" style={{
                color: 'var(--color-text-secondary)', fontSize: 12.5, fontWeight: 500,
                textDecoration: 'none',
              }}>
                ← Volver a ingresar
              </Link>
            </div>
          </>
        )}
      </div>

      <style>{`
        .oh-spin { animation: oh-spin 0.7s linear infinite; }
        @keyframes oh-spin { to { transform: rotate(360deg); } }
      `}</style>
    </div>
  )
}
