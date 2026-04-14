import base64
import logging
import tempfile
import time
from pathlib import Path

import rasterio
import requests
from fastapi import HTTPException, UploadFile
from pyproj import Transformer
from rasterio.errors import RasterioIOError
from shapely.geometry import Point
from shapely.ops import transform as shapely_transform

from app.config import settings
from app.models import PlanetProcessRequest, PlanetProcessResponse, ProcessedParcelNdviResponse
from app.services.common import build_empty_result, build_result, load_request, parse_geometry, save_upload


logger = logging.getLogger(__name__)


PLANET_BASE_URL = "https://api.planet.com/data/v1"
ASSET_FALLBACKS = [
    "ortho_analytic_4b_sr",
    "ortho_analytic_4b",
    "ortho_analytic_8b_sr",
    "ortho_analytic_8b",
]


def _safe_scene_name(scene_id: str) -> str:
    return "".join(char if char.isalnum() or char in {"-", "_"} else "_" for char in scene_id)


def _planet_auth_header() -> dict[str, str]:
    if not settings.planet_api_key:
        return {}
    token = base64.b64encode(f"{settings.planet_api_key}:".encode("utf-8")).decode("ascii")
    return {"Authorization": f"Basic {token}"}


ACTIVATION_MAX_WAIT_SECONDS = 120
ACTIVATION_POLL_INTERVAL_SECONDS = 5


def _get_fresh_download_url(scene_id: str, asset_type: str | None) -> str | None:
    """Access Planet API directly: activate the asset if needed and poll until the download URL is available."""
    auth = _planet_auth_header()
    if not auth:
        logger.warning("No hay PLANET_API_KEY configurada, no se puede obtener URL fresca")
        return None

    assets_url = f"{PLANET_BASE_URL}/item-types/PSScene/items/{scene_id}/assets"
    try:
        resp = requests.get(assets_url, headers=auth, timeout=30)
        resp.raise_for_status()
    except Exception:
        logger.exception("Error consultando assets Planet para sceneId=%s", scene_id)
        return None

    assets = resp.json()

    # Try the specific asset type first, then fallbacks
    candidates = []
    if asset_type and asset_type in assets:
        candidates.append(asset_type)
    for fb in ASSET_FALLBACKS:
        if fb not in candidates and fb in assets:
            candidates.append(fb)

    for candidate in candidates:
        asset = assets[candidate]
        status = asset.get("status", "")

        # Already active → return URL immediately
        if status == "active" and asset.get("location"):
            logger.info("URL fresca obtenida sceneId=%s assetType=%s", scene_id, candidate)
            return asset["location"]

        # Needs activation → activate and poll
        if status in {"inactive", "activating"}:
            if status == "inactive" and "activate" in asset.get("_links", {}):
                logger.info("Activando asset %s para sceneId=%s", candidate, scene_id)
                try:
                    requests.post(asset["_links"]["activate"], headers=auth, timeout=30)
                except Exception:
                    logger.exception("Error activando asset %s sceneId=%s", candidate, scene_id)
                    continue

            # Poll until active or timeout
            logger.info(
                "Esperando activación asset=%s sceneId=%s maxWait=%ss",
                candidate, scene_id, ACTIVATION_MAX_WAIT_SECONDS,
            )
            deadline = time.time() + ACTIVATION_MAX_WAIT_SECONDS
            while time.time() < deadline:
                time.sleep(ACTIVATION_POLL_INTERVAL_SECONDS)
                try:
                    poll_resp = requests.get(assets_url, headers=auth, timeout=30)
                    poll_resp.raise_for_status()
                    poll_assets = poll_resp.json()
                    poll_asset = poll_assets.get(candidate, {})
                    poll_status = poll_asset.get("status", "")
                    if poll_status == "active" and poll_asset.get("location"):
                        logger.info(
                            "Asset activado exitosamente sceneId=%s assetType=%s",
                            scene_id, candidate,
                        )
                        return poll_asset["location"]
                    logger.debug(
                        "Polling activación sceneId=%s asset=%s status=%s",
                        scene_id, candidate, poll_status,
                    )
                except Exception:
                    logger.exception("Error polling activación sceneId=%s", scene_id)
            logger.warning(
                "Timeout esperando activación asset=%s sceneId=%s",
                candidate, scene_id,
            )

    logger.warning("No se encontró asset activo con location para sceneId=%s", scene_id)
    return None


