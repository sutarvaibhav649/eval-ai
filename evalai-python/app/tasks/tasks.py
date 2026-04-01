from app.tasks.celery_app import celery_app
from app.services.pipeline import process_answer_sheet_sync
from app.core.logger import get_logger

logger = get_logger(__name__)


@celery_app.task(
    bind=True,
    autoretry_for=(Exception,),
    retry_backoff=5,
    retry_kwargs={"max_retries": 3}
)
def process_answer_sheet_task(self, request_data: dict):

    task_id = request_data.get("task_id")

    try:
        logger.info(f"[CELERY] Processing task {task_id}")

        result = process_answer_sheet_sync(request_data)

        logger.info(f"[CELERY] Completed task {task_id}")

        return result

    except Exception as e:
        logger.error(f"[CELERY] Failed task {task_id}: {e}")
        raise self.retry(exc=e)