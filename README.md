# SIMGAN Demo — Sistema de Gestión Ganadera

Demo funcional de gestión ganadera con mapas satelitales, rotación de pastoreo, análisis NDVI y seguimiento de ganado.

---

## Lo que incluye el proyecto

El sistema fue construido en 7 fases incrementales:

| Fase | Módulo | Descripción |
|------|--------|-------------|
| 1 | **Fincas** | Registro de fincas con nombre, dueño y coordenadas GPS |
| 2 | **Terrenos** | Dibujar polígonos sobre mapa satelital → calcula área automáticamente |
| 3 | **Parcelas** | Subdividir terrenos en parcelas con validación geoespacial |
| 4 | **Rotación** | Estados de pastoreo por parcela: Disponible / En uso / En descanso |
| 5 | **UI / Dashboard** | Sidebar, componentes reutilizables, diseño mobile-first, dashboard por finca |
| 6 | **Lotes y Ganado** | Grupos de animales con peso, tipo, asignación a parcelas e historial de movimiento |
| 7 | **NDVI / Análisis** | Análisis de vegetación con Sentinel-2 y Planet Labs, alertas automáticas, recomendaciones de rotación |

---

## Tech Stack

**Backend:**
- Java 17 + Spring Boot 3.2
- PostgreSQL 16
- Spring Data JPA + Hibernate
- Lombok

**Frontend:**
- React 18 + Vite
- React Leaflet + Esri World Imagery (mapa satelital)
- Leaflet Draw (dibujo de polígonos)
- Turf.js (cálculos geoespaciales)
- Recharts (graficas NDVI)
- React Router v6 + Axios

---

## Requisitos previos

- Java 17+
- Node.js 18+
- Docker y Docker Compose (recomendado para PostgreSQL), o PostgreSQL 16 instalado localmente
- Maven (o usar el wrapper `./mvnw` incluido)

---

## Levantar el proyecto

### Opción A: Docker para PostgreSQL (recomendado)

```bash
# 1. Levantar la base de datos
docker-compose up -d

# 2. Verificar que postgres está corriendo
docker ps
```

### Opción B: PostgreSQL local

```bash
psql -U postgres -c "CREATE DATABASE simgan_db;"
```

---

### Backend

```bash
cd backend

# Compilar y correr
./mvnw spring-boot:run
```

O desde IntelliJ:
1. Abrir la carpeta `backend/` como proyecto Maven
2. Verificar credenciales en `src/main/resources/application.properties`
3. Ejecutar `SimganDemoApplication.java`

El backend corre en `http://localhost:8080`.

> Al iniciar, Spring Boot crea las tablas automáticamente (`ddl-auto=update`) y genera datos NDVI de demostración para los últimos 6 meses (`ndvi.seed.enabled=true`).

---

### Frontend

```bash
cd frontend

npm install
npm run dev
```

Abre `http://localhost:5173` en el navegador.

> El proxy de Vite redirige `/api/*` al backend automáticamente.

---

## Configuración

El archivo `backend/src/main/resources/application.properties` contiene:

```properties
# Base de datos
spring.datasource.url=jdbc:postgresql://localhost:5432/simgan_db
spring.datasource.username=postgres
spring.datasource.password=postgres

# Planet Labs API (opcional — para imágenes satelitales reales)
planet.api.key=TU_API_KEY

# Copernicus / Sentinel-2 (opcional — para análisis NDVI real)
copernicus.username=TU_EMAIL
copernicus.password=TU_PASSWORD

# NDVI seed data (genera datos demo al iniciar)
ndvi.seed.enabled=true
ndvi.seed.months=6
```

Sin las API keys de Planet Labs o Copernicus, el sistema usa datos de demostración generados automáticamente.

---

## Flujo de uso sugerido

1. Ir a **Fincas** → Crear una finca con coordenadas GPS (ej: Montería `8.7479, -75.8814`)
2. Desde el dashboard de la finca → **Nuevo Terreno** → Dibujar polígono sobre el mapa satelital
3. Entrar al terreno → **Parcelas** → Subdividir en 3-4 parcelas
4. **Rotación** → Cambiar estados de pastoreo por parcela
5. **Lotes** → Crear un lote de ganado, agregar animales, asignar a una parcela
6. **NDVI** → Ver dashboard con salud de vegetación, alertas y recomendaciones de rotación

---

## API Endpoints

### Fincas
| Method | URL | Descripción |
|--------|-----|-------------|
| `GET` | `/api/farms` | Listar todas |
| `GET` | `/api/farms/{id}` | Obtener por ID |
| `POST` | `/api/farms` | Crear finca |
| `DELETE` | `/api/farms/{id}` | Eliminar |

### Terrenos
| Method | URL | Descripción |
|--------|-----|-------------|
| `GET` | `/api/terrains/farm/{farmId}` | Listar por finca |
| `GET` | `/api/terrains/{id}` | Obtener por ID |
| `POST` | `/api/terrains` | Crear terreno (con GeoJSON) |
| `DELETE` | `/api/terrains/{id}` | Eliminar |

