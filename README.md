#  SIMGAn

Demo funcional para registrar fincas, dibujar terrenos sobre imagen satelital, calcular áreas, subdividir en parcelas y gestionar rotación de pastoreo.

---

##  Qué hace el Demo

| Paso | Pantalla | Acción |
|------|----------|--------|
| 1 | **Crear Finca** | Registrar nombre, dueño, ubicación GPS |
| 2 | **Crear Terreno** | Dibujar polígono sobre mapa satelital → calcula área automáticamente |
| 3 | **Parcelas** | Subdividir terreno en parcelas → valida que estén dentro del terreno |
| 4 | **Rotación** | Vista de rotación de pastoreo con estados: Disponible / En uso / En descanso |

---

## 🛠️ Tech Stack

**Backend:**
- Java 17 + Spring Boot 3.2
- PostgreSQL
- Spring Data JPA + Hibernate
- Lombok

**Frontend:**
- React 18 + Vite
- React Leaflet (mapa interactivo)
- Esri World Imagery (tiles satelitales)
- Leaflet Draw (dibujo de polígonos)
- Turf.js (cálculo de áreas geoespaciales)
- React Router v6
- Axios
- React Hot Toast

---

## 🚀 Setup Paso a Paso

### 1. PostgreSQL

```bash
# Crear la base de datos
psql -U postgres -c "CREATE DATABASE simgan_db;"

# (Opcional) Ejecutar el script SQL de referencia
psql -U postgres -d simgan_db -f database/init.sql
```

> **Nota:** Spring Boot crea las tablas automáticamente con `ddl-auto=update`. El script SQL es solo de referencia.

### 2. Backend (IntelliJ)

1. Abre la carpeta `backend/` como proyecto Maven en IntelliJ
2. IntelliJ detectará el `pom.xml` → importa dependencias
3. Verifica las credenciales en `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/simgan_db
   spring.datasource.username=postgres
   spring.datasource.password=postgres
   ```
4. Ejecuta `SimganDemoApplication.java` (clic derecho → Run)
5. El backend corre en `http://localhost:8080`

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Abre `http://localhost:5173` en el navegador.

> El proxy de Vite redirige `/api/*` al backend automáticamente.

---

## 📡 API Endpoints

### Fincas
| Method | URL | Descripción |
|--------|-----|-------------|
| `POST` | `/api/farms` | Crear finca |
| `GET` | `/api/farms` | Listar todas |
| `GET` | `/api/farms/{id}` | Obtener por ID |
| `DELETE` | `/api/farms/{id}` | Eliminar |

### Terrenos
| Method | URL | Descripción |
|--------|-----|-------------|
| `POST` | `/api/terrains` | Crear terreno (con GeoJSON) |
| `GET` | `/api/terrains/farm/{farmId}` | Listar por finca |
| `GET` | `/api/terrains/{id}` | Obtener por ID |
| `DELETE` | `/api/terrains/{id}` | Eliminar |

### Parcelas
| Method | URL | Descripción |
|--------|-----|-------------|
| `POST` | `/api/parcels` | Crear parcela |
| `GET` | `/api/parcels/terrain/{terrainId}` | Listar por terreno |
| `PATCH` | `/api/parcels/{id}/status` | Cambiar estado rotación |
| `DELETE` | `/api/parcels/{id}` | Eliminar |

### Ejemplo: Crear Finca
```json
POST /api/farms
{
  "name": "Hacienda Los Robles",
  "owner": "Carlos Rodríguez",
  "department": "Córdoba",
  "municipality": "Montería",
  "centerLat": 8.7479,
  "centerLng": -75.8814
}
```

### Ejemplo: Cambiar Estado Parcela
```json
PATCH /api/parcels/1/status
{
  "status": "EN_USO"
}
```

---

## Estructura del Proyecto

```
simgan-demo/
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
│       │   └── ParcelController.java
│       ├── dto/
│       │   ├── FarmDto.java
│       │   ├── TerrainDto.java
│       │   └── ParcelDto.java
│       ├── entity/
│       │   ├── Farm.java
│       │   ├── Terrain.java
│       │   └── Parcel.java
│       ├── repository/
│       │   ├── FarmRepository.java
│       │   ├── TerrainRepository.java
│       │   └── ParcelRepository.java
│       └── service/
│           ├── FarmService.java
│           ├── TerrainService.java
│           └── ParcelService.java
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   └── src/
│       ├── main.jsx
│       ├── App.jsx
│       ├── index.css
│       ├── services/api.js
│       └── pages/
│           ├── FarmsPage.jsx
│           ├── CreateFarmPage.jsx
│           ├── CreateTerrainPage.jsx
│           ├── ParcelsPage.jsx
│           └── RotationPage.jsx
└── database/
    └── init.sql
```

---

## 🔮 Futuro (No incluido en demo)

> "Estamos planeando integrar **Sentinel-2 (ESA)** como fuente de datos satelitales:
> - Gratuito
> - Multiespectral
> - NDVI-ready
> - Revisita cada 5 días
>
> Esto permitirá análisis automático de salud vegetacional y recomendaciones inteligentes de rotación."

### Funcionalidades planeadas:
-  Integración Planet Labs (premium)
-  Análisis NDVI / vegetación espectral
-  Generación automática de parcelas
-  Dashboard de métricas por parcela
-  Alertas de rotación

---

 

