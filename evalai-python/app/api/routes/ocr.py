from fastapi import APIRouter
from app.schemas.ocr_schema import OcrRequest
from app.tasks.ocr_tasks import process_answer_sheet
from app.core.logger import get_logger

logger = get_logger(__name__)
router = APIRouter()


@router.post("/extract")
async def extract_answers(request: OcrRequest):
    """
    Accepts an OCR extraction request from Java and queues it as a Celery task.

    Java sends this after C++ preprocessing is complete.
    Returns immediately with task_id — processing happens async in Celery worker.
    Java receives results via callback to POST /pipeline/callback.
    """
    logger.info(
        f"Received OCR request | task_id: {request.task_id} | "
        f"student: {request.student.student_id} | "
        f"pages: {len(request.cleaned_image_paths)}"
    )

    # Queue the task — returns immediately
    task = process_answer_sheet.delay(request.model_dump())

    return {
        "message": "OCR task queued successfully",
        "task_id": request.task_id,
        "celery_task_id": task.id,
        "status": "QUEUED"
    }