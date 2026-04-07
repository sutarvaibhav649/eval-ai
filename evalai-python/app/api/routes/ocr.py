from fastapi import APIRouter
from app.schemas.ocr_schema import OcrRequest
from app.tasks.tasks import process_answer_sheet_task
from app.core.logger import get_logger

logger = get_logger(__name__)
router = APIRouter()


@router.post("/extract")
async def extract_answers(request: OcrRequest):
    """
    Accept OCR request → push to Celery queue
    """

    logger.info(
        f"Received OCR request | task_id: {request.task_id} | "
        f"student: {request.student.student_id} | "
        f"pages: {len(request.raw_image_paths)}"
    )

    # 🔥 Push to Celery
    task = process_answer_sheet_task.delay(request.model_dump())

    return {
        "message": "OCR task queued successfully",
        "task_id": request.task_id,
        "celery_task_id": task.id,  # real celery id now
        "status": "QUEUED"
    }