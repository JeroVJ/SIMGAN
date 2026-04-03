import json
import logging
import shutil
import subprocess
import time
from pathlib import Path
from typing import TypeVar

import numpy as np
from fastapi import HTTPException, UploadFile
from pydantic import BaseModel
from shapely.geometry import shape

from app.config import settings
from app.models import ParcelProcessRequest, ProcessedParcelNdviResponse


ModelType = TypeVar("ModelType", bound=BaseModel)
logger = logging.getLogger(__name__)


def load_request(payload: str, model_cls: type[ModelType]) -> ModelType:
    try:
        model = model_cls.model_validate(json.loads(payload))
        logger.info("Payload %s validado correctamente", model_cls.__name__)
        return model
    except Exception as exc:
        logger.exception("Payload inválido para %s", model_cls.__name__)
        raise HTTPException(status_code=400, detail=f"request inválido: {exc}") from exc


def save_upload(upload: UploadFile, work_dir: Path) -> Path:
    filename = Path(upload.filename or f"upload-{time.time_ns()}").name
    target = work_dir / filename
    with target.open("wb") as output:
        shutil.copyfileobj(upload.file, output)
    logger.info(
        "Archivo guardado filename=%s target=%s sizeBytes=%s",
        filename,
        target,
        target.stat().st_size,
    )
    return target


def convert_jp2_to_tiff(input_path: Path, output_path: Path) -> None:
    command = [
        settings.gdal_translate_command,
        "-of", "GTiff",
        "-ot", "UInt16",
        "-co", "NBITS=16",
        str(input_path),
        str(output_path),
    ]
    logger.info("Ejecutando GDAL command=%s", " ".join(command))
    started = time.perf_counter()
    process = subprocess.run(command, capture_output=True, text=True)
    elapsed_ms = int((time.perf_counter() - started) * 1000)
    if process.returncode != 0 or not output_path.exists() or output_path.stat().st_size == 0:
        output = (process.stdout + process.stderr).strip()
        logger.error(
            "GDAL falló input=%s output=%s exitCode=%s durationMs=%s stdout=%s stderr=%s",
            input_path,
            output_path,
            process.returncode,
            elapsed_ms,
            process.stdout.strip(),
            process.stderr.strip(),
        )
        raise RuntimeError(f"GDAL no pudo convertir {input_path.name}. Salida: {output}")
    logger.info(
        "GDAL finalizado input=%s output=%s exitCode=%s durationMs=%s outputSizeBytes=%s",
        input_path.name,
        output_path.name,
        process.returncode,
        elapsed_ms,
        output_path.stat().st_size,
    )


def parse_geometry(geojson_text: str):
    node = json.loads(geojson_text)
    if isinstance(node, dict) and "features" in node and node["features"]:
        geometry = node["features"][0]["geometry"]
    elif isinstance(node, dict) and "geometry" in node:
        geometry = node["geometry"]
    else:
        geometry = node
    parsed = shape(geometry)
    logger.debug("Geometría parseada type=%s bounds=%s", parsed.geom_type, parsed.bounds)
    return parsed


def build_empty_result(parcel: ParcelProcessRequest, warning: str) -> ProcessedParcelNdviResponse:
    return ProcessedParcelNdviResponse(
        parcelId=parcel.parcelId,
        parcelName=parcel.parcelName,
        pixelCount=0,
        warning=warning,
    )


def build_result(parcel: ParcelProcessRequest, ndvi_values: list[float]) -> ProcessedParcelNdviResponse:
    values = np.array(sorted(ndvi_values), dtype=np.float64)
    mean = float(values.mean())
    min_value = float(values.min())
    max_value = float(values.max())
    median = float(np.median(values))
    std = float(values.std())
    vegetation_cover = float((values > 0.2).sum() / values.size * 100.0)
    biomass = max(0.0, (mean - 0.1) * 12000.0)
    return ProcessedParcelNdviResponse(
        parcelId=parcel.parcelId,
        parcelName=parcel.parcelName,
        meanNdvi=round(mean, 4),
        minNdvi=round(min_value, 4),
        maxNdvi=round(max_value, 4),
        stdNdvi=round(std, 4),
        medianNdvi=round(median, 4),
        pixelCount=int(values.size),
        biomassKgPerHa=round(biomass, 2),
        vegetationCoverPercent=round(vegetation_cover, 2),
    )