from celery import Celery

from app.Config import settings


celery_app = Celery(
    "processing",
    broker=settings.redis_url,
    backend=settings.redis_url,
    # Explicitly import the tasks module so the worker registers all tasks
    # when it starts. Without this, [tasks] is empty and no job is ever consumed.
    include=["app.workers.tasks"],
)

celery_app.conf.update(
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    task_track_started=True,
    result_expires=3600,
    # Process one task at a time per worker slot — prevents memory spikes from
    # prefetching multiple heavy satellite jobs simultaneously.
    worker_prefetch_multiplier=1,
    # Only acknowledge (remove from queue) after the task finishes, so a crashed
    # worker doesn't silently drop a job.
    task_acks_late=True,
    # Retry broker connection on startup (suppresses Celery 6.0 deprecation warning).
    broker_connection_retry_on_startup=True,
)