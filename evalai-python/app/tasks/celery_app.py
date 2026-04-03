from celery import Celery
from app.core.config import get_settings

settings = get_settings()

celery_app = Celery(
    "evalai",
    broker=settings.redis_url,
    backend=settings.redis_url,
    include=["app.tasks.tasks"],  # this is enough, remove autodiscover
)

celery_app.conf.update(
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    timezone="Asia/Kolkata",
    enable_utc=True,
    task_track_started=True,
    task_acks_late=True,
    worker_prefetch_multiplier=1,
    result_expires=86400,
    worker_pool="solo",
)