def _try_download(url: str, output: Path, scene_id: str, label: str) -> bool:
    """Attempt to download from a URL. Returns True on success."""
    auth_headers = _planet_auth_header()

    # For Planet download URLs, try with auth first, then without
    attempts = []
    if auth_headers:
        attempts.append((f"{label}/with-auth", auth_headers))
    attempts.append((f"{label}/without-auth", {}))

    for mode, headers in attempts:
        try:
            logger.info("Descargando Planet sceneId=%s mode=%s", scene_id, mode)
            with requests.get(url, headers=headers, stream=True, timeout=(30, 300)) as response:
                if response.status_code in {401, 403}:
                    body_preview = ""
                    try:
                        body_preview = response.text[:300]
                    except Exception:
                        pass
                    logger.warning(
                        "Descarga Planet falló sceneId=%s mode=%s status=%s body=%s",
                        scene_id, mode, response.status_code, body_preview,
                    )
                    if output.exists():
                        output.unlink()
                    continue
                response.raise_for_status()
                with output.open("wb") as handle:
                    for chunk in response.iter_content(chunk_size=1024 * 1024):
                        if chunk:
                            handle.write(chunk)
            if output.exists() and output.stat().st_size > 0:
                logger.info("Descarga Planet exitosa sceneId=%s mode=%s size=%s", scene_id, mode, output.stat().st_size)
                return True
        except requests.HTTPError as exc:
            status_code = exc.response.status_code if exc.response is not None else "N/A"
            logger.warning("Descarga Planet falló sceneId=%s mode=%s status=%s", scene_id, mode, status_code)
            if output.exists():
                output.unlink()
        except Exception:
            logger.exception("Error inesperado descargando Planet sceneId=%s mode=%s", scene_id, mode)
            if output.exists():
                output.unlink()
    return False


def _download_planet_asset(download_url: str, scene_id: str, work_dir: Path, asset_type: str | None = None) -> Path:
    output = work_dir / f"{_safe_scene_name(scene_id)}.tif"
    if output.exists() and output.stat().st_size > 0:
        logger.info("GeoTIFF Planet reutilizado sceneId=%s file=%s", scene_id, output)
        return output

    # 1) Primary: access Planet API directly with our API key (activate + download)
    fresh_url = _get_fresh_download_url(scene_id, asset_type)
    if fresh_url:
        logger.info("Descargando desde Planet API directamente sceneId=%s", scene_id)
        if _try_download(fresh_url, output, scene_id, "planet-api-direct"):
            return output

    # 2) Fallback: try the URL provided by the backend (may be expired)
    if download_url and download_url != fresh_url:
        logger.warning("URL de Planet API falló, intentando URL del backend sceneId=%s", scene_id)
        if _try_download(download_url, output, scene_id, "backend-url"):
            return output

    raise RuntimeError(
        f"No fue posible descargar el raster Planet para la escena {scene_id}. "
        "Verifica que PLANET_API_KEY esté configurada correctamente y que tu licencia Planet permita la descarga."
    )


def _process_planet_file(payload: PlanetProcessRequest, image_path: Path, started: float) -> PlanetProcessResponse:
    warnings: list[str] = []
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
        return _process_planet_file(payload, image_path, started)
    except RasterioIOError as exc:
        logger.exception("Error abriendo raster Planet terrainId=%s sceneId=%s", payload.terrainId, payload.sceneId)
        raise HTTPException(status_code=500, detail=f"No fue posible leer raster Planet: {exc}") from exc
    except Exception as exc:
        logger.exception("Error procesando Planet terrainId=%s sceneId=%s", payload.terrainId, payload.sceneId)
        raise HTTPException(status_code=500, detail=f"procesamientoImagen no pudo procesar Planet: {exc}") from exc
    finally:
        import shutil
        try:
            shutil.rmtree(work_dir, ignore_errors=True)
        except Exception:
            logger.warning("No se pudo eliminar directorio temporal workDir=%s", work_dir)


async def analyze_planet_request(payload: PlanetProcessRequest) -> PlanetProcessResponse:
    work_dir = Path(tempfile.mkdtemp(prefix="planet-remote-", dir=str(settings.gdal_temp_dir)))
    started = time.perf_counter()
    logger.info(
        "Inicio procesamiento Planet remoto terrainId=%s sceneId=%s assetType=%s parcelas=%s workDir=%s",
        payload.terrainId,
        payload.sceneId,
        payload.assetType,
        len(payload.parcels),
        work_dir,
    )

    try:
        image_path = _download_planet_asset(payload.downloadUrl or "", payload.sceneId, work_dir, payload.assetType)
        return _process_planet_file(payload, image_path, started)
    except requests.HTTPError as exc:
        logger.exception("Error descargando Planet sceneId=%s", payload.sceneId)
        raise HTTPException(status_code=502, detail=f"No fue posible descargar raster Planet: {exc}") from exc
    except RasterioIOError as exc:
        logger.exception("Error abriendo raster Planet remoto terrainId=%s sceneId=%s", payload.terrainId, payload.sceneId)
        raise HTTPException(status_code=500, detail=f"No fue posible leer raster Planet: {exc}") from exc
    except Exception as exc:
        logger.exception("Error procesando Planet remoto terrainId=%s sceneId=%s", payload.terrainId, payload.sceneId)
        raise HTTPException(status_code=500, detail=f"procesamientoImagen no pudo procesar Planet: {exc}") from exc
    finally:
        import shutil
        try:
            shutil.rmtree(work_dir, ignore_errors=True)
            logger.debug("Directorio temporal eliminado workDir=%s", work_dir)
        except Exception:
            logger.warning("No se pudo eliminar directorio temporal workDir=%s", work_dir)