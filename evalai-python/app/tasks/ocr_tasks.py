import httpx
from app.tasks.celery_app import celery_app
from app.schemas.ocr_schema import OcrRequest, OcrResponse, ExtractedAnswer
from app.services.ocr_service import extract_text_from_images
from app.services.embedding_service import generate_embedding
from app.services.segmentation_service import segment_text_by_labels, is_text_sufficient
from app.services.scoring_service import cosine_similarity, calculate_marks
from app.core.config import get_settings
from app.core.logger import get_logger
from typing import List
import os

logger = get_logger(__name__)
settings = get_settings()

# OCR confidence threshold — below this score is considered a failure
OCR_CONFIDENCE_THRESHOLD = 0.5


@celery_app.task(bind=True, name="ocr_tasks.process_answer_sheet")
def process_answer_sheet(self, task_data: dict):
    """
    Main Celery task — processes one student's answer sheet end to end.

    Flow:
    1. Extract text from all cleaned page images via OCR
    2. Segment text by sub-question labels
    3. Generate embedding for each extracted answer
    4. Calculate cosine similarity vs model answer embedding
    5. Calculate ai_marks from similarity score
    6. POST results back to Java via callback

    Args:
        task_data: dict matching OcrRequest schema

    This task is triggered by Java via POST /ocr/extract.
    Results are sent back to Java via POST /pipeline/callback.
    """
    request = OcrRequest(**task_data)
    logger.info(
        f"Starting OCR task {request.task_id} for "
        f"student {request.student.student_id}"
    )

    # Step 1 — Extract all expected labels from request
    expected_labels = [q.sub_question_label for q in request.questions]

    # Step 2 — Run OCR on all cleaned images
    try:
        full_text, avg_confidence = extract_text_from_images(
            request.cleaned_image_paths
        )
        total_pages = len(request.cleaned_image_paths)
        ocr_engine = "PADDLEOCR"
    except Exception as e:
        logger.error(f"OCR failed entirely for task {request.task_id}: {e}")
        # Send total failure callback to Java
        _send_failure_callback(request, str(e))
        return

    # Step 3 — Segment text by sub-question labels
    segments = segment_text_by_labels(full_text, expected_labels)

    # Step 4 — Process each sub-question
    extracted_answers: List[ExtractedAnswer] = []
    failed_count = 0
    completed_count = 0

    for question in request.questions:
        label = question.sub_question_label
        segment_text = segments.get(label)

        # Check for segmentation failure
        if not segment_text:
            logger.warning(f"Label '{label}' not found in OCR text")
            extracted_answers.append(ExtractedAnswer(
                sub_question_id=question.sub_question_id,
                sub_question_label=label,
                question_number=question.question_number,
                subject_code=request.context.subject_code,
                status="FAILED",
                failure_reason="LABEL_NOT_DETECTED",
                extracted_text=None,
                ai_marks=0.0
            ))
            failed_count += 1
            continue

        # Check for low confidence
        if avg_confidence < OCR_CONFIDENCE_THRESHOLD:
            logger.warning(
                f"Low OCR confidence {avg_confidence:.3f} for label '{label}'"
            )
            extracted_answers.append(ExtractedAnswer(
                sub_question_id=question.sub_question_id,
                sub_question_label=label,
                question_number=question.question_number,
                subject_code=request.context.subject_code,
                status="FAILED",
                failure_reason="OCR_LOW_CONFIDENCE",
                extracted_text=segment_text,
                ocr_confidence=avg_confidence,
                ai_marks=0.0
            ))
            failed_count += 1
            continue

        # Check for text too short
        if not is_text_sufficient(segment_text):
            logger.warning(f"Text too short for label '{label}': '{segment_text}'")
            extracted_answers.append(ExtractedAnswer(
                sub_question_id=question.sub_question_id,
                sub_question_label=label,
                question_number=question.question_number,
                subject_code=request.context.subject_code,
                status="FAILED",
                failure_reason="TEXT_TOO_SHORT",
                extracted_text=segment_text,
                ocr_confidence=avg_confidence,
                ai_marks=0.0
            ))
            failed_count += 1
            continue

        # Step 5 — Generate embedding for student answer
        try:
            student_embedding = generate_embedding(segment_text)
        except Exception as e:
            logger.error(f"Embedding generation failed for label '{label}': {e}")
            extracted_answers.append(ExtractedAnswer(
                sub_question_id=question.sub_question_id,
                sub_question_label=label,
                question_number=question.question_number,
                subject_code=request.context.subject_code,
                status="FAILED",
                failure_reason="LABEL_NOT_DETECTED",
                extracted_text=segment_text,
                ai_marks=0.0
            ))
            failed_count += 1
            continue

        # Step 6 — Calculate similarity vs model answer
        similarity = cosine_similarity(
            question.model_answer_embedding,
            student_embedding
        )

        # Step 7 — Calculate marks
        ai_marks = calculate_marks(similarity, question.marks)

        logger.info(
            f"Label '{label}' | similarity: {similarity:.3f} | "
            f"ai_marks: {ai_marks}/{question.marks}"
        )

        extracted_answers.append(ExtractedAnswer(
            sub_question_id=question.sub_question_id,
            sub_question_label=label,
            question_number=question.question_number,
            subject_code=request.context.subject_code,
            status="COMPLETED",
            extracted_text=segment_text,
            cleaned_text=segment_text,
            embedding=student_embedding,
            ocr_confidence=avg_confidence,
            similarity_score=similarity,
            ai_marks=ai_marks
        ))
        completed_count += 1

    # Step 8 — Determine overall status
    if failed_count == 0:
        overall_status = "COMPLETED"
    elif completed_count > 0:
        overall_status = "COMPLETED_WITH_FAILURES"
    else:
        overall_status = "FAILED"

    # Step 9 — Build response and send callback to Java
    response = OcrResponse(
        task_id=request.task_id,
        context=request.context,
        student=request.student,
        status=overall_status,
        ocr_engine_used=ocr_engine,
        total_pages_processed=total_pages,
        failed_count=failed_count,
        completed_count=completed_count,
        extracted_answers=extracted_answers
    )

    _send_callback_to_java(response)
    logger.info(
        f"Task {request.task_id} completed | "
        f"status: {overall_status} | "
        f"completed: {completed_count} | failed: {failed_count}"
    )


