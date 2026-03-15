from fastapi import APIRouter, BackgroundTasks
from app.schemas.ocr_schema import OcrRequest
from app.tasks.ocr_tasks import process_answer_sheet_sync
from app.core.logger import get_logger

logger = get_logger(__name__)
router = APIRouter()


@router.post("/extract")
async def extract_answers(request: OcrRequest, background_tasks: BackgroundTasks):
    """
    Accepts OCR request and processes in background.
    Uses FastAPI BackgroundTasks instead of Celery — avoids Windows multiprocessing issues.
    Returns immediately with task_id.
    """
    logger.info(
        f"Received OCR request | task_id: {request.task_id} | "
        f"student: {request.student.student_id} | "
        f"pages: {len(request.cleaned_image_paths)}"
    )

    # Run in background — returns immediately to Java
    background_tasks.add_task(process_answer_sheet_sync, request.model_dump())

    return {
        "message": "OCR task queued successfully",
        "task_id": request.task_id,
        "celery_task_id": request.task_id,  # use task_id as reference
        "status": "QUEUED"
    }