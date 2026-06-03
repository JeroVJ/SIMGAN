# 📋 Guía de demostración — SIMGAN

Comandos para reproducir los experimentos de NDVI, recomendaciones de rotación y
alertas por correo (pensado para mostrar al profesor).

**Finca de pruebas:** "Finca real (copia)" → `farm_id = 15` · Potreros **P1** (id 25) y **P2** (id 24)
**Umbrales calibrados:** alerta = **0.39**, óptimo = **0.45**

> Las consultas SQL se corren en **DBeaver** (conexión pública del Postgres de Railway).
> Los `curl` se corren en la **Terminal**.

---

## 0) Preparación (una sola vez)

**a) Activar reenvío de alertas repetidas** (para poder repetir en la demo):
Railway → backend → Variables → `ALERT_DEDUP_ENABLED = false` *(vuélvela a `true` al terminar)*.

**b) Token** (solo para duplicar finca; los correos de prueba NO lo necesitan):
En la app, F12 → Console → `localStorage.getItem('token')` → copia el valor. Dura ~24h.

**c) Duplicar una finca** (crea una copia idéntica para experimentar):
```bash
curl -X POST "https://api.simgan.com/api/farms/2/duplicate" -H "Authorization: Bearer <TOKEN>"
```

**d) Calibración de biomasa** (necesaria una vez para que el dashboard muestre recomendaciones):
```sql
INSERT INTO biomass_calibration_models
  (parcel_id, terrain_id, coefficient_a, coefficient_b, r_squared, sample_count, calibration_date, formula, created_at)
SELECT p.id, p.terrain_id, 6000, 0, 0.9, 5, CURRENT_DATE, 'Biomasa = 6000.00 × NDVI', NOW()
FROM parcels p WHERE p.terrain_id IN (SELECT id FROM terrains WHERE farm_id = 15);
```

---

## 1) Recomendaciones de rotación

**Dónde ver:** app → *Finca real (copia)* → **NDVI & Salud** → pestaña **Recomendaciones**.
*(Refresca con Cmd+Shift+R tras cada SQL.)*

### 🔴 Meter NDVI BAJO — urgencia URGENTE (pasto crítico, P1 en uso)
```sql
UPDATE parcels SET status='EN_USO'
WHERE name='P1' AND terrain_id IN (SELECT id FROM terrains WHERE farm_id=15);

DELETE FROM ndvi_records WHERE capture_date >= CURRENT_DATE
  AND parcel_id IN (SELECT id FROM parcels WHERE name='P1' AND terrain_id IN (SELECT id FROM terrains WHERE farm_id=15));

INSERT INTO ndvi_records (parcel_id, terrain_id, capture_date, mean_ndvi, source, created_at)
SELECT id, terrain_id, CURRENT_DATE, 0.12, 'MANUAL', NOW()
FROM parcels WHERE name='P1' AND terrain_id IN (SELECT id FROM terrains WHERE farm_id=15);
```
→ Recomienda **P1 → En Descanso**, urgencia **URGENTE** *(NDVI 0.12 < 0.15)*.

### 🟠 Meter NDVI BAJO — urgencia ALTA
Igual que el anterior pero cambia `0.12` por **`0.25`** *(entre 0.15 y el umbral 0.39)* → urgencia **ALTA**.

### 🟢 Meter NDVI ÓPTIMO — potrero recuperado (P1 listo de nuevo)
```sql
UPDATE parcels SET status='EN_DESCANSO'
WHERE name='P1' AND terrain_id IN (SELECT id FROM terrains WHERE farm_id=15);

DELETE FROM ndvi_records WHERE capture_date >= CURRENT_DATE
  AND parcel_id IN (SELECT id FROM parcels WHERE name='P1' AND terrain_id IN (SELECT id FROM terrains WHERE farm_id=15));

INSERT INTO ndvi_records (parcel_id, terrain_id, capture_date, mean_ndvi, source, created_at)
SELECT id, terrain_id, CURRENT_DATE, 0.55, 'MANUAL', NOW()
FROM parcels WHERE name='P1' AND terrain_id IN (SELECT id FROM terrains WHERE farm_id=15);
```
→ Recomienda **P1 → Disponible**, urgencia MEDIA *(pasto recuperado, NDVI 0.55 ≥ 0.45)*.

---

## 2) Correos de alerta (endpoint de prueba — NO necesita token)

Llegan al correo del dueño de la finca. `parcelId=25` es P1 de la copia.
```bash
# 🔴 Forraje bajo / en umbral
curl -X POST "https://api.simgan.com/api/alerts/test/send-email?parcelId=25&type=ESTADO_FORRAJE_BAJO_O_EN_UMBRAL"

# 🔵 Potrero encharcado
curl -X POST "https://api.simgan.com/api/alerts/test/send-email?parcelId=25&type=POTRERO_ENCHARCADO"

# 🟠 Estrés hídrico
curl -X POST "https://api.simgan.com/api/alerts/test/send-email?parcelId=25&type=POTRERO_CON_ESTRES_HIDRICO"

# 🟢 Potrero recuperado
curl -X POST "https://api.simgan.com/api/alerts/test/send-email?parcelId=25&type=POTRERO_RECUPERADO"
```

---

## 3) Correo NDVI semanal + recomendaciones de rotación

App → *Finca real (copia)* → **Línea NDVI** → botón **"Revisar NDVI de esta semana"**.
Procesa la semana, actualiza la gráfica y envía el correo semanal **con la sección de
recomendaciones de rotación** incluida.

---

## Tabla de referencia (NDVI → resultado)

| NDVI que metes | Estado de P1 | Resultado |
|---|---|---|
| `0.12` (< 0.15) | EN_USO | Recomienda **En Descanso – URGENTE** |
| `0.25` (< 0.39) | EN_USO | Recomienda **En Descanso – ALTA** |
| `0.55` (≥ 0.45) | EN_DESCANSO | Recomienda **Disponible** (recuperado) |

---

## Notas / limpieza después de la demo

- Volver a poner `ALERT_DEDUP_ENABLED = true` en Railway.
- Cerrar y volver a iniciar sesión para invalidar el token usado en los `curl`.
- El INSERT manual en `ndvi_records` **no** dispara la alerta/correo automáticos
  (eso solo pasa cuando el análisis procesa una imagen real). Para ver el correo,
  usar el endpoint de prueba de la sección 2.
- Si la base rechaza una alerta con un mensaje de "registro duplicado", borrar la
  restricción CHECK vieja de `alert_type`:
  ```sql
  DO $$
  DECLARE c record;
  BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'alertas'::regclass AND contype = 'c'
               AND pg_get_constraintdef(oid) ILIKE '%alert_type%'
    LOOP
      EXECUTE format('ALTER TABLE alertas DROP CONSTRAINT %I', c.conname);
    END LOOP;
  END $$;
  ```
