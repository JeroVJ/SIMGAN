<<<<<<< HEAD
import logging

from fastapi import APIRouter, File, Form, UploadFile
=======
import asyncio
import logging

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
>>>>>>> origin/procesamiento

from app.models import (
    PlanetProcessRequest,
    PlanetProcessResponse,
    SentinelProcessRequest,
    SentinelProcessResponse,
    PointNdviRequest,
    PointNdviResponse,
)
from app.services.planet import analyze_planet_request, process_planet_request
from app.services.sentinel import analyze_sentinel_request, process_sentinel_request, compute_point_ndvi
<<<<<<< HEAD
=======
from app.workers.tasks import task_sentinel_analyze, task_planet_analyze, task_point_ndvi
>>>>>>> origin/procesamiento


router = APIRouter(tags=["ndvi"])
logger = logging.getLogger(__name__)


<<<<<<< HEAD
=======
# ---------------------------------------------------------------------------
# Sentinel
# ---------------------------------------------------------------------------

>>>>>>> origin/procesamiento
@router.post("/ndvi/sentinel/process", response_model=SentinelProcessResponse)
async def process_sentinel(
    request: str = Form(...),
    redBand: UploadFile = File(...),
    nirBand: UploadFile = File(...),
) -> SentinelProcessResponse:
<<<<<<< HEAD
=======
    """Upload-based sentinel processing (bands as files) — runs directly, not queued."""
>>>>>>> origin/procesamiento
    logger.info(
        "Request Sentinel recibido redBand=%s nirBand=%s requestBytes=%s",
        redBand.filename,
        nirBand.filename,
        len(request),
    )
    return await process_sentinel_request(request, redBand, nirBand)


@router.post("/ndvi/sentinel/analyze", response_model=SentinelProcessResponse)
async def analyze_sentinel(request: SentinelProcessRequest) -> SentinelProcessResponse:
<<<<<<< HEAD
    logger.info(
        "Request Sentinel analyze recibido sceneId=%s terrainId=%s parcelas=%s",
=======
    """Enqueue a Sentinel NDVI analysis job and wait for the worker result."""
    logger.info(
        "Encolando job Sentinel analyze sceneId=%s terrainId=%s parcelas=%s",
>>>>>>> origin/procesamiento
        request.sceneId,
        request.terrainId,
        len(request.parcels),
    )
<<<<<<< HEAD
    return await analyze_sentinel_request(request)
=======
    task = task_sentinel_analyze.apply_async(args=[request.model_dump()], queue="ndvi")
    try:
        result_dict = await asyncio.to_thread(task.get, timeout=660, propagate=True)
    except Exception as exc:
        logger.error("Error en worker Sentinel sceneId=%s: %s", request.sceneId, exc)
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return SentinelProcessResponse(**result_dict)
>>>>>>> origin/procesamiento


@router.post("/ndvi/sentinel/point-ndvi", response_model=PointNdviResponse)
async def point_ndvi(request: PointNdviRequest) -> PointNdviResponse:
<<<<<<< HEAD
    logger.info(
        "Request point NDVI recibido sceneId=%s numPoints=%s",
        request.sceneId,
        len(request.points),
    )
    return await compute_point_ndvi(request)


=======
    """Enqueue a point-NDVI computation job and wait for the worker result."""
    logger.info(
        "Encolando job point-NDVI sceneId=%s numPoints=%s",
        request.sceneId,
        len(request.points),
    )
    task = task_point_ndvi.apply_async(args=[request.model_dump()], queue="ndvi")
    try:
        result_dict = await asyncio.to_thread(task.get, timeout=660, propagate=True)
    except Exception as exc:
        logger.error("Error en worker point-NDVI sceneId=%s: %s", request.sceneId, exc)
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return PointNdviResponse(**result_dict)


# ---------------------------------------------------------------------------
# Planet
# ---------------------------------------------------------------------------

>>>>>>> origin/procesamiento
@router.post("/ndvi/planet/process", response_model=PlanetProcessResponse)
async def process_planet(
    request: str = Form(...),
    image: UploadFile = File(...),
) -> PlanetProcessResponse:
<<<<<<< HEAD
=======
    """Upload-based Planet processing (image as file) — runs directly, not queued."""
>>>>>>> origin/procesamiento
    logger.info(
        "Request Planet recibido image=%s requestBytes=%s",
        image.filename,
        len(request),
    )
    return await process_planet_request(request, image)


@router.post("/ndvi/planet/analyze", response_model=PlanetProcessResponse)
async def analyze_planet(request: PlanetProcessRequest) -> PlanetProcessResponse:
<<<<<<< HEAD
    logger.info(
        "Request Planet analyze recibido sceneId=%s terrainId=%s parcelas=%s",
=======
    """Enqueue a Planet NDVI analysis job and wait for the worker result."""
    logger.info(
        "Encolando job Planet analyze sceneId=%s terrainId=%s parcelas=%s",
>>>>>>> origin/procesamiento
        request.sceneId,
        request.terrainId,
        len(request.parcels),
    )
<<<<<<< HEAD
    return await analyze_planet_request(request)
=======
    task = task_planet_analyze.apply_async(args=[request.model_dump()], queue="ndvi")
    try:
        result_dict = await asyncio.to_thread(task.get, timeout=660, propagate=True)
    except Exception as exc:
        logger.error("Error en worker Planet sceneId=%s: %s", request.sceneId, exc)
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return PlanetProcessResponse(**result_dict)

>>>>>>> origin/procesamiento
