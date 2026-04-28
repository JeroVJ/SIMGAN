import logging
import re
import tempfile
import time
import zipfile
from pathlib import Path

import numpy as np
import rasterio
import requests
from fastapi import HTTPException, UploadFile
from pyproj import Transformer
from rasterio.errors import RasterioIOError
from shapely.geometry import Point
from shapely.ops import transform as shapely_transform

from app.Config import settings
from app.models import (
    PointNdviRequest,
    PointNdviResponse,
    PointNdviResult,
    ProcessedParcelNdviResponse,
    SentinelProcessRequest,
    SentinelProcessResponse,
    SentinelTerrainAnalyzeRequest,
    SentinelTerrainAnalyzeResponse,
)
from app.services.common import (
    build_empty_result,
    build_result,
    convert_jp2_to_tiff,
    load_request,
    parse_geometry,
    save_upload,
)


logger = logging.getLogger(__name__)

TOKEN_URL = "https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/token"
ODATA_CATALOG = "https://catalogue.dataspace.copernicus.eu/odata/v1"
ODATA_DOWNLOAD = "https://zipper.dataspace.copernicus.eu/odata/v1"
MIN_ZIP_SIZE_BYTES = 10_000

_cached_token: str | None = None
_token_expiry_epoch: float = 0.0


def _safe_scene_name(scene_id: str) -> str:
    return "".join(char if char.isalnum() or char in {"-", "_"} else "_" for char in scene_id)


def _delete_quietly(path: Path | None) -> None:
    try:
        if path and path.exists():
            path.unlink()
    except Exception:
        pass


def _is_valid_zip(path: Path) -> bool:
    if not path.exists() or path.stat().st_size < MIN_ZIP_SIZE_BYTES:
        return False
    try:
        with zipfile.ZipFile(path) as handle:
            return any(True for _ in handle.infolist())
    except Exception:
        return False


def _is_usable_band_file(path: Path | None) -> bool:
    if path is None or not path.exists() or path.stat().st_size < 1000:
        return False

    suffix = path.suffix.lower()
    with path.open("rb") as handle:
        header = handle.read(12)
    if suffix == ".jp2":
        return len(header) >= 8 and header[4:8] == b"jP  "
    if suffix in {".tif", ".tiff"}:
        return len(header) >= 4 and header[:4] in {b"II*\x00", b"MM\x00*"}
    return True


def _parse_granule_metadata(metadata_file: Path) -> tuple[int, float, float]:
    content = metadata_file.read_text(encoding="utf-8", errors="ignore")
    epsg_match = re.search(r"EPSG:(\d+)", content)
    ulx_match = re.search(r'<Geoposition\s+resolution="10"[\s\S]*?<ULX>([-0-9.]+)</ULX>', content)
    uly_match = re.search(r'<Geoposition\s+resolution="10"[\s\S]*?<ULY>([-0-9.]+)</ULY>', content)
    epsg = int(epsg_match.group(1)) if epsg_match else 32618
    ulx = float(ulx_match.group(1)) if ulx_match else 600000.0
    uly = float(uly_match.group(1)) if uly_match else 500000.0
    return epsg, ulx, uly


