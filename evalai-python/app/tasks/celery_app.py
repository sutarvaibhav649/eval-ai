from celery import Celery
from app.core.config import get_settings

settings = get_settings()

# Initialize Celery with Redis as both broker and result backend
celery_app = Celery(
    "evalai",
    broker=settings.redis_url,
    backend=settings.redis_url,
    include=["app.tasks.ocr_tasks"]
)

celery_app.autodiscover_tasks(["app"])

celery_app.conf.update(
    # Serialize tasks as JSON
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],

    # Timezone
    timezone="Asia/Kolkata",
    enable_utc=True,

    # Task settings
    task_track_started=True,
    task_acks_late=True,          # acknowledge after task completes, not before
    worker_prefetch_multiplier=1, # process one task at a time per worker

    # Result expiry — keep results for 24 hours
    result_expires=86400,

    # Windows fix — use solo pool
    worker_pool="solo",
)