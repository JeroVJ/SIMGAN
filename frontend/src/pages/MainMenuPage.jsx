import { useState, useEffect, useMemo } from 'react'
import { farmApi, terrainApi, parcelApi, loteApi, ndviApi } from '../services/api'
import { 
  ChevronDown, 
  TrendingUp, 
  LayoutGrid, 
  CheckCircle2, 
  Clock, 
  PauseCircle, 
  RefreshCcw, 
  Users, 
  Heart, 
  AlertTriangle 
} from 'lucide-react'
import Spinner from '../components/Spinner'
import toast from 'react-hot-toast'

export default function MainMenuPage() {
  const [farms, setFarms] = useState([])
  const [selectedFarmId, setSelectedFarmId] = useState('')
  const [loading, setLoading] = useState(true)
  const [farmData, setFarmData] = useState({ terrains: [], terrainDetails: {} })
  const [refreshing, setRefreshing] = useState(false)

  // Cargar fincas al inicio
  useEffect(() => {
    loadFarms()
  }, [])

  async function loadFarms() {
    try {
      const data = await farmApi.getAll()
      setFarms(data)
      if (data.length > 0) {
        setSelectedFarmId(data[0].id)
      }
    } catch (err) {
      toast.error('Error cargando fincas')
    } finally {
      setLoading(false)
    }
  }

  // Cargar datos de la finca seleccionada
  useEffect(() => {
    if (selectedFarmId) {
      loadFarmData(selectedFarmId)
    }
  }, [selectedFarmId])

  async function loadFarmData(id) {
    setRefreshing(true)
    try {
      const ts = await terrainApi.getByFarm(id)
      const details = {}
      
      await Promise.all(ts.map(async (t) => {
        try {
          const [parcels, lotes] = await Promise.all([
            parcelApi.getByTerrain(t.id),
            loteApi.getByTerrain(t.id).catch(() => []),
          ])
          let ndvi = null
          try { ndvi = await ndviApi.getDashboard(t.id) } catch { /* no NDVI */ }
          details[t.id] = { parcels, lotes, ndvi }
        } catch {
          details[t.id] = { parcels: [], lotes: [], ndvi: null }
        }
      }))

      setFarmData({ terrains: ts, terrainDetails: details })
    } catch (err) {
      toast.error('Error cargando datos de la finca')
    } finally {
      setRefreshing(false)
    }
  }

  const selectedFarm = useMemo(() => farms.find(f => f.id === parseInt(selectedFarmId)), [farms, selectedFarmId])

  // Cálculos de métricas
  const metrics = useMemo(() => {
    const details = Object.values(farmData.terrainDetails)
    
    const allParcels = details.flatMap(d => d.parcels)
    const allLotes = details.flatMap(d => d.lotes).filter(l => !l.fechaSalida)
    
    // Conteo de animales
    const totalAnimales = allLotes.reduce((sum, l) => sum + (l.cabezas || 0), 0)
    
    // Potreros en alerta (NDVI < 0.3 o por lógica de negocio)
    // Supongamos que NDVI < 0.3 es alerta según requerimiento
    const potrerosEnAlerta = details.reduce((count, d) => {
      // Si el terreno tiene NDVI dashboard, miramos parcelas con bajo NDVI
      if (d.ndvi?.parcelComparison) {
        return count + d.ndvi.parcelComparison.filter(p => p.avgNdvi < 0.3).length
      }
      return count
    }, 0)

    // Animales afectados (en potreros en alerta)
    const animalesAfectados = details.reduce((count, d) => {
      const alertaParcelIds = d.ndvi?.parcelComparison?.filter(p => p.avgNdvi < 0.3).map(p => p.parcelId) || []
      const lotesEnAlerta = d.lotes.filter(l => !l.fechaSalida && alertaParcelIds.includes(l.parcelId))
      return count + lotesEnAlerta.reduce((s, l) => s + (l.cabezas || 0), 0)
    }, 0)

    // Rotaciones (Simulado como total de movimientos históricos en las últimas 24h o total)
    // Por ahora usaremos un valor derivado o simulado si no hay campo exacto
    const totalRotaciones = details.reduce((sum, d) => sum + (d.lotes.length), 0) // Simplificación

    return {
      totalPotreros: allParcels.length,
      disponibles: allParcels.filter(p => p.status === 'DISPONIBLE').length,
      ocupados: allParcels.filter(p => p.status === 'EN_USO').length,
      enDescanso: allParcels.filter(p => p.status === 'EN_DESCANSO').length,
      totalRotaciones,
      totalAnimales,
      animalesAfectados,
      potrerosEnAlerta,
      gananciaEstimada: (totalAnimales * 3.9).toFixed(0) // Simulación de ganancia basada en animales
    }
  }, [farmData])

  if (loading) return <Spinner page label="Cargando menú principal..." />

  return (
    <div className="page-container" style={{ background: 'var(--color-bg)', minHeight: '100vh', padding: '24px' }}>
      
      {/* Selector de Finca */}
      <div style={{ marginBottom: '24px', position: 'relative', display: 'inline-block' }}>
        <select 
          value={selectedFarmId} 
          onChange={(e) => setSelectedFarmId(e.target.value)}
          style={{
            appearance: 'none',
            background: 'var(--color-surface-2)',
            border: '1px solid var(--color-border)',
            borderRadius: '8px',
            padding: '10px 40px 10px 16px',
            fontSize: '18px',
            fontWeight: '600',
            color: 'var(--color-text)',
            cursor: 'pointer',
            boxShadow: '0 1px 2px rgba(0,0,0,0.2)',
            minWidth: '200px'
          }}
        >
          {farms.map(f => (
            <option key={f.id} value={f.id}>{f.name}</option>
          ))}
        </select>
        <ChevronDown size={20} style={{ position: 'absolute', right: '12px', top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none', color: 'var(--color-text-muted)' }} />
      </div>

      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>
        
        {/* Ganancia Estimada */}
        <div style={{ 
          flex: '1 1 300px', 
          background: 'var(--color-surface)', 
          borderRadius: '16px', 
          padding: '32px', 
          boxShadow: 'var(--shadow)',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          border: '1px solid var(--color-border)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
            <div style={{ background: 'rgba(74, 222, 128, 0.1)', padding: '10px', borderRadius: '50%' }}>
              <TrendingUp size={24} color="var(--color-primary)" />
            </div>
            <span style={{ fontSize: '14px', fontWeight: '600', color: 'var(--color-text-secondary)' }}>Ganancia Total Estimada (KG)</span>
          </div>
          <div style={{ fontSize: '42px', fontWeight: '700', color: 'var(--color-primary)' }}>
            +{Number(metrics.gananciaEstimada).toLocaleString()} kg
          </div>
        </div>

        {/* Grid de Métricas */}
        <div style={{ flex: '2 1 600px', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '16px' }}>
          
          <MetricCard 
            icon={<LayoutGrid size={20} />} 
            label="Total Potreros" 
            value={metrics.totalPotreros} 
            sub="Capacidad total" 
          />
          
          <MetricCard 
            icon={<CheckCircle2 size={20} />} 
            label="Potreros disponibles" 
            value={metrics.disponibles} 
            sub="Listos para 48h" 
            iconBg="rgba(74, 222, 128, 0.1)"
            iconColor="var(--color-primary)"
          />

          <MetricCard 
            icon={<Clock size={20} />} 
            label="Potreros Ocupados" 
            value={metrics.ocupados} 
            sub="Ocupación actual" 
            iconBg="rgba(245, 158, 11, 0.1)"
            iconColor="var(--color-warning)"
          />

          <MetricCard 
            icon={<PauseCircle size={20} />} 
            label="Potreros en descanso" 
            value={metrics.enDescanso} 
            iconBg="rgba(59, 130, 246, 0.1)"
            iconColor="var(--color-info)"
          />

          <MetricCard 
            icon={<RefreshCcw size={20} />} 
            label="Total Rotaciones" 
            value={metrics.totalRotaciones} 
            sub="Última 24h" 
          />

          <MetricCard 
            icon={<Users size={20} />} 
            label="Total Animales" 
            value={metrics.totalAnimales} 
            sub={`Densidad: ${(metrics.totalAnimales / (metrics.totalPotreros || 1)).toFixed(1)} / potrero`} 
          />

          <MetricCard 
            icon={<Heart size={20} />} 
            label="Animales afectados" 
            value={metrics.animalesAfectados} 
            valueColor="var(--color-danger)"
            iconBg="rgba(239, 68, 68, 0.1)"
            iconColor="var(--color-danger)"
          />

          <MetricCard 
            icon={<AlertTriangle size={20} />} 
            label="Potreros en alerta" 
            value={metrics.potrerosEnAlerta} 
            valueColor="var(--color-danger)"
            iconBg="rgba(239, 68, 68, 0.1)"
            iconColor="var(--color-danger)"
          />

        </div>
      </div>

      {refreshing && (
        <div style={{ marginTop: '20px', display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--color-text-muted)', fontSize: '14px' }}>
          <div className="spinner" style={{ width: '16px', height: '16px' }} /> Actualizando datos...
        </div>
      )}
    </div>
  )
}

function MetricCard({ icon, label, value, sub, iconBg = 'var(--color-surface-2)', iconColor = 'var(--color-text-muted)', valueColor = 'var(--color-text)' }) {
  return (
    <div style={{ 
      background: 'var(--color-surface)', 
      borderRadius: '12px', 
      padding: '16px', 
      boxShadow: 'var(--shadow)',
      border: '1px solid var(--color-border)',
      display: 'flex',
      flexDirection: 'column',
      gap: '8px'
    }}>
      <div style={{ background: iconBg, color: iconColor, width: '36px', height: '36px', borderRadius: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        {icon}
      </div>
      <div style={{ fontSize: '13px', fontWeight: '500', color: 'var(--color-text-secondary)', lineHeight: '1.2' }}>{label}</div>
      <div style={{ fontSize: '24px', fontWeight: '700', color: valueColor }}>{value}</div>
      {sub && <div style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>{sub}</div>}
    </div>
  )
}
