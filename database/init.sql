-- =============================================
-- SIMGAN Database Setup v0.1 (NDVI Edition)
-- Spring JPA creates tables automatically,
-- this is reference only.
-- =============================================

-- Farms
CREATE TABLE IF NOT EXISTS farms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    owner VARCHAR(255) NOT NULL,
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