def _extract_bands_from_zip(zip_path: Path, output_dir: Path) -> tuple[Path, Path, int, float, float]:
    red_file: Path | None = None
    nir_file: Path | None = None
    metadata_file: Path | None = None

    with zipfile.ZipFile(zip_path) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            name = entry.filename.lower()
            is_b04 = "_b04_10m.jp2" in name or "_b04_10m.tif" in name
            is_b08 = "_b08_10m.jp2" in name or "_b08_10m.tif" in name
            is_mtd = name.endswith("mtd_tl.xml") and "granule" in name
            if not any((is_b04, is_b08, is_mtd)):
                continue

            if is_b04:
                target = output_dir / ("B04_10m.tif" if name.endswith(".tif") else "B04_10m.jp2")
            elif is_b08:
                target = output_dir / ("B08_10m.tif" if name.endswith(".tif") else "B08_10m.jp2")
            else:
                target = output_dir / "MTD_TL.xml"

            logger.info("Extrayendo Sentinel entry=%s target=%s", entry.filename, target.name)
            with archive.open(entry) as source, target.open("wb") as destination:
                destination.write(source.read())

            if is_b04:
                red_file = target
            elif is_b08:
                nir_file = target
            elif is_mtd:
                metadata_file = target

    if not _is_usable_band_file(red_file) or not _is_usable_band_file(nir_file):
        raise RuntimeError("Las bandas B04/B08 extraídas desde Sentinel no son válidas.")

    if metadata_file and metadata_file.exists():
        epsg, ulx, uly = _parse_granule_metadata(metadata_file)
    else:
        epsg, ulx, uly = 32618, 600000.0, 500000.0

    return red_file, nir_file, epsg, ulx, uly


def _get_access_token() -> str:
    global _cached_token, _token_expiry_epoch

    if _cached_token and time.time() < _token_expiry_epoch:
        return _cached_token

    if not settings.copernicus_username or not settings.copernicus_password:
        raise RuntimeError("Processing no tiene COPERNICUS_USERNAME/COPERNICUS_PASSWORD configurados.")

    response = requests.post(
        TOKEN_URL,
        data={
            "grant_type": "password",
            "username": settings.copernicus_username,
            "password": settings.copernicus_password,
            "client_id": "cdse-public",
        },
        timeout=(30, 120),
    )
    response.raise_for_status()
    payload = response.json()
    _cached_token = payload["access_token"]
    _token_expiry_epoch = time.time() + max(int(payload.get("expires_in", 600)) - 30, 60)
    return _cached_token


def _find_product_uuid(product_name: str) -> str | None:
    clean_name = product_name.replace(".SAFE", "")
    response = requests.get(
        f"{ODATA_CATALOG}/Products?$filter=contains(Name,'{clean_name}')&$top=1",
        headers={"Accept": "application/json"},
        timeout=(30, 120),
    )
    response.raise_for_status()
    values = response.json().get("value", [])
    if not values:
        return None
    return values[0].get("Id")


def _download_with_auth(url: str, output: Path, token: str, max_retries: int = 5) -> None:
    """Descarga con soporte de reanudación (Range) y reintentos ante ConnectionReset."""
    import time

    for attempt in range(1, max_retries + 1):
        downloaded = output.stat().st_size if output.exists() else 0
        headers = {"Authorization": f"Bearer {token}"}
        if downloaded > 0:
            headers["Range"] = f"bytes={downloaded}-"
            logger.info("Reanudando descarga desde byte %d (intento %d/%d)", downloaded, attempt, max_retries)

        try:
            with requests.get(url, headers=headers, stream=True, timeout=(30, 300)) as response:
                if response.status_code == 416:
                    # El servidor dice que el rango pedido ya está completo
                    return
                response.raise_for_status()
                mode = "ab" if downloaded > 0 and response.status_code == 206 else "wb"
                if mode == "wb" and downloaded > 0:
                    output.unlink()  # reiniciar si el servidor no soporta Range
                with output.open(mode) as handle:
                    for chunk in response.iter_content(chunk_size=1024 * 1024):
                        if chunk:
                            handle.write(chunk)
            return  # descarga completa
        except (requests.exceptions.ChunkedEncodingError,
                requests.exceptions.ConnectionError,
                requests.exceptions.Timeout) as exc:
            if attempt == max_retries:
                raise
            wait = 10 * attempt
            logger.warning("Descarga interrumpida (intento %d/%d): %s — reintentando en %ds", attempt, max_retries, exc, wait)
            time.sleep(wait)


