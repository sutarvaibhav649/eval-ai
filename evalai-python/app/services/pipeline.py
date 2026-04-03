from app.services.ocr_service import extract_text_from_images
from app.services.segmentation_service import segment_text_by_labels, is_text_sufficient
from app.services.embedding_service import generate_embedding
from app.services.scoring_service import cosine_similarity, calculate_marks
from app.services.feedback_service import generate_feedback
from app.core.logger import get_logger
import httpx
import json
import os

logger = get_logger(__name__)

OCR_MODE = os.getenv("OCR_MODE", "openrouter")


def process_answer_sheet_sync(request_data: dict):

    task_id = request_data["task_id"]
    image_paths = request_data["cleaned_image_paths"]
    questions = request_data["questions"]
    callback_url = request_data.get("callback_url")
    context = request_data.get("context", {})
    student = request_data.get("student", {})

    logger.info(f"[PIPELINE] Start task {task_id}")
    logger.info(f"[CALLBACK URL] {callback_url}")

    # ✅ Build once before the loop
    context_payload = {
        "exam_id": context.get("exam_id"),
        "exam_name": context.get("exam_name"),
        "course_id": context.get("course_id"),
        "course_name": context.get("course_name"),
        "subject_code": context.get("subject_code"),
        "subject_name": context.get("subject_name"),
        "academic_year": context.get("academic_year"),
        "question_paper_id": context.get("question_paper_id"),
        "question_paper_set": context.get("set_label")
    }

    student_payload = {
        "student_id": student.get("student_id"),
        "answer_sheet_id": student.get("answer_sheet_id")  # ✅ fixed key
    }

    # STEP 1 — OCR
    try:
        full_text, ocr_confidence = extract_text_from_images(image_paths)
        logger.info(f"[OCR] Completed | confidence={ocr_confidence}")
    except Exception as e:
        logger.error(f"[OCR ERROR] {e}")
        return

    # STEP 2 — Prepare labels
    expected_labels = [q["sub_question_label"].lower() for q in questions]

    # STEP 3 — Segmentation
    segments = segment_text_by_labels(full_text, expected_labels)
    logger.info(f"[SEGMENTATION] Found {len(segments)} segments")

    results = []

    # STEP 4 — Per question evaluation
    for q in questions:
        label = q["sub_question_label"].lower()
        student_answer = segments.get(label, "")

        max_marks = q["marks"]
        model_embedding = q["model_answer_embedding"]
        model_answer = q["model_answer_text"]
        question_text = q["question_text"]
        key_concepts = q.get("key_concepts", [])
        subject_code = context_payload["subject_code"] or ""

        # FAILED path — insufficient answer
        if not is_text_sufficient(student_answer):
            logger.warning(f"[EMPTY] {label} insufficient text")
            results.append({
                "sub_question_id": q["sub_question_id"],
                "sub_question_label": q["sub_question_label"],  # ✅
                "question_number": q.get("question_number", 0), # ✅
                "subject_code": subject_code,                   # ✅
                "status": "FAILED",
                "failure_reason": "TEXT_TOO_SHORT",
                "extracted_text": student_answer,
                "cleaned_text": student_answer,
                "embedding": None,
                "ocr_confidence": ocr_confidence,
                "similarity_score": 0.0,
                "ai_marks": 0.0,
                "feedback": {
                    "strengths": "",
                    "weakness": "Answer is too short or missing",
                    "suggestions": "Provide a complete answer",
                    "overall_feedback": "Insufficient answer",
                    "key_concepts_missed": key_concepts
                }
            })
            continue

        try:
            # STEP 5 — Embedding
            student_embedding = generate_embedding(student_answer)
            if hasattr(student_embedding, 'tolist'):
                student_embedding = student_embedding.tolist()

            # STEP 6 — Similarity
            similarity = cosine_similarity(model_embedding, student_embedding)

            # STEP 7 — Marks
            marks = calculate_marks(similarity, max_marks)

            # STEP 8 — Feedback
            feedback = generate_feedback(
                question_text=question_text,
                student_answer=student_answer,
                model_answer=model_answer,
                key_concepts=key_concepts,
                ai_marks=marks,
                max_marks=max_marks,
                sub_question_label=label
            )

            results.append({
                "sub_question_id": q["sub_question_id"],
                "sub_question_label": q["sub_question_label"],  # ✅
                "question_number": q.get("question_number", 0), # ✅
                "subject_code": subject_code,                   # ✅
                "status": "COMPLETED",
                "failure_reason": None,
                "extracted_text": student_answer,
                "cleaned_text": student_answer,
                "embedding": student_embedding,
                "ocr_confidence": ocr_confidence,
                "similarity_score": similarity,
                "ai_marks": marks,
                "feedback": feedback
            })

        except Exception as e:
            logger.error(f"[PROCESS ERROR] {label}: {e}")
            results.append({
                "sub_question_id": q["sub_question_id"],
                "sub_question_label": q["sub_question_label"],
                "question_number": q.get("question_number", 0),
                "subject_code": subject_code,
                "status": "FAILED",
                "failure_reason": "PROCESSING_ERROR",
                "extracted_text": student_answer,
                "cleaned_text": None,
                "embedding": None,
                "ocr_confidence": ocr_confidence,
                "similarity_score": 0.0,
                "ai_marks": 0.0,
                "feedback": None
            })

    # STEP 9 — Overall status
    failed = sum(1 for r in results if r["status"] == "FAILED")
    completed = len(results) - failed

    if failed == 0:
        overall_status = "COMPLETED"
    elif completed > 0:
        overall_status = "COMPLETED_WITH_FAILURES"
    else:
        overall_status = "FAILED"

    # STEP 10 — Final payload
    payload = {
        "task_id": task_id,
        "context": context_payload,
        "student": student_payload,
        "status": overall_status,
        "ocr_engine_used": OCR_MODE,
        "total_pages_processed": len(image_paths),
        "failed_count": failed,
        "completed_count": completed,
        "extracted_answers": results
    }

    logger.info(f"[PAYLOAD READY] status={overall_status} | completed={completed} | failed={failed}")

    # STEP 11 — Callback to Java
    if callback_url:
        try:
            with httpx.Client(timeout=30.0) as client:
                for attempt in range(3):
                    try:
                        res = client.post(callback_url, json=payload)
                        logger.info(f"[CALLBACK] Attempt {attempt+1} | status={res.status_code}")
                        if res.status_code == 200:
                            logger.info(f"[CALLBACK SUCCESS] task {task_id}")
                            break
                        else:
                            logger.error(f"[CALLBACK ERROR] {res.status_code} | {res.text}")
                    except Exception as retry_error:
                        logger.error(f"[RETRY {attempt+1}] {retry_error}")
        except Exception as e:
            logger.error(f"[CALLBACK FAILED] {task_id}: {e}")
    else:
        logger.error("[NO CALLBACK URL] Cannot send result to Java")

    logger.info(f"[PIPELINE DONE] {task_id}")
    return payload