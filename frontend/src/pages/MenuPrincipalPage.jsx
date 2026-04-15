import { useState, useEffect } from 'react'
import { dashboardApi } from '../services/api'
import { Card, Spinner } from '../components'
import toast from 'react-hot-toast'

export default function MenuPrincipalPage() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [selectedFarmId, setSelectedFarmId] = useState(null)

  useEffect(() => {
    fetchDashboard(selectedFarmId)
  }, [selectedFarmId])

  async function fetchDashboard(farmId) {
    setLoading(true)
    try {
      const summary = await dashboardApi.getSummary(farmId)
      setData(summary)
      if (farmId === null && summary.farms && summary.farms.length > 0) {
        setSelectedFarmId(summary.farms[0].id)
      }
    } catch (err) {
      toast.error('Error al cargar el dashboard')
    } finally {
      setLoading(false)
    }
  }

  if (loading && !data) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 100 }}>
        <Spinner />
      </div>
    )
  }

  return (
    <div style={{ padding: '24px' }}>
      {/* Header with Selector */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '32px' }}>
        <div style={{ position: 'relative', width: '280px' }}>
          <select
            value={selectedFarmId || ''}
            onChange={(e) => setSelectedFarmId(e.target.value)}
            style={{
              width: '100%',
              padding: '12px 16px',
              fontSize: '18px',
              fontWeight: '600',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-sm)',
              appearance: 'none',
              background: 'var(--color-surface)',
              color: 'var(--color-text)',
              cursor: 'pointer'
            }}
          >
            {data?.farms?.map(farm => (
              <option key={farm.id} value={farm.id}>{farm.name}</option>
            ))}
          </select>
          <div style={{ position: 'absolute', right: '16px', top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none' }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M6 9l6 6 6-6" />
            </svg>
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '350px 1fr', gap: '24px' }}>
        
        {/* Main Metric Card */}
        <Card style={{ background: '#F8FBF9', border: '1px solid #E8F2ED', padding: '32px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '16px' }}>
            <div style={{ 
              width: '48px', height: '48px', borderRadius: '50%', background: '#E8F2ED', 
              display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '24px'
            }}>
              💰
            </div>
            <div>
              <p style={{ margin: 0, color: 'var(--color-text-secondary)', fontSize: '14px', fontWeight: '500' }}>
                Ganancia Total Estimada (KG)
              </p>
              <h2 style={{ margin: 0, fontSize: '32px', color: '#15532E', fontWeight: '700' }}>
                +{data?.gananciaTotalEstimada?.toLocaleString() || 0} kg
              </h2>
            </div>
          </div>
        </Card>

        {/* Grid of Metrics */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px' }}>
          
          {/* Row 1 */}
          <MetricCard 
            title="Total Potreros" 
            value={data?.totalPotreros} 
            subtitle="Capacidad total" 
            icon="🏡" 
          />
          <MetricCard 
            title="Potreros disponibles" 
            value={data?.potrerosDisponibles} 
            subtitle="Listos para 48h" 
            icon="🚜" 
          />
          <MetricCard 
            title="Potreros Ocupados" 
            value={data?.potrerosOcupados} 
            subtitle="Ocupación actual" 
            icon="📋" 
          />
          <MetricCard 
            title="Potreros en descanso" 
            value={data?.potrerosEnDescanso} 
            subtitle="" 
            icon="⏸" 
          />

          {/* Row 2 */}
          <MetricCard 
            title="Total Rotaciones" 
            value={data?.totalRotacionesUltima24h} 
            subtitle="Última 24h" 
            icon="🔄" 
          />
          <MetricCard 
            title="Total Animales" 
            value={data?.totalAnimales} 
            subtitle={`Densidad: ${data?.densidadAnimal?.toFixed(1) || 0} / potrero`} 
            icon="🐄" 
          />
          <MetricCard 
            title="Animales afectados" 
            value={data?.animalesAfectados} 
            subtitle="" 
            icon="➕" 
            color="#EF4444"
            iconBg="#FEF2F2"
          />
          <MetricCard 
            title="Potreros en alerta" 
            value={data?.potrerosEnAlerta} 
            subtitle="" 
            icon="⚠️" 
            color="#EF4444"
            iconBg="#FEF2F2"
          />

        </div>
      </div>
    </div>
  )
}

function MetricCard({ title, value, subtitle, icon, color = 'var(--color-text)', iconBg = '#F3F4F6' }) {
  return (
    <Card style={{ padding: '20px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
        <div style={{ 
          width: '32px', height: '32px', borderRadius: '8px', background: iconBg,
          display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '16px'
        }}>
          {icon}
        </div>
        <p style={{ margin: 0, fontSize: '13px', color: 'var(--color-text-secondary)', fontWeight: '500' }}>
          {title}
        </p>
      </div>
      <div>
        <h3 style={{ margin: 0, fontSize: '28px', color: color, fontWeight: '700' }}>
          {value || 0}
        </h3>
        {subtitle && (
          <p style={{ margin: '4px 0 0', fontSize: '11px', color: 'var(--color-text-muted)' }}>
            {subtitle}
          </p>
        )}
      </div>
    </Card>
  )
}
