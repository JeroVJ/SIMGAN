import { useEffect, useMemo, useState } from 'react'

function fmtTime(totalSec) {
  if (totalSec <= 0) return '0s'
  const m = Math.floor(totalSec / 60)
  const s = totalSec % 60
  if (m === 0) return `${s}s`
  return s === 0 ? `${m}m` : `${m}m ${s}s`
}

export default function OperationProgress({
  active,
  title = 'Procesando...',
  expectedSeconds = 30,
  hint = null,
}) {
  const [startedAt, setStartedAt] = useState(null)
  const [elapsedMs, setElapsedMs] = useState(0)

  useEffect(() => {
    if (!active) {
      setStartedAt(null)
      setElapsedMs(0)
      return
    }
    if (!startedAt) {
      setStartedAt(Date.now())
      setElapsedMs(0)
    }
  }, [active, startedAt])

  useEffect(() => {
    if (!active || !startedAt) return
    const id = window.setInterval(() => {
      setElapsedMs(Date.now() - startedAt)
    }, 250)
    return () => window.clearInterval(id)
  }, [active, startedAt])

  const { progress, remainingSeconds, elapsedSeconds } = useMemo(() => {
    if (!active) return { progress: 0, remainingSeconds: 0, elapsedSeconds: 0 }
    const safeExpected = Math.max(5, Number(expectedSeconds) || 30)
    const expectedMs = safeExpected * 1000
    const elapsed = Math.max(0, elapsedMs)
    const boundedProgress = Math.min(95, (elapsed / expectedMs) * 95)
    return {
      progress: Math.round(boundedProgress),
      remainingSeconds: Math.max(0, Math.ceil((expectedMs - elapsed) / 1000)),
      elapsedSeconds: Math.floor(elapsed / 1000),
    }
  }, [active, elapsedMs, expectedSeconds])

  if (!active) return null

  const remainingLabel = remainingSeconds > 0
    ? `Tiempo restante: ~${fmtTime(remainingSeconds)}`
    : 'Finalizando...'

  return (
    <div className="comp-op-progress" role="status" aria-live="polite">
      <div className="comp-op-progress__head">
        <div className="comp-op-progress__title">
          <span className="comp-op-progress__pulse" aria-hidden="true" />
          <strong>{title}</strong>
        </div>
        <span className="comp-op-progress__pct">{progress}%</span>
      </div>

      {hint && <p className="comp-op-progress__hint">{hint}</p>}

      <div className="comp-op-progress__track" aria-hidden="true">
        <div className="comp-op-progress__fill" style={{ width: `${progress}%` }} />
      </div>

      <div className="comp-op-progress__meta">
        <span>{remainingLabel}</span>
        <span>Transcurrido: {fmtTime(elapsedSeconds)}</span>
      </div>
    </div>
  )
}
