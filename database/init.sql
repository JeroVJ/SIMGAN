-- =============================================
-- SIMGAN Database Setup v0.1 (NDVI Edition)
-- Spring JPA creates tables automatically,
-- this is reference only.
-- =============================================

-- Ganaderos
CREATE TABLE IF NOT EXISTS ganaderos (
    id BIGSERIAL PRIMARY KEY,
    nombre_completo VARCHAR(255) NOT NULL,
    apellido_completo VARCHAR(255) NOT NULL,
    correo VARCHAR(255) UNIQUE NOT NULL,
    contrasena VARCHAR(255) NOT NULL,
    tipo_documento VARCHAR(10),
    id_documento INTEGER,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Auth Tokens (Stateful token management)
CREATE TABLE IF NOT EXISTS auth_tokens (
    id BIGSERIAL PRIMARY KEY,
    ganadero_id BIGINT NOT NULL REFERENCES ganaderos(id) ON DELETE CASCADE,
    token TEXT NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    is_revoked BOOLEAN DEFAULT FALSE,
    CONSTRAINT idx_auth_token_ganadero UNIQUE (ganadero_id, token)
);

CREATE INDEX IF NOT EXISTS idx_auth_tokens_ganadero_id ON auth_tokens(ganadero_id);
CREATE INDEX IF NOT EXISTS idx_auth_tokens_token ON auth_tokens(token);
CREATE INDEX IF NOT EXISTS idx_auth_tokens_revoked ON auth_tokens(is_revoked);

-- Farms
CREATE TABLE IF NOT EXISTS farms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    ganadero_id BIGINT NOT NULL REFERENCES ganaderos(id) ON DELETE CASCADE,
    department VARCHAR(255),
    municipality VARCHAR(255),
    center_lat DOUBLE PRECISION,
    center_lng DOUBLE PRECISION,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Terrains
CREATE TABLE IF NOT EXISTS terrains (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    geo_json TEXT NOT NULL,
    area_sq_meters DOUBLE PRECISION,
    area_hectares DOUBLE PRECISION,
    farm_id BIGINT NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Parcels
CREATE TABLE IF NOT EXISTS parcels (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    geo_json TEXT NOT NULL,
    area_sq_meters DOUBLE PRECISION,
    area_hectares DOUBLE PRECISION,
    status VARCHAR(50) DEFAULT 'DISPONIBLE',
    terrain_id BIGINT NOT NULL REFERENCES terrains(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- NDVI Records (per parcel per satellite capture)
CREATE TABLE IF NOT EXISTS ndvi_records (
    id BIGSERIAL PRIMARY KEY,
    parcel_id BIGINT REFERENCES parcels(id) ON DELETE CASCADE,
    terrain_id BIGINT REFERENCES terrains(id) ON DELETE CASCADE,
    capture_date DATE NOT NULL,
    mean_ndvi DOUBLE PRECISION,
    min_ndvi DOUBLE PRECISION,
    max_ndvi DOUBLE PRECISION,
    std_ndvi DOUBLE PRECISION,
    median_ndvi DOUBLE PRECISION,
    pixel_count INTEGER,
    biomass_kg_per_ha DOUBLE PRECISION,
    vegetation_cover_percent DOUBLE PRECISION,
    planet_scene_id VARCHAR(255),
    cloud_cover_percent DOUBLE PRECISION,
    source VARCHAR(20) DEFAULT 'PLANET',
    created_at TIMESTAMP DEFAULT NOW()
);

-- Rotation History (logs every status change)
CREATE TABLE IF NOT EXISTS rotation_history (
    id BIGSERIAL PRIMARY KEY,
    parcel_id BIGINT NOT NULL REFERENCES parcels(id) ON DELETE CASCADE,
    previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    ndvi_at_change DOUBLE PRECISION,
    biomass_at_change DOUBLE PRECISION,
    note TEXT,
    changed_at TIMESTAMP DEFAULT NOW()
);

-- NDVI Alerts
CREATE TABLE IF NOT EXISTS ndvi_alerts (
    id BIGSERIAL PRIMARY KEY,
    parcel_id BIGINT NOT NULL REFERENCES parcels(id) ON DELETE CASCADE,
    alert_type VARCHAR(50) NOT NULL,
    threshold DOUBLE PRECISION,
    current_value DOUBLE PRECISION,
    message VARCHAR(500),
    severity VARCHAR(20) DEFAULT 'MEDIUM',
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_terrains_farm_id ON terrains(farm_id);
CREATE INDEX IF NOT EXISTS idx_parcels_terrain_id ON parcels(terrain_id);
CREATE INDEX IF NOT EXISTS idx_ndvi_parcel_date ON ndvi_records(parcel_id, capture_date);
CREATE INDEX IF NOT EXISTS idx_ndvi_terrain_date ON ndvi_records(terrain_id, capture_date);
CREATE INDEX IF NOT EXISTS idx_rotation_parcel ON rotation_history(parcel_id, changed_at);

-- =============================================
-- GANADO MODULE v0.2
-- =============================================

-- Lotes (batches of cattle)
CREATE TABLE IF NOT EXISTS lotes (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    fecha_ingreso DATE NOT NULL,
    fecha_salida DATE,
    terrain_id BIGINT NOT NULL REFERENCES terrains(id) ON DELETE CASCADE,
    current_parcel_id BIGINT REFERENCES parcels(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Ganado (individual cattle)
CREATE TABLE IF NOT EXISTS ganados (
    id BIGSERIAL PRIMARY KEY,
    numeracion VARCHAR(100) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    peso_inicial DOUBLE PRECISION NOT NULL,
    peso_actual DOUBLE PRECISION NOT NULL,
    lote_id BIGINT NOT NULL REFERENCES lotes(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Lote-Parcel History (rotation tracking per batch)
CREATE TABLE IF NOT EXISTS lote_parcel_history (
    id BIGSERIAL PRIMARY KEY,
    lote_id BIGINT NOT NULL REFERENCES lotes(id) ON DELETE CASCADE,
    parcel_id BIGINT NOT NULL REFERENCES parcels(id) ON DELETE CASCADE,
    fecha_ingreso DATE NOT NULL,
    fecha_salida DATE
);

CREATE INDEX IF NOT EXISTS idx_lotes_terrain ON lotes(terrain_id);
CREATE INDEX IF NOT EXISTS idx_ganados_lote ON ganados(lote_id);
CREATE INDEX IF NOT EXISTS idx_lote_parcel_hist ON lote_parcel_history(lote_id, fecha_ingreso);
