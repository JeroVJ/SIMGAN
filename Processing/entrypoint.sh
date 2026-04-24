#!/bin/sh
# Entrypoint that selects API or Worker mode based on SERVICE_MODE env var.
# Set SERVICE_MODE=worker to start a Celery worker.
# Any other value (or unset) starts the Uvicorn API server.

set -e

if [ "$SERVICE_MODE" = "worker" ]; then
    echo "[entrypoint] Iniciando Celery worker (concurrencia=${WORKER_CONCURRENCY:-4})..."
    exec celery -A app.Config.celery_app worker \
        --loglevel="${PROCESSING_LOG_LEVEL:-info}" \
        --concurrency="${WORKER_CONCURRENCY:-4}" \
        --events \
        -Q ndvi
else
    echo "[entrypoint] Iniciando Uvicorn API server en puerto ${SERVER_PORT:-8082}..."
    exec uvicorn app.main:app \
        --host 0.0.0.0 \
        --port "${SERVER_PORT:-8082}" \
        --workers 2 \
        --timeout-keep-alive 120 \
        --limit-max-requests 1000
fi
