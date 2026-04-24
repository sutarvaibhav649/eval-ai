import json
import re

import cv2
from fastapi import APIRouter, HTTPException
from app.schemas.ocr_schema import OcrRequest, QrScanRequest, QrScanResponse
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


def _extract_student_id_from_payload(payload: str) -> str | None:
    if not payload:
        return None

    cleaned = payload.strip()

    try:
        parsed = json.loads(cleaned)
        if isinstance(parsed, dict):
            for key in ("student_id", "studentId", "id"):
                value = parsed.get(key)
                if value is not None and str(value).strip():
                    return str(value).strip()
    except Exception:
        pass

    kv_match = re.search(r"(?:student[_-]?id)\s*[:=]\s*([A-Za-z0-9_-]+)", cleaned, re.IGNORECASE)
    if kv_match:
        return kv_match.group(1).strip()

    # fallback: payload itself is student id
    return cleaned


@router.post("/scan-qr", response_model=QrScanResponse)
async def scan_qr(request: QrScanRequest):
    detector = cv2.QRCodeDetector()

    for path in request.raw_image_paths:
        image = cv2.imread(path)
        if image is None:
            continue

        decoded_text, _, _ = detector.detectAndDecode(image)
        if decoded_text:
            student_id = _extract_student_id_from_payload(decoded_text)
            if student_id:
                logger.info("QR decoded successfully for student_id=%s from %s", student_id, path)
                return QrScanResponse(student_id=student_id, qr_payload=decoded_text)

    raise HTTPException(status_code=400, detail="No valid QR code with student id found")