def _send_callback_to_java(response: OcrResponse):
    """
    Sends OCR results back to Java via HTTP POST.
    Java's pipeline endpoint receives this and saves results to DB.
    """
    callback_url = f"{settings.java_base_url}/pipeline/callback"
    try:
        with httpx.Client(timeout=30.0) as client:
            resp = client.post(
                callback_url,
                json=response.model_dump(),
                headers={"Content-Type": "application/json"}
            )
            logger.info(f"Callback sent to Java | status: {resp.status_code}")
    except Exception as e:
        logger.error(f"Failed to send callback to Java: {e}")


def _send_failure_callback(request: OcrRequest, error_message: str):
    """
    Sends a total failure callback to Java when OCR cannot run at all.
    """
    failed_answers = [
        ExtractedAnswer(
            sub_question_id=q.sub_question_id,
            sub_question_label=q.sub_question_label,
            question_number=q.question_number,
            subject_code=request.context.subject_code,
            status="FAILED",
            failure_reason="LABEL_NOT_DETECTED",
            ai_marks=0.0
        )
        for q in request.questions
    ]

    response = OcrResponse(
        task_id=request.task_id,
        context=request.context,
        student=request.student,
        status="FAILED",
        ocr_engine_used="PADDLEOCR",
        total_pages_processed=0,
        failed_count=len(request.questions),
        completed_count=0,
        extracted_answers=failed_answers
    )
    _send_callback_to_java(response)