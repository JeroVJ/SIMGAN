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
  expectedRangeSeconds = null,
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

  const { progress, remainingSeconds, elapsedSeconds, remainingRangeSeconds } = useMemo(() => {
    if (!active) return { progress: 0, remainingSeconds: 0, elapsedSeconds: 0, remainingRangeSeconds: null }
    const minExpected = expectedRangeSeconds?.min ?? expectedSeconds
    const maxExpected = expectedRangeSeconds?.max ?? expectedSeconds
    const safeMinExpected = Math.max(5, Number(minExpected) || 30)
    const safeMaxExpected = Math.max(safeMinExpected, Number(maxExpected) || safeMinExpected)
    const expectedMs = safeMaxExpected * 1000
    const elapsed = Math.max(0, elapsedMs)
    const boundedProgress = Math.min(95, (elapsed / expectedMs) * 95)
    return {
      progress: Math.round(boundedProgress),
      remainingSeconds: Math.max(0, Math.ceil((expectedMs - elapsed) / 1000)),
      elapsedSeconds: Math.floor(elapsed / 1000),
      remainingRangeSeconds: expectedRangeSeconds
        ? {
            min: Math.max(0, Math.ceil((safeMinExpected * 1000 - elapsed) / 1000)),
            max: Math.max(0, Math.ceil((safeMaxExpected * 1000 - elapsed) / 1000)),
          }
        : null,
    }
  }, [active, elapsedMs, expectedRangeSeconds, expectedSeconds])

  if (!active) return null

  const remainingLabel = remainingRangeSeconds
    ? (remainingRangeSeconds.max > 0
        ? `Tiempo restante: ~${fmtTime(remainingRangeSeconds.min)} a ${fmtTime(remainingRangeSeconds.max)}`
        : 'Finalizando...')
    : (remainingSeconds > 0
        ? `Tiempo restante: ~${fmtTime(remainingSeconds)}`
        : 'Finalizando...')

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
