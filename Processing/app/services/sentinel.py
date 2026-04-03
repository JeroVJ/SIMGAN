import logging
import tempfile
import time
from pathlib import Path

import rasterio
from fastapi import HTTPException, UploadFile
from pyproj import Transformer
from rasterio.errors import RasterioIOError
from shapely.geometry import Point
from shapely.ops import transform as shapely_transform

from app.config import settings
from app.models import ProcessedParcelNdviResponse, SentinelProcessRequest, SentinelProcessResponse
from app.services.common import (
    build_empty_result,
    build_result,
    convert_jp2_to_tiff,
    load_request,
    parse_geometry,
    save_upload,
)


logger = logging.getLogger(__name__)


def _process_sentinel_parcel(
    parcel,
    red_dataset,
    nir_dataset,
    transformer: Transformer,
    ulx: float,
    uly: float,
    pixel_size: float,
) -> ProcessedParcelNdviResponse:
    geometry = parse_geometry(parcel.geoJson)
    projected = shapely_transform(transformer.transform, geometry)
    minx, miny, maxx, maxy = projected.bounds
    ndvi_values: list[float] = []
    logger.info(
        "Procesando parcela Sentinel parcelId=%s parcelName=%s bounds=%s pixelSize=%s",
        parcel.parcelId,
        parcel.parcelName,
        (minx, miny, maxx, maxy),
        pixel_size,
    )

    x = minx
    while x <= maxx:
        y = miny
        while y <= maxy:
            point = Point(x, y)
            if projected.contains(point):
                px = int((x - ulx) / pixel_size)
                py = int((uly - y) / pixel_size)
                if 0 <= px < red_dataset.width and 0 <= py < red_dataset.height:
                    red = float(red_dataset.read(1, window=((py, py + 1), (px, px + 1)))[0, 0])
                    nir = float(nir_dataset.read(1, window=((py, py + 1), (px, px + 1)))[0, 0])
                    if 0 < red < 65535 and 0 < nir < 65535:
                        red_reflectance = red / 10000.0
                        nir_reflectance = nir / 10000.0
                        denominator = nir_reflectance + red_reflectance
                        if denominator != 0:
                            ndvi = (nir_reflectance - red_reflectance) / denominator
                            if -1.0 <= ndvi <= 1.0:
                                ndvi_values.append(float(ndvi))
            y += pixel_size
        x += pixel_size

    if not ndvi_values:
        logger.warning("Sin píxeles NDVI válidos para parcela Sentinel parcelId=%s", parcel.parcelId)
        return build_empty_result(parcel, "No se encontraron pixeles NDVI válidos dentro de la parcela.")
    logger.info("Parcela Sentinel procesada parcelId=%s pixelCount=%s", parcel.parcelId, len(ndvi_values))
    return build_result(parcel, ndvi_values)


async def process_sentinel_request(
    request_payload: str,
    red_band: UploadFile,
    nir_band: UploadFile,
) -> SentinelProcessResponse:
    payload = load_request(request_payload, SentinelProcessRequest)
    work_dir = Path(tempfile.mkdtemp(prefix="sentinel-", dir=str(settings.gdal_temp_dir)))
    started = time.perf_counter()
    warnings: list[str] = []
    logger.info(
        "Inicio procesamiento Sentinel terrainId=%s sceneId=%s epsg=%s parcelas=%s workDir=%s",
        payload.terrainId,
        payload.sceneId,
        payload.epsg,
        len(payload.parcels),
        work_dir,
    )

    try:
        red_path = save_upload(red_band, work_dir)
        nir_path = save_upload(nir_band, work_dir)
        red_tiff = red_path if red_path.suffix.lower() in {".tif", ".tiff"} else work_dir / f"{red_path.stem}.tif"
        nir_tiff = nir_path if nir_path.suffix.lower() in {".tif", ".tiff"} else work_dir / f"{nir_path.stem}.tif"

        if red_tiff != red_path:
            convert_jp2_to_tiff(red_path, red_tiff)
        if nir_tiff != nir_path:
            convert_jp2_to_tiff(nir_path, nir_tiff)

        transformer = Transformer.from_crs("EPSG:4326", f"EPSG:{payload.epsg}", always_xy=True)
        with rasterio.open(red_tiff) as red_dataset, rasterio.open(nir_tiff) as nir_dataset:
            logger.info(
                "Raster Sentinel abierto red=%s nir=%s width=%s height=%s dtypes=%s/%s crs=%s",
                red_tiff.name,
                nir_tiff.name,
                red_dataset.width,
                red_dataset.height,
                red_dataset.dtypes[0],
                nir_dataset.dtypes[0],
                red_dataset.crs,
            )
            parcel_results = [
                _process_sentinel_parcel(
                    parcel,
                    red_dataset,
                    nir_dataset,
                    transformer,
                    payload.ulx,
                    payload.uly,
                    payload.pixelSize or 10.0,
                )
                for parcel in payload.parcels
            ]

            for result in parcel_results:
                if result.warning:
                    warnings.append(f"Parcela {result.parcelId}: {result.warning}")

            elapsed_ms = int((time.perf_counter() - started) * 1000)
            logger.info(
                "Fin procesamiento Sentinel terrainId=%s sceneId=%s processedParcels=%s warnings=%s durationMs=%s",
                payload.terrainId,
                payload.sceneId,
                sum(1 for item in parcel_results if item.pixelCount > 0),
                len(warnings),
                elapsed_ms,
            )

            return SentinelProcessResponse(
                terrainId=payload.terrainId,
                terrainName=payload.terrainName,
                sceneId=payload.sceneId,
                captureDate=payload.captureDate,
                source="SENTINEL",
                epsg=payload.epsg,
                ulx=payload.ulx,
                uly=payload.uly,
                pixelSize=payload.pixelSize or 10.0,
                cloudCoverPercent=payload.cloudCoverPercent,
                rasterWidth=red_dataset.width,
                rasterHeight=red_dataset.height,
                redBandName=red_path.name,
                nirBandName=nir_path.name,
                redGeoTiffName=red_tiff.name,
                nirGeoTiffName=nir_tiff.name,
                processedParcelCount=sum(1 for item in parcel_results if item.pixelCount > 0),
                processingDurationMs=elapsed_ms,
                warnings=warnings,
                parcelResults=parcel_results,
            )
    except RasterioIOError as exc:
        logger.exception("Error abriendo raster Sentinel terrainId=%s sceneId=%s", payload.terrainId, payload.sceneId)
        raise HTTPException(status_code=500, detail=f"No fue posible leer raster Sentinel: {exc}") from exc
    except Exception as exc:
        logger.exception("Error procesando Sentinel terrainId=%s sceneId=%s", payload.terrainId, payload.sceneId)
        raise HTTPException(status_code=500, detail=f"procesamientoImagen no pudo calcular NDVI: {exc}") from exc