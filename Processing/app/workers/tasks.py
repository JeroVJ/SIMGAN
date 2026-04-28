"""
Celery tasks for satellite NDVI processing.

Each task is a thin wrapper that deserializes the JSON payload into the
corresponding Pydantic request model, calls the existing async service
function (via asyncio.run so it works in a synchronous Celery context),
and returns the result serialized back to a plain dict for Redis storage.
"""
import asyncio
import logging

from fastapi import HTTPException

from app.Config.celery_app import celery_app
from app.models import (
    PlanetProcessRequest,
    PointNdviRequest,
    SentinelProcessRequest,
    SentinelTerrainAnalyzeRequest,
)

logger = logging.getLogger(__name__)


@celery_app.task(name="ndvi.sentinel.analyze", bind=True, max_retries=0)
def task_sentinel_analyze(self, request_dict: dict) -> dict:
    from app.services.sentinel import analyze_sentinel_request

    request = SentinelProcessRequest(**request_dict)
    logger.info(
        "Worker procesando Sentinel: terrainId=%s sceneId=%s parcelas=%s",
        request.terrainId,
        request.sceneId,
        len(request.parcels),
    )
    try:
        result = asyncio.run(analyze_sentinel_request(request))
        return result.model_dump()
    except HTTPException as exc:
        raise RuntimeError(exc.detail) from exc


@celery_app.task(name="ndvi.planet.analyze", bind=True, max_retries=0)
def task_planet_analyze(self, request_dict: dict) -> dict:
    from app.services.planet import analyze_planet_request

    request = PlanetProcessRequest(**request_dict)
    logger.info(
        "Worker procesando Planet: terrainId=%s sceneId=%s parcelas=%s",
        request.terrainId,
        request.sceneId,
        len(request.parcels),
    )
    try:
        result = asyncio.run(analyze_planet_request(request))
        return result.model_dump()
    except HTTPException as exc:
        raise RuntimeError(exc.detail) from exc


@celery_app.task(name="ndvi.sentinel.analyze_terrain", bind=True, max_retries=0)
def task_sentinel_analyze_terrain(self, request_dict: dict) -> dict:
    from app.services.sentinel import analyze_sentinel_terrain_request

    request = SentinelTerrainAnalyzeRequest(**request_dict)
    logger.info(
        "Worker procesando terrain Sentinel: terrainId=%s sceneId=%s",
        request.terrainId,
        request.sceneId,
    )
    try:
        result = asyncio.run(analyze_sentinel_terrain_request(request))
        return result.model_dump()
    except HTTPException as exc:
        raise RuntimeError(exc.detail) from exc


@celery_app.task(name="ndvi.point_ndvi", bind=True, max_retries=0)
def task_point_ndvi(self, request_dict: dict) -> dict:
    from app.services.sentinel import compute_point_ndvi

    request = PointNdviRequest(**request_dict)
    logger.info(
        "Worker procesando point-NDVI: sceneId=%s puntos=%s",
        request.sceneId,
        len(request.points),
    )
    try:
        result = asyncio.run(compute_point_ndvi(request))
        return result.model_dump()
    except HTTPException as exc:
        raise RuntimeError(exc.detail) from exc