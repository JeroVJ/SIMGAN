import { Menu, Satellite } from 'lucide-react'

/**
 * MobileHeader
 *
 * A fixed top bar displayed on mobile viewports only (hidden via CSS on desktop).
 * Shows the SIMGAN brand and a hamburger button that opens the sidebar drawer.
 *
 * Props:
 *   onMenuClick — callback to open the sidebar drawer
 */
export default function MobileHeader({ onMenuClick }) {
  return (
    <header className="mobile-header" aria-label="Barra de navegación">
      <button
        className="mobile-header__menu-btn"
        onClick={onMenuClick}
        aria-label="Abrir menú de navegación"
        aria-expanded="false"
      >
        <Menu size={22} strokeWidth={1.75} />
      </button>

      <div className="mobile-header__brand">
        <span className="mobile-header__brand-icon">
          <Satellite size={14} strokeWidth={1.75} />
        </span>
        <span className="mobile-header__brand-name">SIMGAN</span>
      </div>

      {/* Spacer keeps brand centered */}
      <div className="mobile-header__spacer" aria-hidden="true" />
    </header>
  )
}