### Parcelas
| Method | URL | Descripción |
|--------|-----|-------------|
| `GET` | `/api/parcels/terrain/{terrainId}` | Listar por terreno |
| `POST` | `/api/parcels` | Crear parcela |
| `PATCH` | `/api/parcels/{id}/status` | Cambiar estado de rotación |
| `DELETE` | `/api/parcels/{id}` | Eliminar |

### Lotes y Ganado
| Method | URL | Descripción |
|--------|-----|-------------|
| `GET` | `/api/lotes/terrain/{terrainId}` | Listar lotes por terreno |
| `GET` | `/api/lotes/{id}` | Obtener lote |
| `POST` | `/api/lotes` | Crear lote |
| `PATCH` | `/api/lotes/{id}/close` | Cerrar lote |
| `POST` | `/api/lotes/{id}/assign-parcel` | Asignar parcela al lote |
| `POST` | `/api/lotes/{id}/ganado` | Agregar animal al lote |
| `POST` | `/api/lotes/{id}/ganado/batch` | Agregar animales en lote |
| `PUT` | `/api/lotes/ganado/{ganadoId}` | Actualizar animal |
| `DELETE` | `/api/lotes/ganado/{ganadoId}` | Eliminar animal |

### NDVI / Análisis
| Method | URL | Descripción |
|--------|-----|-------------|
| `GET` | `/api/ndvi/dashboard/{terrainId}` | Dashboard completo con NDVI, biomasa y alertas |
| `GET` | `/api/ndvi/timeline/{terrainId}` | Serie temporal de NDVI |
| `GET` | `/api/ndvi/comparison/{terrainId}` | Comparación entre parcelas |
| `GET` | `/api/ndvi/recommendations/{terrainId}` | Recomendaciones de rotación |
| `GET` | `/api/ndvi/alerts/{terrainId}` | Alertas activas |
| `PATCH` | `/api/ndvi/alerts/{alertId}/acknowledge` | Confirmar alerta |
| `POST` | `/api/ndvi/analyze/{terrainId}` | Ejecutar análisis |
| `GET` | `/api/ndvi/grazing-estimate/{terrainId}` | Estimación de capacidad de pastoreo |

---

## Estructura del proyecto

```
simgan-demo/
├── docker-compose.yml              # PostgreSQL en Docker
├── database/
│   └── init.sql                    # Script SQL de referencia
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/simgan/
│       ├── SimganDemoApplication.java
│       ├── config/
│       │   ├── CorsConfig.java
│       │   └── GlobalExceptionHandler.java
│       ├── controller/
│       │   ├── FarmController.java
│       │   ├── TerrainController.java
│       │   ├── ParcelController.java
│       │   ├── LoteController.java
│       │   └── NdviController.java
│       ├── entity/
│       │   ├── Farm.java
│       │   ├── Terrain.java
│       │   ├── Parcel.java
│       │   ├── Lote.java
│       │   ├── Ganado.java
│       │   ├── LoteParcelHistory.java
│       │   ├── RotationHistory.java
│       │   ├── NdviRecord.java
│       │   └── NdviAlert.java
│       ├── service/
│       │   ├── FarmService.java
│       │   ├── TerrainService.java
│       │   ├── ParcelService.java
│       │   ├── LoteService.java
│       │   ├── NdviProcessingService.java
│       │   ├── NdviRecommendationService.java
│       │   ├── NdviSeedService.java
│       │   ├── PlanetApiService.java
│       │   ├── SentinelApiService.java
│       │   └── AnalysisOrchestrator.java
│       ├── dto/
│       ├── repository/
│       └── resources/
│           └── application.properties
└── frontend/
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── App.jsx
        ├── pages/
        │   ├── FarmsPage.jsx
        │   ├── CreateFarmPage.jsx
        │   ├── FarmDashboardPage.jsx
        │   ├── CreateTerrainPage.jsx
        │   ├── ParcelsPage.jsx
        │   ├── RotationPage.jsx
        │   ├── LotesPage.jsx
        │   ├── LoteDetailPage.jsx
        │   └── NdviDashboardPage.jsx
        ├── components/        # Badge, Breadcrumb, Button, Card, Sidebar, etc.
        ├── hooks/             # useFarm, useTerrain, useLote, useNdvi
        ├── services/
        │   └── api.js
        └── utils/
            ├── ndvi.js
            └── grazing.js
```

---

## GPS de ejemplo (Colombia — zonas ganaderas)

| Municipio | Lat | Lng |
|-----------|-----|-----|
| Montería, Córdoba | `8.7479` | `-75.8814` |
| Sincelejo, Sucre | `9.3047` | `-75.3978` |
| Villavicencio, Meta | `4.1420` | `-73.6266` |
| Yopal, Casanare | `5.3378` | `-72.3959` |
