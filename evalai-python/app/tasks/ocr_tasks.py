import httpx
from app.schemas.ocr_schema import OcrRequest, OcrResponse, ExtractedAnswer
from app.services.embedding_service import generate_embedding
from app.services.segmentation_service import segment_text_by_labels, is_text_sufficient
from app.services.scoring_service import cosine_similarity, calculate_marks
from app.core.config import get_settings
from app.core.logger import get_logger
from typing import List

logger = get_logger(__name__)
settings = get_settings()

OCR_CONFIDENCE_THRESHOLD = 0.5


def process_answer_sheet_sync(task_data: dict):
    from app.core.config import get_settings
    settings = get_settings()

    # Select OCR engine based on OCR_MODE
    if settings.ocr_mode == "OPENROUTER":
        from app.services.openrouter_ocr_service import extract_text_from_images_openrouter
        ocr_function = extract_text_from_images_openrouter
        ocr_engine_name = f"OPENROUTER:{settings.openrouter_ocr_model}"
    elif settings.ocr_mode == "GEMINI":
        from app.services.openrouter_ocr_service import extract_text_from_images_gemini
        ocr_function = extract_text_from_images_gemini
        ocr_engine_name = "GEMINI"
    else:
        from app.services.ocr_service import extract_text_from_images
        ocr_function = extract_text_from_images
        ocr_engine_name = "PADDLEOCR"


    request = OcrRequest(**task_data)

    expected_labels = [q.sub_question_label for q in request.questions]

    # Step 1 — Run OCR
    try:
        full_text, avg_confidence = ocr_function(request.cleaned_image_paths)
        total_pages = len(request.cleaned_image_paths)
    except Exception as e:
        _send_failure_callback(request, str(e))
        return

    # Step 2 — Segment text by sub-question labels
    segments = segment_text_by_labels(full_text, expected_labels)

    # Step 3 — Process each sub-question
    extracted_answers: List[ExtractedAnswer] = []
    failed_count = 0
    completed_count = 0

    for question in request.questions:
        label = question.sub_question_label
        segment_text = segments.get(label)

        # Check segmentation failure
        if not segment_text:
            extracted_answers.append(ExtractedAnswer(
                sub_question_id=question.sub_question_id,
                sub_question_label=label,
                question_number=question.question_number,
                subject_code=request.context.subject_code,
                status="FAILED",
                failure_reason="LABEL_NOT_DETECTED",
                ai_marks=0.0
            ))
            failed_count += 1
            continue

        # Check OCR confidence
        if avg_confidence < OCR_CONFIDENCE_THRESHOLD:
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

        # Check text length
        if not is_text_sufficient(segment_text):
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

        # Step 4 — Generate embedding
        try:
            student_embedding = generate_embedding(segment_text)
        except Exception as e:
            extracted_answers.append(ExtractedAnswer(
                sub_question_id=question.sub_question_id,
                sub_question_label=label,
                question_number=question.question_number,
                subject_code=request.context.subject_code,
                status="FAILED",
                failure_reason="LABEL_NOT_DETECTED",
                ai_marks=0.0
            ))
            failed_count += 1
            continue

        # Step 5 — Calculate similarity and marks
        similarity = cosine_similarity(
            question.model_answer_embedding,
            student_embedding
        )
        ai_marks = calculate_marks(similarity, question.marks)

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

    # Step 6 — Determine overall status
    if failed_count == 0:
        overall_status = "COMPLETED"
    elif completed_count > 0:
        overall_status = "COMPLETED_WITH_FAILURES"
    else:
        overall_status = "FAILED"

    # Step 7 — Build response and send callback
    response = OcrResponse(
        task_id=request.task_id,
        context=request.context,
        student=request.student,
        status=overall_status,
        ocr_engine_used=ocr_engine_name,
        total_pages_processed=total_pages,
        failed_count=failed_count,
        completed_count=completed_count,
        extracted_answers=extracted_answers
    )

    _send_callback_to_java(response)


def _send_callback_to_java(response: OcrResponse):
    callback_url = f"{settings.java_base_url}/pipeline/callback"
    try:
        with httpx.Client(timeout=30.0) as client:
            resp = client.post(
                callback_url,
                json=response.model_dump(),
                headers={"Content-Type": "application/json"}
            )
    except Exception as e:
        logger.error(f"Failed to send callback: {e}")


def _send_failure_callback(request: OcrRequest, error_message: str):
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