def _download_bands_for_scene(
    scene_id: str,
    download_url: str | None,
    work_dir: Path,
) -> tuple[Path, Path, int, float, float]:
    """Resolve the OData download URL (if missing/STAC-only), fetch the SAFE ZIP,
    and extract red/nir bands + UTM metadata. Shared by per-parcel and
    terrain-level analyses."""
    token = _get_access_token()
    resolved_url = download_url

    if not resolved_url or "('S2" in resolved_url:
        uuid = _find_product_uuid(scene_id)
        if not uuid:
            raise RuntimeError(f"No se encontró UUID OData para la escena Sentinel {scene_id}.")
        resolved_url = f"{ODATA_DOWNLOAD}/Products({uuid})/$value"

    zip_path = work_dir / "product.zip"
    if zip_path.exists() and not _is_valid_zip(zip_path):
        _delete_quietly(zip_path)

    if not zip_path.exists():
        _download_with_auth(resolved_url, zip_path, token)

    if not _is_valid_zip(zip_path):
        raise RuntimeError(f"El ZIP descargado para Sentinel {scene_id} es inválido.")

    return _extract_bands_from_zip(zip_path, work_dir)


def _download_and_extract_bands(payload: SentinelProcessRequest, work_dir: Path) -> tuple[Path, Path, int, float, float]:
    return _download_bands_for_scene(payload.sceneId, payload.downloadUrl, work_dir)


