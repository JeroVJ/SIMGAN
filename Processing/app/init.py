import logging
import sys

from fastapi import FastAPI

from app.api.routes import router
from app.config import settings


def _configure_logging() -> logging.Logger:
    logging.basicConfig(
        level=getattr(logging, settings.log_level, logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s :: %(message)s",
        stream=sys.stdout,
        force=True,
    )
    root_logger = logging.getLogger()
    root_logger.setLevel(getattr(logging, settings.log_level, logging.INFO))
    for logger_name in ("procesamientoImagen", "app", "app.api.routes", "app.services"):
        named_logger = logging.getLogger(logger_name)
        named_logger.setLevel(getattr(logging, settings.log_level, logging.INFO))
    return logging.getLogger("procesamientoImagen")


def create_app() -> FastAPI:
    logger = _configure_logging()
    app = FastAPI(title="procesamientoImagen")
    logger.info(
        "Inicializando app api_prefix=%s gdal_command=%s temp_dir=%s log_level=%s",
        settings.api_prefix,
        settings.gdal_translate_command,
        settings.gdal_temp_dir,
        settings.log_level,
    )
    app.include_router(router, prefix=settings.api_prefix)
    return app
