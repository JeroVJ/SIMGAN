import os
import tempfile
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

# Cargar .env del proyecto raíz (../)
_env_path = Path(__file__).resolve().parent.parent.parent / ".env"
load_dotenv(_env_path)


@dataclass(frozen=True)
class Settings:
    api_prefix: str
    gdal_translate_command: str
    gdal_temp_dir: Path
    log_level: str
    planet_api_key: str
    copernicus_username: str
    copernicus_password: str


def _build_settings() -> Settings:
    api_prefix = os.getenv("SERVER_CONTEXT_PATH", "/api").rstrip("/") or ""
    gdal_translate_command = os.getenv("PROCESSING_GDAL_TRANSLATE_COMMAND", "gdal_translate")
    gdal_temp_dir = Path(
        os.getenv("PROCESSING_GDAL_TEMP_DIR", f"{tempfile.gettempdir()}/simgan-procesamiento")
    )
    log_level = os.getenv("PROCESSING_LOG_LEVEL", "INFO").upper()
    planet_api_key = os.getenv("PLANET_API_KEY", "")
    copernicus_username = os.getenv("COPERNICUS_USERNAME", "")
    copernicus_password = os.getenv("COPERNICUS_PASSWORD", "")
    gdal_temp_dir.mkdir(parents=True, exist_ok=True)
    return Settings(
        api_prefix=api_prefix,
        gdal_translate_command=gdal_translate_command,
        gdal_temp_dir=gdal_temp_dir,
        log_level=log_level,
        planet_api_key=planet_api_key,
        copernicus_username=copernicus_username,
        copernicus_password=copernicus_password,
    )


settings = _build_settings()