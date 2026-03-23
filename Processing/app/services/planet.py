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
from app.models import PlanetProcessRequest, PlanetProcessResponse, ProcessedParcelNdviResponse
from app.services.common import build_empty_result, build_result, load_request, parse_geometry, save_upload


logger = logging.getLogger(__name__)


def _process_planet_parcel(
    parcel,
    dataset,
    transformer: Transformer,
    red_index: int,
    nir_index: int,
    step: float,
) -> ProcessedParcelNdviResponse:
    geometry = parse_geometry(parcel.geoJson)
    projected = shapely_transform(transformer.transform, geometry)
    minx, miny, maxx, maxy = projected.bounds
    ndvi_values: list[float] = []
    logger.info(
        "Procesando parcela Planet parcelId=%s parcelName=%s bounds=%s step=%s",
        parcel.parcelId,
        parcel.parcelName,
        (minx, miny, maxx, maxy),
        step,
    )

    x = minx
    while x <= maxx:
        y = miny
        while y <= maxy:
            point = Point(x, y)
            if projected.contains(point):
                try:
                    values = next(dataset.sample([(x, y)]))
                    if len(values) > max(red_index, nir_index):
                        red = float(values[red_index])
                        nir = float(values[nir_index])
                        denominator = nir + red
                        if denominator != 0 and (red > 0 or nir > 0):
                            ndvi = (nir - red) / denominator
                            if -1.0 <= ndvi <= 1.0:
                                ndvi_values.append(float(ndvi))
                except Exception:
                    pass
            y += step
        x += step

    if not ndvi_values:
        logger.warning("Sin píxeles NDVI válidos para parcela Planet parcelId=%s", parcel.parcelId)
        return build_empty_result(parcel, "No se encontraron pixeles NDVI válidos dentro de la parcela.")
    logger.info("Parcela Planet procesada parcelId=%s pixelCount=%s", parcel.parcelId, len(ndvi_values))
    return build_result(parcel, ndvi_values)


async def process_planet_request(
    request_payload: str,
    image: UploadFile,
) -> PlanetProcessResponse:
    payload = load_request(request_payload, PlanetProcessRequest)
    work_dir = Path(tempfile.mkdtemp(prefix="planet-", dir=str(settings.gdal_temp_dir)))
    started = time.perf_counter()
    warnings: list[str] = []
    logger.info(
        "Inicio procesamiento Planet terrainId=%s sceneId=%s assetType=%s parcelas=%s workDir=%s",
        payload.terrainId,
        payload.sceneId,
        payload.assetType,
        len(payload.parcels),
        work_dir,
    )

    try:
        image_path = save_upload(image, work_dir)
        with rasterio.open(image_path) as dataset:
            transformer = Transformer.from_crs("EPSG:4326", dataset.crs, always_xy=True)
            num_bands = payload.numBands or dataset.count
            red_index = 5 if num_bands >= 8 else 2
            nir_index = 7 if num_bands >= 8 else 3
            step = max(abs(dataset.res[0]), abs(dataset.res[1]), 3.0)
            logger.info(
                "Raster Planet abierto image=%s width=%s height=%s bands=%s dtypes=%s crs=%s res=%s",
                image_path.name,
                dataset.width,
                dataset.height,
                dataset.count,
                dataset.dtypes,
                dataset.crs,
                dataset.res,
            )

            parcel_results = [
                _process_planet_parcel(parcel, dataset, transformer, red_index, nir_index, step)
                for parcel in payload.parcels
            ]

            for result in parcel_results:
                if result.warning:
                    warnings.append(f"Parcela {result.parcelId}: {result.warning}")

            elapsed_ms = int((time.perf_counter() - started) * 1000)
            logger.info(
                "Fin procesamiento Planet terrainId=%s sceneId=%s processedParcels=%s warnings=%s durationMs=%s",
                payload.terrainId,
                payload.sceneId,
                sum(1 for item in parcel_results if item.pixelCount > 0),
                len(warnings),
                elapsed_ms,
            )

            return PlanetProcessResponse(
                terrainId=payload.terrainId,
                terrainName=payload.terrainName,
                sceneId=payload.sceneId,
                captureDate=payload.captureDate,
                source="PLANET",
                assetType=payload.assetType,
                numBands=num_bands,
                cloudCoverPercent=payload.cloudCoverPercent,
                rasterWidth=dataset.width,
                rasterHeight=dataset.height,
                processingDurationMs=elapsed_ms,
                warnings=warnings,
                parcelResults=parcel_results,
            )
    except RasterioIOError as exc:
        logger.exception("Error abriendo raster Planet terrainId=%s sceneId=%s", payload.terrainId, payload.sceneId)
        raise HTTPException(status_code=500, detail=f"No fue posible leer raster Planet: {exc}") from exc
    except Exception as exc:
        logger.exception("Error procesando Planet terrainId=%s sceneId=%s", payload.terrainId, payload.sceneId)
        raise HTTPException(status_code=500, detail=f"procesamientoImagen no pudo procesar Planet: {exc}") from exc