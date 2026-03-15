import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import Button from './Button'

/**
 * ConfirmDialog
 *
 * A modal confirmation dialog that replaces window.confirm().
 * Renders via a React portal so it always appears above all other content.
 *
 * Props:
 *   open         — whether the dialog is visible
 *   title        — dialog heading
 *   message      — body text explaining the action
 *   confirmLabel — label for the confirm button (default: 'Confirmar')
 *   cancelLabel  — label for the cancel button  (default: 'Cancelar')
 *   variant      — 'danger' | 'warning' (controls header accent and confirm button colour)
 *   onConfirm    — called when the user clicks the confirm button
 *   onCancel     — called when the user clicks cancel or the backdrop
 *
 * Usage:
 *   const [open, setOpen] = useState(false)
 *
 *   <Button onClick={() => setOpen(true)}>Eliminar</Button>
 *
 *   <ConfirmDialog
 *     open={open}
 *     variant="danger"
 *     title="Eliminar finca"
 *     message="¿Eliminar esta finca y todos sus terrenos? Esta acción no se puede deshacer."
 *     confirmLabel="Eliminar"
 *     onConfirm={handleDelete}
 *     onCancel={() => setOpen(false)}
 *   />
 */

const ICONS = {
  danger: (
    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
      <path
        d="M10 3L18 17H2L10 3Z"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M10 8v4M10 14.5v.5" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" />
    </svg>
  ),
  warning: (
    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
      <circle cx="10" cy="10" r="7.5" stroke="currentColor" strokeWidth="1.5" />
      <path d="M10 6v5M10 13.5v.5" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" />
    </svg>
  ),
}

export default function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  variant = 'danger',
  onConfirm,
  onCancel,
}) {
  const cancelRef = useRef(null)

  // Focus the cancel button when dialog opens (safer default)
  useEffect(() => {
    if (open) {
      setTimeout(() => cancelRef.current?.focus(), 50)
    }
  }, [open])

  // Close on Escape
  useEffect(() => {
    if (!open) return
    function handleKey(e) {
      if (e.key === 'Escape') onCancel?.()
    }
    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [open, onCancel])

  if (!open) return null

  const confirmVariant = variant === 'danger' ? 'danger' : 'warning'

  const dialog = (
    <div
      className="comp-dialog-overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel?.()
      }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="comp-dialog-title"
    >
      <div className={`comp-dialog comp-dialog--${variant}`}>
        <div className="comp-dialog__header">
          <div className={`comp-dialog__icon comp-dialog__icon--${variant}`}>
            {ICONS[variant]}
          </div>
          {title && (
            <h2 className="comp-dialog__title" id="comp-dialog-title">
              {title}
            </h2>
          )}
        </div>

        {message && (
          <div className="comp-dialog__body">{message}</div>
        )}

        <div className="comp-dialog__footer">
          <Button
            ref={cancelRef}
            variant="ghost"
            onClick={onCancel}
          >
            {cancelLabel}
          </Button>
          <Button
            variant={confirmVariant}
            onClick={onConfirm}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )

  return createPortal(dialog, document.body)
}
