from pydantic import BaseModel


class ParcelProcessRequest(BaseModel):
    parcelId: int
    parcelName: str | None = None
    geoJson: str


class ProcessedParcelNdviResponse(BaseModel):
    parcelId: int
    parcelName: str | None = None
    meanNdvi: float | None = None
    minNdvi: float | None = None
    maxNdvi: float | None = None
    stdNdvi: float | None = None
    medianNdvi: float | None = None
    pixelCount: int
    vegetationCoverPercent: float | None = None
    warning: str | None = None


class SentinelProcessRequest(BaseModel):
    terrainId: int
    terrainName: str | None = None
    terrainGeoJson: str | None = None
    sceneId: str
    captureDate: str
    downloadUrl: str | None = None
    epsg: int | None = None
    ulx: float | None = None
    uly: float | None = None
    pixelSize: float | None = 10.0
    cloudCoverPercent: float | None = None
    parcels: list[ParcelProcessRequest]


class SentinelProcessResponse(BaseModel):
    terrainId: int
    terrainName: str | None = None
    sceneId: str
    captureDate: str
    source: str
    epsg: int
    ulx: float
    uly: float
    pixelSize: float
    cloudCoverPercent: float | None = None
    rasterWidth: int
    rasterHeight: int
    redBandName: str
    nirBandName: str
    redGeoTiffName: str
    nirGeoTiffName: str
    processedParcelCount: int
    processingDurationMs: int
    warnings: list[str]
    parcelResults: list[ProcessedParcelNdviResponse]


class PlanetProcessRequest(BaseModel):
    terrainId: int
    terrainName: str | None = None
    terrainGeoJson: str | None = None
    sceneId: str
    captureDate: str
    downloadUrl: str | None = None
    assetType: str | None = None
    numBands: int | None = None
    cloudCoverPercent: float | None = None
    parcels: list[ParcelProcessRequest]


class PlanetProcessResponse(BaseModel):
    terrainId: int
    terrainName: str | None = None
    sceneId: str
    captureDate: str
    source: str
    assetType: str | None = None
    numBands: int | None = None
    cloudCoverPercent: float | None = None
    rasterWidth: int
    rasterHeight: int
    processingDurationMs: int
    warnings: list[str]
    parcelResults: list[ProcessedParcelNdviResponse]


class PointNdviInput(BaseModel):
    pointIndex: int
    latitude: float
    longitude: float
    areaM2: float


class PointNdviResult(BaseModel):
    pointIndex: int
    latitude: float
    longitude: float
    ndvi: float | None = None
    pixelCount: int = 0
    warning: str | None = None


class PointNdviRequest(BaseModel):
    sceneId: str
    downloadUrl: str | None = None
    points: list[PointNdviInput]


class PointNdviResponse(BaseModel):
    sceneId: str
    processingDurationMs: int
    results: list[PointNdviResult]


# ===== TERRAIN-LEVEL NDVI (auto-calibration) =====
# The terrain endpoint computes a single NDVI aggregate over the whole terrain
# polygon — used by the 12-month auto-calibration that derives p25/p75
# thresholds without requiring the terrain to have parcels yet.

class SentinelTerrainAnalyzeRequest(BaseModel):
    terrainId: int
    terrainName: str | None = None
    terrainGeoJson: str
    sceneId: str
    captureDate: str
    downloadUrl: str | None = None
    cloudCoverPercent: float | None = None


class SentinelTerrainAnalyzeResponse(BaseModel):
    terrainId: int
    sceneId: str
    captureDate: str
    cloudCoverPercent: float | None = None
    meanNdvi: float | None = None
    minNdvi: float | None = None
    maxNdvi: float | None = None
    medianNdvi: float | None = None
    stdNdvi: float | None = None
    pixelCount: int = 0
    vegetationCoverPercent: float | None = None
    processingDurationMs: int = 0
    warning: str | None = None