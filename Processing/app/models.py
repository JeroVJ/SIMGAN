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
    biomassKgPerHa: float | None = None
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