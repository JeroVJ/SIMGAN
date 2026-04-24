import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useLote } from '../hooks'
import Spinner from '../components/Spinner'
import EmptyState from '../components/EmptyState'
import ConfirmDialog from '../components/ConfirmDialog'

const TIPOS = ['NOVILLO', 'NOVILLA', 'TORO', 'VACA']
const TIPO_COLORS = {
  NOVILLO: 'var(--color-info)',
  NOVILLA: '#ec4899',
  TORO:    'var(--color-warning)',
  VACA:    '#a855f7',
}

export default function LoteDetailPage() {
  const { loteId } = useParams()
  const navigate = useNavigate()
  const { lote, loading, addGanado, addGanadoBatch, updateGanado, deleteGanado } = useLote(loteId)

  const [activeTab, setActiveTab]         = useState('ganado')
  const [showAddForm, setShowAddForm]     = useState(false)
  const [showBatchForm, setShowBatchForm] = useState(false)
  const [editingId, setEditingId]         = useState(null)
  const [editPeso, setEditPeso]           = useState('')
  const [editNumeracion, setEditNumeracion] = useState('')
  const [editTipo, setEditTipo]           = useState('')
  const [confirm, setConfirm]             = useState(null)

  const [addForm, setAddForm] = useState({
    numeracion: '', tipo: 'NOVILLO', pesoInicial: '', pesoActual: '',
  })
  const [batchForm, setBatchForm] = useState({
    tipo: 'NOVILLO', prefix: '', startNum: 1, count: 5, pesoInicial: '',
  })

  async function handleAdd(e) {
    e.preventDefault()
    if (!addForm.numeracion.trim() || !addForm.pesoInicial) return
    try {
      await addGanado({
        numeracion: addForm.numeracion,
        tipo: addForm.tipo,
        pesoInicial: Number(addForm.pesoInicial),
        pesoActual: addForm.pesoActual ? Number(addForm.pesoActual) : Number(addForm.pesoInicial),
      })
      setAddForm({ numeracion: '', tipo: 'NOVILLO', pesoInicial: '', pesoActual: '' })
      setShowAddForm(false)
    } catch { /* toast shown in hook */ }
  }

  async function handleBatchAdd(e) {
    e.preventDefault()
    if (!batchForm.pesoInicial || !batchForm.count) return
    const items = []
    for (let i = 0; i < batchForm.count; i++) {
      const num = batchForm.prefix + String(batchForm.startNum + i).padStart(3, '0')
      items.push({ numeracion: num, tipo: batchForm.tipo, pesoInicial: Number(batchForm.pesoInicial), pesoActual: Number(batchForm.pesoInicial) })
    }
    try {
      await addGanadoBatch(items)
      setShowBatchForm(false)
      setBatchForm({ tipo: 'NOVILLO', prefix: '', startNum: 1, count: 5, pesoInicial: '' })
    } catch { /* toast shown in hook */ }
  }

  function startEdit(g) {
    setEditingId(g.id)
    setEditPeso(String(g.pesoActual))
    setEditNumeracion(g.numeracion)
    setEditTipo(g.tipo)
  }

  async function handleSaveEdit(ganadoId) {
    try {
      await updateGanado(ganadoId, { numeracion: editNumeracion, tipo: editTipo, pesoActual: Number(editPeso) })
      setEditingId(null)
    } catch { /* toast shown in hook */ }
  }

  function handleDeleteGanado(g) {
    setConfirm({
      title: `Eliminar ${g.numeracion}`,
      message: `¿Eliminar este animal del lote? Esta acción no se puede deshacer.`,
      confirmLabel: 'Eliminar',
      onConfirm: async () => {
        try { await deleteGanado(g.id) } catch { /* toast shown in hook */ }
      },
    })
  }

  if (loading) return <Spinner page label="Cargando lote..." />
  if (!lote) { navigate(-1); return null }

  const ganados = lote.ganados || []
  const isClosed = !!lote.fechaSalida

  const tipoCount = {}
  TIPOS.forEach(t => { tipoCount[t] = ganados.filter(g => g.tipo === t).length })
  const totalPesoInicial = ganados.reduce((s, g) => s + (g.pesoInicial || 0), 0)
  const totalPesoActual = ganados.reduce((s, g) => s + (g.pesoActual || 0), 0)
  const totalGanancia = totalPesoActual - totalPesoInicial

  return (
    <div className="page-container">
      <ConfirmDialog
        open={!!confirm}
        title={confirm?.title}
        message={confirm?.message}
        confirmLabel={confirm?.confirmLabel || 'Confirmar'}
        variant="danger"
        onConfirm={() => { confirm?.onConfirm?.(); setConfirm(null) }}
        onCancel={() => setConfirm(null)}
      />

      {/* Breadcrumb */}
      <div className="breadcrumb">
        <Link to="/farms">Fincas</Link>
        <span>›</span>
        <Link to={`/terrains/${lote.terrainId}/lotes`}>Lotes</Link>
        <span>›</span>
        <span>{lote.name}</span>
      </div>

      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <div>
          <h1 style={{ margin: 0, color: 'var(--color-text)', fontSize: 28 }}>
            {lote.name}
            {isClosed && <span style={{ fontSize: 14, color: 'var(--color-negative)', marginLeft: 10 }}>CERRADO</span>}
          </h1>
          <p style={{ margin: '4px 0 0', color: 'var(--color-text-secondary)', fontSize: 13 }}>
            {lote.terrainName} · Ingreso: {lote.fechaIngreso}
            {lote.fechaSalida && ` · Salida: ${lote.fechaSalida}`}
            {lote.currentParcelName && <> · Potrero: <strong style={{ color: 'var(--color-primary)' }}>{lote.currentParcelName}</strong></>}
          </p>
        </div>
        <button onClick={() => navigate(`/terrains/${lote.terrainId}/lotes`)} className="action-btn">
          ← Volver a Lotes
        </button>
      </div>

      {/* Summary Cards */}
      <div className="lote-summary-grid">
        <div className="summary-card">
          <div className="summary-value">{ganados.length}</div>
          <div className="summary-label">Cabezas</div>
        </div>
        <div className="summary-card">
          <div className="summary-value">{lote.pesoPromedioActual ? lote.pesoPromedioActual + ' kg' : '—'}</div>
          <div className="summary-label">Peso Promedio</div>
        </div>
        <div className="summary-card">
          <div className="summary-value" style={{ color: totalGanancia > 0 ? 'var(--color-positive)' : totalGanancia < 0 ? 'var(--color-negative)' : 'var(--color-text)' }}>
            {ganados.length > 0 ? (totalGanancia > 0 ? '+' : '') + totalGanancia.toFixed(1) + ' kg' : '—'}
          </div>
          <div className="summary-label">Ganancia Total</div>
        </div>
        <div className="summary-card">
          <div className="summary-value">{totalPesoActual.toFixed(0)} kg</div>
          <div className="summary-label">Peso Total Lote</div>
        </div>
      </div>

      {/* Tipo breakdown */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20, flexWrap: 'wrap' }}>
        {TIPOS.map(t => tipoCount[t] > 0 && (
          <span key={t} style={{
            padding: '4px 12px', borderRadius: 20, fontSize: 13,
            background: 'var(--color-surface-2)', color: TIPO_COLORS[t],
            border: `1px solid var(--color-border)`,
          }}>
            {t}: {tipoCount[t]}
          </span>
        ))}
      </div>

      {/* Tabs */}
      <div className="ndvi-tabs" style={{ marginBottom: 20 }}>
        {[{ key: 'ganado', label: 'Ganado' }, { key: 'historial', label: ' Historial Terrenos' }].map(tab => (
          <button
            key={tab.key}
            className={`ndvi-tab${activeTab === tab.key ? ' ndvi-tab--active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* TAB: Ganado */}
      {activeTab === 'ganado' && (
        <div>
          {!isClosed && (
            <div style={{ display: 'flex', gap: 10, marginBottom: 16 }}>
              <button className="action-btn action-btn--primary action-btn--small"
                onClick={() => { setShowAddForm(!showAddForm); setShowBatchForm(false) }}>
                + Agregar Animal
              </button>
              <button className="action-btn action-btn--small"
                onClick={() => { setShowBatchForm(!showBatchForm); setShowAddForm(false) }}>
                ++ Agregar en Lote
              </button>
            </div>
          )}

          {showAddForm && (
            <div className="card" style={{ marginBottom: 16, borderColor: 'var(--color-primary)' }}>
              <h4 style={{ margin: '0 0 12px', color: 'var(--color-primary)' }}>Agregar Animal</h4>
              <form onSubmit={handleAdd} style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
                <div>
                  <label className="field-label">Numeración</label>
                  <input className="input-field" value={addForm.numeracion}
                    onChange={e => setAddForm({ ...addForm, numeracion: e.target.value })}
                    placeholder="Ej: A-001" style={{ width: 120 }} />
                </div>
                <div>
                  <label className="field-label">Tipo</label>
                  <select className="input-field" value={addForm.tipo}
                    onChange={e => setAddForm({ ...addForm, tipo: e.target.value })}>
                    {TIPOS.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                </div>
                <div>
                  <label className="field-label">Peso Inicial (kg)</label>
                  <input className="input-field" type="number" step="0.1" value={addForm.pesoInicial}
                    onChange={e => setAddForm({ ...addForm, pesoInicial: e.target.value })}
                    placeholder="350" style={{ width: 110 }} />
                </div>
                <div>
                  <label className="field-label">Peso Actual (kg)</label>
                  <input className="input-field" type="number" step="0.1" value={addForm.pesoActual}
                    onChange={e => setAddForm({ ...addForm, pesoActual: e.target.value })}
                    placeholder="Igual al inicial" style={{ width: 130 }} />
                </div>
                <button type="submit" className="action-btn action-btn--primary action-btn--small">Agregar</button>
                <button type="button" className="action-btn action-btn--small" onClick={() => setShowAddForm(false)}>Cancelar</button>
              </form>
            </div>
          )}

          {showBatchForm && (
            <div className="card" style={{ marginBottom: 16, borderColor: 'var(--color-info)' }}>
              <h4 style={{ margin: '0 0 12px', color: 'var(--color-info)' }}>Agregar en Lote (múltiples animales)</h4>
              <form onSubmit={handleBatchAdd} style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
                <div>
                  <label className="field-label">Tipo</label>
                  <select className="input-field" value={batchForm.tipo}
                    onChange={e => setBatchForm({ ...batchForm, tipo: e.target.value })}>
                    {TIPOS.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                </div>
                <div>
                  <label className="field-label">Prefijo</label>
                  <input className="input-field" value={batchForm.prefix}
                    onChange={e => setBatchForm({ ...batchForm, prefix: e.target.value })}
                    placeholder="A-" style={{ width: 80 }} />
                </div>
                <div>
                  <label className="field-label">Desde #</label>
                  <input className="input-field" type="number" value={batchForm.startNum}
                    onChange={e => setBatchForm({ ...batchForm, startNum: Number(e.target.value) })}
                    style={{ width: 70 }} />
                </div>
                <div>
                  <label className="field-label">Cantidad</label>
                  <input className="input-field" type="number" value={batchForm.count}
                    onChange={e => setBatchForm({ ...batchForm, count: Number(e.target.value) })}
                    style={{ width: 70 }} min={1} max={100} />
                </div>
                <div>
                  <label className="field-label">Peso Inicial (kg)</label>
                  <input className="input-field" type="number" step="0.1" value={batchForm.pesoInicial}
                    onChange={e => setBatchForm({ ...batchForm, pesoInicial: e.target.value })}
                    placeholder="350" style={{ width: 110 }} />
                </div>
                <button type="submit" className="action-btn action-btn--primary action-btn--small">Agregar {batchForm.count}</button>
                <button type="button" className="action-btn action-btn--small" onClick={() => setShowBatchForm(false)}>Cancelar</button>
              </form>
              <p style={{ fontSize: 11, color: 'var(--color-text-muted)', marginTop: 8 }}>
                Generará: {batchForm.prefix}{String(batchForm.startNum).padStart(3, '0')} ... {batchForm.prefix}{String(batchForm.startNum + batchForm.count - 1).padStart(3, '0')}
              </p>
            </div>
          )}

          {ganados.length === 0 ? (
            <EmptyState
              icon=""
              title="Sin ganado registrado"
              description="Agrega animales a este lote para comenzar el seguimiento."
              card={false}
            />
          ) : (
            <div className="ganado-table-wrapper">
              <table className="ganado-table">
                <thead>
                  <tr>
                    <th>#</th><th>Numeración</th><th>Tipo</th>
                    <th>Peso Inicial (kg)</th><th>Peso Actual (kg)</th><th>Ganancia (kg)</th><th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {ganados.map((g, idx) => (
                    <tr key={g.id}>
                      <td style={{ color: 'var(--color-text-muted)' }}>{idx + 1}</td>
                      {editingId === g.id ? (
                        <>
                          <td>
                            <input className="input-field input-sm" value={editNumeracion}
                              onChange={e => setEditNumeracion(e.target.value)} style={{ width: 100 }} />
                          </td>
                          <td>
                            <select className="input-field input-sm" value={editTipo} onChange={e => setEditTipo(e.target.value)}>
                              {TIPOS.map(t => <option key={t} value={t}>{t}</option>)}
                            </select>
                          </td>
                          <td>{g.pesoInicial}</td>
                          <td>
                            <input className="input-field input-sm" type="number" step="0.1"
                              value={editPeso} onChange={e => setEditPeso(e.target.value)} style={{ width: 90 }} />
                          </td>
                          <td style={{ color: (Number(editPeso) - g.pesoInicial) >= 0 ? 'var(--color-positive)' : 'var(--color-negative)' }}>
                            {(Number(editPeso) - g.pesoInicial).toFixed(1)}
                          </td>
                          <td>
                            <button className="action-btn action-btn--primary action-btn--small" onClick={() => handleSaveEdit(g.id)}>💾</button>
                            <button className="action-btn action-btn--small" onClick={() => setEditingId(null)} style={{ marginLeft: 4 }}>✕</button>
                          </td>
                        </>
                      ) : (
                        <>
                          <td style={{ fontWeight: 500 }}>{g.numeracion}</td>
                          <td>
                            <span style={{ color: TIPO_COLORS[g.tipo] }}>{g.tipo}</span>
                          </td>
                          <td>{g.pesoInicial}</td>
                          <td style={{ fontWeight: 600 }}>{g.pesoActual}</td>
                          <td style={{ color: g.gananciaPeso > 0 ? 'var(--color-positive)' : g.gananciaPeso < 0 ? 'var(--color-negative)' : 'var(--color-text-secondary)', fontWeight: 500 }}>
                            {g.gananciaPeso > 0 ? '+' : ''}{g.gananciaPeso?.toFixed(1)}
                          </td>
                          <td>
                            {!isClosed && (
                              <>
                                <button className="action-btn action-btn--small" onClick={() => startEdit(g)}>✏️</button>
                                <button className="action-btn action-btn--danger action-btn--small" onClick={() => handleDeleteGanado(g)} style={{ marginLeft: 4 }}>🗑️</button>
                              </>
                            )}
                          </td>
                        </>
                      )}
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr style={{ fontWeight: 600, borderTop: `2px solid var(--color-border)` }}>
                    <td colSpan={3} style={{ textAlign: 'right', color: 'var(--color-text-secondary)' }}>Totales ({ganados.length} cabezas):</td>
                    <td>{totalPesoInicial.toFixed(1)}</td>
                    <td>{totalPesoActual.toFixed(1)}</td>
                    <td style={{ color: totalGanancia >= 0 ? 'var(--color-positive)' : 'var(--color-negative)' }}>
                      {totalGanancia >= 0 ? '+' : ''}{totalGanancia.toFixed(1)}
                    </td>
                    <td></td>
                  </tr>
                </tfoot>
              </table>
            </div>
          )}
        </div>
      )}

      {/* TAB: Historial */}
      {activeTab === 'historial' && (
        <div>
          {(!lote.parcelHistory || lote.parcelHistory.length === 0) ? (
            <EmptyState
              icon="🔄"
              title="Sin historial de terrenos"
              description="Asigna un potrero al lote para comenzar el seguimiento."
              card={false}
            />
          ) : (
            <div className="ndvi-history-list">
              {lote.parcelHistory.map((h) => (
                <div key={h.id} className="ndvi-history-item">
                  <div className="ndvi-history-dot" style={{ background: h.fechaSalida ? 'var(--color-text-muted)' : 'var(--color-primary)' }} />
                  <div className="ndvi-history-content">
                    <div style={{ fontWeight: 600, color: 'var(--color-text)' }}>📍 {h.parcelName}</div>
                    <div style={{ fontSize: 13, color: 'var(--color-text-secondary)', marginTop: 2 }}>
                      Ingreso: {h.fechaIngreso}
                      {h.fechaSalida
                        ? ` · Salida: ${h.fechaSalida}`
                        : <span style={{ color: 'var(--color-primary)' }}> · Actualmente en este potrero</span>
                      }
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