def _process_sentinel_files(
    payload: SentinelProcessRequest,
    red_path: Path,
    nir_path: Path,
    epsg: int,
    ulx: float,
    uly: float,
    started: float,
) -> SentinelProcessResponse:
    warnings: list[str] = []
    red_tiff = red_path if red_path.suffix.lower() in {".tif", ".tiff"} else red_path.with_suffix(".tif")
    nir_tiff = nir_path if nir_path.suffix.lower() in {".tif", ".tiff"} else nir_path.with_suffix(".tif")

    if red_tiff != red_path:
        convert_jp2_to_tiff(red_path, red_tiff)
    if nir_tiff != nir_path:
        convert_jp2_to_tiff(nir_path, nir_tiff)

    transformer = Transformer.from_crs("EPSG:4326", f"EPSG:{epsg}", always_xy=True)
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
                ulx,
                uly,
                payload.pixelSize or 10.0,
            )
            for parcel in payload.parcels
        ]

        for result in parcel_results:
            if result.warning:
                warnings.append(f"Parcela {result.parcelId}: {result.warning}")

        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return SentinelProcessResponse(
            terrainId=payload.terrainId,
            terrainName=payload.terrainName,
            sceneId=payload.sceneId,
            captureDate=payload.captureDate,
            source="SENTINEL",
            epsg=epsg,
            ulx=ulx,
            uly=uly,
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
        epsg = payload.epsg or 32618
        ulx = payload.ulx or 600000.0
        uly = payload.uly or 500000.0
        return _process_sentinel_files(payload, red_path, nir_path, epsg, ulx, uly, started)
    except RasterioIOError as exc:
        logger.exception("Error abriendo raster Sentinel terrainId=%s sceneId=%s", payload.terrainId, payload.sceneId)
        raise HTTPException(status_code=500, detail=f"No fue posible leer raster Sentinel: {exc}") from exc
    except Exception as exc:
        logger.exception("Error procesando Sentinel terrainId=%s sceneId=%s", payload.terrainId, payload.sceneId)
        raise HTTPException(status_code=500, detail=f"procesamientoImagen no pudo calcular NDVI: {exc}") from exc
    finally:
        import shutil
        try:
            shutil.rmtree(work_dir, ignore_errors=True)
        except Exception:
            logger.warning("No se pudo eliminar directorio temporal workDir=%s", work_dir)


async def analyze_sentinel_request(payload: SentinelProcessRequest) -> SentinelProcessResponse:
    work_dir = Path(tempfile.mkdtemp(prefix="sentinel-remote-", dir=str(settings.gdal_temp_dir)))
    started = time.perf_counter()
    logger.info(
        "Inicio procesamiento Sentinel remoto terrainId=%s sceneId=%s parcelas=%s workDir=%s",
        payload.terrainId,
        payload.sceneId,
        len(payload.parcels),
        work_dir,
    )

    try:
        red_path, nir_path, epsg, ulx, uly = _download_and_extract_bands(payload, work_dir)
        return _process_sentinel_files(payload, red_path, nir_path, epsg, ulx, uly, started)
    except requests.HTTPError as exc:
        logger.exception("Error descargando Sentinel sceneId=%s", payload.sceneId)
        raise HTTPException(status_code=502, detail=f"No fue posible descargar Sentinel: {exc}") from exc
    except RasterioIOError as exc:
        logger.exception("Error abriendo raster Sentinel remoto terrainId=%s sceneId=%s", payload.terrainId, payload.sceneId)
        raise HTTPException(status_code=500, detail=f"No fue posible leer raster Sentinel: {exc}") from exc
    except Exception as exc:
        logger.exception("Error procesando Sentinel remoto terrainId=%s sceneId=%s", payload.terrainId, payload.sceneId)
        raise HTTPException(status_code=500, detail=f"procesamientoImagen no pudo calcular NDVI: {exc}") from exc
    finally:
        import shutil
        try:
            shutil.rmtree(work_dir, ignore_errors=True)
        except Exception:
            logger.warning("No se pudo eliminar directorio temporal workDir=%s", work_dir)


async def compute_point_ndvi(payload: PointNdviRequest) -> PointNdviResponse:
    """Calculate NDVI at specific geographic points using a Sentinel scene.

    For each point, a circular area matching the given areaM2 is sampled.
    """
    import math
    import shutil

    work_dir = Path(tempfile.mkdtemp(prefix="sentinel-points-", dir=str(settings.gdal_temp_dir)))
    started = time.perf_counter()
    logger.info("Inicio cálculo NDVI en puntos sceneId=%s numPoints=%s workDir=%s", payload.sceneId, len(payload.points), work_dir)

    try:
        dummy = SentinelProcessRequest(
            terrainId=0,
            sceneId=payload.sceneId,
            captureDate="1970-01-01",
            downloadUrl=payload.downloadUrl,
            parcels=[],
        )
        red_path, nir_path, epsg, ulx, uly = _download_and_extract_bands(dummy, work_dir)

        red_tiff = red_path if red_path.suffix.lower() in {".tif", ".tiff"} else red_path.with_suffix(".tif")
        nir_tiff = nir_path if nir_path.suffix.lower() in {".tif", ".tiff"} else nir_path.with_suffix(".tif")
        if red_tiff != red_path:
            convert_jp2_to_tiff(red_path, red_tiff)
        if nir_tiff != nir_path:
            convert_jp2_to_tiff(nir_path, nir_tiff)

        transformer = Transformer.from_crs("EPSG:4326", f"EPSG:{epsg}", always_xy=True)
        pixel_size = 10.0

        results: list[PointNdviResult] = []

        def _read_ndvi(red_ds, nir_ds, row, col):
            """Read a single pixel and return its NDVI or None."""
            if not (0 <= col < red_ds.width and 0 <= row < red_ds.height):
                return None
            red = float(red_ds.read(1, window=((row, row + 1), (col, col + 1)))[0, 0])
            nir = float(nir_ds.read(1, window=((row, row + 1), (col, col + 1)))[0, 0])
            if not (0 < red < 65535 and 0 < nir < 65535):
                return None
            red_r = red / 10000.0
            nir_r = nir / 10000.0
            denom = nir_r + red_r
            if denom == 0:
                return None
            ndvi = (nir_r - red_r) / denom
            return ndvi if -1.0 <= ndvi <= 1.0 else None

        with rasterio.open(red_tiff) as red_ds, rasterio.open(nir_tiff) as nir_ds:
            for pt in payload.points:
                try:
                    px_utm, py_utm = transformer.transform(pt.longitude, pt.latitude)
                    radius = math.sqrt(max(pt.areaM2, 1.0) / math.pi)

                    sampled: set[tuple[int, int]] = set()
                    ndvi_values: list[float] = []

                    # Always sample the center pixel (handles sub-pixel areas)
                    center_col = int((px_utm - ulx) / pixel_size)
                    center_row = int((uly - py_utm) / pixel_size)
                    sampled.add((center_row, center_col))

                    # For areas larger than one pixel, sample surrounding pixels
                    if radius > pixel_size / 2:
                        col_min = int((px_utm - radius - ulx) / pixel_size)
                        col_max = int((px_utm + radius - ulx) / pixel_size)
                        row_min = int((uly - py_utm - radius) / pixel_size)
                        row_max = int((uly - py_utm + radius) / pixel_size)
                        for r in range(max(0, row_min), min(red_ds.height, row_max + 1)):
                            for c in range(max(0, col_min), min(red_ds.width, col_max + 1)):
                                px_x = ulx + c * pixel_size + pixel_size / 2
                                px_y = uly - r * pixel_size - pixel_size / 2
                                if (px_x - px_utm) ** 2 + (px_y - py_utm) ** 2 <= radius ** 2:
                                    sampled.add((r, c))

                    for r, c in sampled:
                        ndvi = _read_ndvi(red_ds, nir_ds, r, c)
                        if ndvi is not None:
                            ndvi_values.append(ndvi)

                    if ndvi_values:
                        mean_ndvi = sum(ndvi_values) / len(ndvi_values)
                        results.append(PointNdviResult(
                            pointIndex=pt.pointIndex,
                            latitude=pt.latitude,
                            longitude=pt.longitude,
                            ndvi=round(mean_ndvi, 4),
                            pixelCount=len(ndvi_values),
                        ))
                    else:
                        results.append(PointNdviResult(
                            pointIndex=pt.pointIndex,
                            latitude=pt.latitude,
                            longitude=pt.longitude,
                            warning="No se encontraron píxeles NDVI válidos en el área del punto.",
                        ))
                except Exception as e:
                    logger.warning("Error calculando NDVI en punto %s: %s", pt.pointIndex, e)
                    results.append(PointNdviResult(
                        pointIndex=pt.pointIndex,
                        latitude=pt.latitude,
                        longitude=pt.longitude,
                        warning=f"Error: {e}",
                    ))

        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return PointNdviResponse(sceneId=payload.sceneId, processingDurationMs=elapsed_ms, results=results)

    except Exception as exc:
        logger.exception("Error calculando NDVI en puntos sceneId=%s", payload.sceneId)
        raise HTTPException(status_code=500, detail=f"Error calculando NDVI en puntos: {exc}") from exc
    finally:
        try:
            shutil.rmtree(work_dir, ignore_errors=True)
        except Exception:
            logger.warning("No se pudo eliminar directorio temporal workDir=%s", work_dir)


# ===== TERRAIN-LEVEL NDVI =====
# Used by the 12-month auto-calibration. Aggregates NDVI over the whole
# terrain polygon — no parcels required. The output feeds p25/p75 percentiles
# that get applied as ALERT/OPTIM thresholds across all parcels of the terrain.

def _process_sentinel_terrain_files(
    payload: SentinelTerrainAnalyzeRequest,
    red_path: Path,
    nir_path: Path,
    epsg: int,
    ulx: float,
    uly: float,
    started: float,
) -> SentinelTerrainAnalyzeResponse:
    pixel_size = 10.0
    red_tiff = red_path if red_path.suffix.lower() in {".tif", ".tiff"} else red_path.with_suffix(".tif")
    nir_tiff = nir_path if nir_path.suffix.lower() in {".tif", ".tiff"} else nir_path.with_suffix(".tif")
    if red_tiff != red_path:
        convert_jp2_to_tiff(red_path, red_tiff)
    if nir_tiff != nir_path:
        convert_jp2_to_tiff(nir_path, nir_tiff)

    transformer = Transformer.from_crs("EPSG:4326", f"EPSG:{epsg}", always_xy=True)
    geometry = parse_geometry(payload.terrainGeoJson)
    projected = shapely_transform(transformer.transform, geometry)
    minx, miny, maxx, maxy = projected.bounds
    ndvi_values: list[float] = []

    with rasterio.open(red_tiff) as red_dataset, rasterio.open(nir_tiff) as nir_dataset:
        logger.info(
            "Procesando terrain Sentinel terrainId=%s sceneId=%s bounds=%s",
            payload.terrainId, payload.sceneId, (minx, miny, maxx, maxy),
        )
        x = minx
        while x <= maxx:
            y = miny
            while y <= maxy:
                if projected.contains(Point(x, y)):
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

    elapsed_ms = int((time.perf_counter() - started) * 1000)
    if not ndvi_values:
        logger.warning(
            "Sin pixeles NDVI válidos para terrain Sentinel terrainId=%s sceneId=%s",
            payload.terrainId, payload.sceneId,
        )
        return SentinelTerrainAnalyzeResponse(
            terrainId=payload.terrainId,
            sceneId=payload.sceneId,
            captureDate=payload.captureDate,
            cloudCoverPercent=payload.cloudCoverPercent,
            pixelCount=0,
            processingDurationMs=elapsed_ms,
            warning="No se encontraron pixeles NDVI válidos dentro del terreno.",
        )

    arr = np.array(sorted(ndvi_values), dtype=np.float64)
    logger.info(
        "Terrain Sentinel procesado terrainId=%s sceneId=%s pixelCount=%s meanNdvi=%.4f",
        payload.terrainId, payload.sceneId, int(arr.size), float(arr.mean()),
    )
    return SentinelTerrainAnalyzeResponse(
        terrainId=payload.terrainId,
        sceneId=payload.sceneId,
        captureDate=payload.captureDate,
        cloudCoverPercent=payload.cloudCoverPercent,
        meanNdvi=round(float(arr.mean()), 4),
        minNdvi=round(float(arr.min()), 4),
        maxNdvi=round(float(arr.max()), 4),
        medianNdvi=round(float(np.median(arr)), 4),
        stdNdvi=round(float(arr.std()), 4),
        pixelCount=int(arr.size),
        vegetationCoverPercent=round(float((arr > 0.2).sum() / arr.size * 100.0), 2),
        processingDurationMs=elapsed_ms,
    )


async def analyze_sentinel_terrain_request(
    payload: SentinelTerrainAnalyzeRequest,
) -> SentinelTerrainAnalyzeResponse:
    import shutil
    work_dir = Path(tempfile.mkdtemp(prefix="sentinel-terrain-", dir=str(settings.gdal_temp_dir)))
    started = time.perf_counter()
    logger.info(
        "Inicio análisis terrain Sentinel terrainId=%s sceneId=%s workDir=%s",
        payload.terrainId, payload.sceneId, work_dir,
    )

    try:
        red_path, nir_path, epsg, ulx, uly = _download_bands_for_scene(
            payload.sceneId, payload.downloadUrl, work_dir,
        )
        return _process_sentinel_terrain_files(payload, red_path, nir_path, epsg, ulx, uly, started)
    except requests.HTTPError as exc:
        logger.exception("Error descargando Sentinel sceneId=%s", payload.sceneId)
        raise HTTPException(status_code=502, detail=f"No fue posible descargar Sentinel: {exc}") from exc
    except RasterioIOError as exc:
        logger.exception("Error abriendo raster terrain Sentinel sceneId=%s", payload.sceneId)
        raise HTTPException(status_code=500, detail=f"No fue posible leer raster Sentinel: {exc}") from exc
    except Exception as exc:
        logger.exception("Error procesando terrain Sentinel sceneId=%s", payload.sceneId)
        raise HTTPException(status_code=500, detail=f"procesamientoImagen no pudo calcular NDVI: {exc}") from exc
    finally:
        try:
            shutil.rmtree(work_dir, ignore_errors=True)
        except Exception:
            logger.warning("No se pudo eliminar directorio temporal workDir=%s", work_dir)