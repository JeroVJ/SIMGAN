import logging

from fastapi import APIRouter, File, Form, UploadFile

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


router = APIRouter(tags=["ndvi"])
logger = logging.getLogger(__name__)


@router.post("/ndvi/sentinel/process", response_model=SentinelProcessResponse)
async def process_sentinel(
    request: str = Form(...),
    redBand: UploadFile = File(...),
    nirBand: UploadFile = File(...),
) -> SentinelProcessResponse:
    logger.info(
        "Request Sentinel recibido redBand=%s nirBand=%s requestBytes=%s",
        redBand.filename,
        nirBand.filename,
        len(request),
    )
    return await process_sentinel_request(request, redBand, nirBand)


@router.post("/ndvi/sentinel/analyze", response_model=SentinelProcessResponse)
async def analyze_sentinel(request: SentinelProcessRequest) -> SentinelProcessResponse:
    logger.info(
        "Request Sentinel analyze recibido sceneId=%s terrainId=%s parcelas=%s",
        request.sceneId,
        request.terrainId,
        len(request.parcels),
    )
    return await analyze_sentinel_request(request)


@router.post("/ndvi/sentinel/point-ndvi", response_model=PointNdviResponse)
async def point_ndvi(request: PointNdviRequest) -> PointNdviResponse:
    logger.info(
        "Request point NDVI recibido sceneId=%s numPoints=%s",
        request.sceneId,
        len(request.points),
    )
    return await compute_point_ndvi(request)


@router.post("/ndvi/planet/process", response_model=PlanetProcessResponse)
async def process_planet(
    request: str = Form(...),
    image: UploadFile = File(...),
) -> PlanetProcessResponse:
    logger.info(
        "Request Planet recibido image=%s requestBytes=%s",
        image.filename,
        len(request),
    )
    return await process_planet_request(request, image)


@router.post("/ndvi/planet/analyze", response_model=PlanetProcessResponse)
async def analyze_planet(request: PlanetProcessRequest) -> PlanetProcessResponse:
    logger.info(
        "Request Planet analyze recibido sceneId=%s terrainId=%s parcelas=%s",
        request.sceneId,
        request.terrainId,
        len(request.parcels),
    )
    return await analyze_planet_request(request)
