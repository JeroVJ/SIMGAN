# Guía de despliegue — SIMGAN

Stack de producción:

| Pieza         | Proveedor   | Plan                  | Costo aprox. |
|---------------|-------------|-----------------------|--------------|
| Frontend      | **Vercel**  | Hobby                 | $0           |
| Backend       | **Railway** | Hobby (con créditos)  | ~$5/mes (cubierto por créditos iniciales) |
| Base de datos | **Railway Postgres** | Plugin del mismo proyecto | Cuenta dentro de los $5 |
| Dominio       | `*.vercel.app` + `*.up.railway.app` | — | $0 |

> **Por qué Railway en lugar de Render:** no hay cold starts, la base de datos
> vive en el mismo proyecto que el backend (las variables `DATABASE_URL`,
> `PGHOST`, etc. se inyectan automáticamente), y la DX es más limpia. Los
> créditos iniciales de Railway suelen alcanzar para varios meses de un
> proyecto de grado con tráfico bajo.

---

## 1. Crear el proyecto en Railway

1. Ir a https://railway.app y entrar con GitHub.
2. **New Project → Deploy from GitHub repo** → seleccionar este repo.
3. Railway detecta el `Dockerfile` en `backend/`. Si no, en **Settings** del
   servicio:
   - **Root Directory**: `backend`
   - **Builder**: Dockerfile

---

## 2. Agregar Postgres al proyecto

1. Dentro del proyecto: **+ New → Database → Add PostgreSQL**.
2. Railway crea una instancia y expone variables como `DATABASE_URL`,
   `PGHOST`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`, `PGPORT` dentro del
   proyecto (compartidas entre servicios).
3. Las tablas se crean solas la primera vez que el backend arranca
   (`spring.jpa.hibernate.ddl-auto=update`).

---

## 3. Configurar variables del backend

En el servicio del backend → **Variables** → agregar:

| Variable | Valor | Notas |
|----------|-------|-------|
| `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}?sslmode=require` | Referencia a las vars del plugin Postgres |
| `DB_USERNAME` | `${{Postgres.PGUSER}}` | Referencia |
| `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` | Referencia |
| `JWT_SECRET` | (cadena aleatoria ≥ 64 chars, ej. `openssl rand -base64 64`) | Único por entorno |
| `MAIL_ENABLED` | `true` | Activa el bean de SMTP |
| `MAIL_USERNAME` | `tu-gmail@gmail.com` | |
| `MAIL_PASSWORD` | App password de Gmail (16 chars) | NO la contraseña de Google |
| `MAIL_FROM` | `SIMGAN <tu-gmail@gmail.com>` | |
| `APP_BASE_URL` | URL final del frontend (después del paso 4) | ej. `https://simgan.vercel.app` |
| `CORS_ALLOWED_ORIGINS` | Igual que `APP_BASE_URL` | |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | `https://*.vercel.app` | Permite preview deploys |

> **Importante:** la sintaxis `${{Postgres.PGUSER}}` es de Railway — referencia
> variables del plugin Postgres dentro del mismo proyecto. Si tu plugin se
> llama distinto (ej. `database`), ajusta el prefijo.

Railway expone el servicio en `https://<nombre-servicio>.up.railway.app`.
Anota esta URL — va en el frontend.

---

## 4. Frontend — Vercel

1. https://vercel.com → **Add New Project** → importar el repo.
2. Configurar:
   - **Root Directory**: `frontend`
   - **Framework Preset**: Vite (lo detecta solo)
   - **Build Command**: `npm run build` (predeterminado)
   - **Output Directory**: `dist`
3. En **Environment Variables**, agregar:

   | Variable | Valor |
   |----------|-------|
   | `VITE_API_URL` | `https://<tu-backend>.up.railway.app/api` |

4. Desplegar. La primera vez tarda ~1 min.
5. Vuelve a Railway y actualiza `APP_BASE_URL` y `CORS_ALLOWED_ORIGINS` con
   el dominio definitivo de Vercel, y redeploya el backend.

---

## 5. Correo (Gmail) — App Password

El backend usa Gmail SMTP. Necesitas un **App Password** (no tu contraseña):

1. https://myaccount.google.com/security → habilitar 2FA si no lo está.
2. https://myaccount.google.com/apppasswords → generar uno de 16 caracteres
   (elige "Other" y nómbralo `SIMGAN`).
3. Pega ese valor en `MAIL_PASSWORD` en Railway (sin espacios).

> **Nunca** pongas la app password en `application.properties` ni en
> `.env.example`. Solo en las variables del entorno de producción.

---

## 6. Verificación post-deploy

- [ ] `https://<tu-frontend>.vercel.app` carga la pantalla de login.
- [ ] Registro de usuario funciona → llega a `/farms` vacío.
- [ ] Crear finca → terreno → potrero → se guardan en Railway Postgres.
- [ ] "¿Olvidaste tu contraseña?" → introduces correo → llega el mail con
      enlace `?token=...`.
- [ ] Abrir el enlace → cambiar password → re-login OK.
- [ ] Crear una alerta de prueba → llega el correo.

---

## 7. Local dev (para clases / clones)

El repo está configurado para correr **sin tocar ningún archivo de
configuración**:

```bash
# 1. Levantar Postgres en Docker
docker compose up -d

# 2. Backend
cd backend && ./mvnw spring-boot:run

# 3. Frontend (en otra terminal)
cd frontend && npm install && npm run dev
```

- El backend usa los defaults de `application.properties` (Postgres local,
  JWT dev secret, mail **desactivado**).
- El frontend usa el proxy de Vite (`/api` → `localhost:8080`).
- Si alguien quiere probar el envío de correo localmente, copia
  `backend/.env.example` → `backend/.env` y carga las vars (ej. con la
  extensión EnvFile de IntelliJ o exportándolas en la shell antes de
  `mvn spring-boot:run`).

---

## 8. Rollback rápido

- **Vercel:** dashboard del proyecto → un deploy anterior → **Promote**.
- **Railway:** servicio → **Deployments** → cualquier build pasada → **Redeploy**.
- **Postgres:** Railway permite snapshots manuales en el plugin de Postgres
  (Settings → Backups).

---

## 9. Monitoreo

- Logs del backend: Railway → servicio → **Deployments → [latest] → View Logs**.
- Logs del frontend: Vercel → proyecto → **Deployments → [latest] → Logs**.
- Métricas (CPU, RAM, red): pestaña **Metrics** de Railway.
