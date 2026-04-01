from app.tasks.celery_app import celery_app
from app.services.pipeline import process_answer_sheet_sync
from app.core.logger import get_logger
from app.services.idempotency_service import (
    is_task_processed,
    mark_task_processed, redis_client
)

logger = get_logger(__name__)

def acquire_lock(task_id: str) -> bool:
    return redis_client.set(f"lock:{task_id}", "1", nx=True, ex=300)


def release_lock(task_id: str):
    redis_client.delete(f"lock:{task_id}")

@celery_app.task(
    bind=True,
    autoretry_for=(Exception,),
    retry_backoff=5,
    retry_kwargs={"max_retries": 3}
)
def process_answer_sheet_task(self, request_data: dict):

    task_id = request_data.get("task_id")

    #  LOCK FIRST
    lock_acquired = acquire_lock(task_id)

    if not lock_acquired:
        logger.warning(f"[LOCK] Task {task_id} already in progress — skipping")
        return {"status": "skipped_in_progress"}

    try:
        #  IDEMPOTENCY AFTER LOCK
        if is_task_processed(task_id):
            logger.warning(f"[IDEMPOTENT] Task {task_id} already processed — skipping")
            return {"status": "already_processed"}

        logger.info(f"[CELERY] Processing task {task_id}")

        result = process_answer_sheet_sync(request_data)

        mark_task_processed(task_id)

        logger.info(f"[CELERY] Completed task {task_id}")

        return result

    except Exception as e:
        logger.error(f"[CELERY] Failed task {task_id}: {e}")

        if self.request.retries >= self.max_retries:
            logger.error(f"[FINAL FAILURE] Task {task_id} permanently failed")

        raise self.retry(exc=e)

    finally:
        if lock_acquired:
            release_lock(task_id)