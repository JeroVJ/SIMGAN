import logging

from fastapi import APIRouter, File, Form, UploadFile

from app.models import PlanetProcessResponse, SentinelProcessResponse
from app.services.planet import process_planet_request
from app.services.sentinel import process_sentinel_request


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
