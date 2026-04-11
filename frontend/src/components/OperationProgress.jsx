import { useEffect, useMemo, useState } from 'react'

export default function OperationProgress({
  active,
  title = 'Procesando...',
  expectedSeconds = 30,
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
    if (!active) {
      return { progress: 0, remainingSeconds: 0, elapsedSeconds: 0 }
    }

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

  const helper = remainingSeconds > 0
    ? `Tiempo estimado restante: ${remainingSeconds}s`
    : 'Finalizando los ultimos pasos...'

  return (
    <div className="comp-op-progress" role="status" aria-live="polite">
      <div className="comp-op-progress__head">
        <strong>{title}</strong>
        <span>{progress}%</span>
      </div>
      <div className="comp-op-progress__bar" aria-hidden="true">
        <div
          className="comp-op-progress__fill"
          style={{ width: `${progress}%` }}
        />
      </div>
      <div className="comp-op-progress__meta">
        <span>{helper}</span>
        <span>Transcurrido: {elapsedSeconds}s</span>
      </div>
    </div>
  )
}
