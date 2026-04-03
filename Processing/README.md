# Processing

Servicio Python/FastAPI separado del backend principal para procesar bandas Sentinel y Planet con GDAL y devolver resultados NDVI por parcela.

## Responsabilidad

- Recibe `B04` y `B08` desde el backend como `multipart/form-data`.
- Recibe también GeoTIFF de Planet desde el backend como `multipart/form-data`.
- Convierte JP2 a GeoTIFF usando `gdal_translate`.
- Calcula NDVI por parcela usando el `geoJson` recibido.
- Devuelve métricas por parcela y metadatos adicionales del raster.

## Endpoint

- `POST /api/ndvi/sentinel/process`
- `POST /api/ndvi/planet/process`

Partes esperadas:

- `request`: JSON con `terrainId`, `sceneId`, `captureDate`, `epsg`, `ulx`, `uly`, `pixelSize`, `cloudCoverPercent` y `parcels`.
- `redBand`: archivo B04.
- `nirBand`: archivo B08.

Para Planet:

- `request`: JSON con `terrainId`, `sceneId`, `captureDate`, `assetType`, `numBands`, `cloudCoverPercent` y `parcels`.
- `image`: GeoTIFF Planet.

## Variables

- `SERVER_PORT`: puerto del servicio. Default `8082`.
- `SERVER_CONTEXT_PATH`: contexto HTTP. Default `/api`.
- `PROCESSING_GDAL_TRANSLATE_COMMAND`: ruta o comando de `gdal_translate`.
- `PROCESSING_GDAL_TEMP_DIR`: carpeta temporal de trabajo.

## Stack

- FastAPI
- rasterio
- shapely
- pyproj
- numpy
- GDAL CLI para conversion JP2 -> GeoTIFF

## Integración con backend

El backend usa `image.processing.base-url` y envía las bandas extraídas a este servicio.

Valor por defecto en backend:

- `http://localhost:8082/api`

## Docker Compose

`docker-compose.yml` expone este servicio en `http://localhost:8082/api`.

Construcción y arranque:

```bash
docker compose up --build procesamiento-imagen
```

Con toda la infraestructura local:

```bash
docker compose up --build
```