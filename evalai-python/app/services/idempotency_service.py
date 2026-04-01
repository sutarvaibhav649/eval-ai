import redis
from app.core.config import get_settings
from app.core.logger import get_logger

logger = get_logger(__name__)
settings = get_settings()

redis_client = redis.Redis.from_url(settings.redis_url)


def is_task_processed(task_id: str) -> bool:
    return redis_client.exists(f"task:{task_id}")


def mark_task_processed(task_id: str):
    redis_client.set(f"task:{task_id}", "done", ex=86400)  # TTL = 1 day