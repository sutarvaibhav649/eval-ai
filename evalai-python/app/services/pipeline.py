from app.services.ocr_service import extract_text_from_images
from app.services.segmentation_service import segment_text_by_labels, is_text_sufficient
from app.services.embedding_service import generate_embedding
from app.services.scoring_service import cosine_similarity, calculate_marks
from app.services.feedback_service import generate_feedback

from app.core.logger import get_logger
import httpx

logger = get_logger(__name__)


def process_answer_sheet_sync(request_data: dict):

    task_id = request_data["task_id"]
    image_paths = request_data["cleaned_image_paths"]
    questions = request_data["questions"]
    callback_url = request_data.get("callback_url")

    logger.info(f"[PIPELINE] Start task {task_id}")

    # STEP 1 — OCR
    full_text, ocr_confidence = extract_text_from_images(image_paths)

    logger.info(f"[OCR] Completed for task {task_id}")

    # STEP 2 — Prepare labels
    expected_labels = [
        q["sub_question_label"].lower() for q in questions
    ]

    # STEP 3 — Segmentation
    segments = segment_text_by_labels(full_text, expected_labels)

    logger.info(f"[SEGMENTATION] Found {len(segments)} segments")

    results = []

    # STEP 4 — Per Question Evaluation
    for q in questions:
        label = q["sub_question_label"].lower()
        student_answer = segments.get(label, "")

        max_marks = q["marks"]
        model_embedding = q["model_answer_embedding"]
        model_answer = q["model_answer_text"]
        question_text = q["question_text"]
        key_concepts = q.get("key_concepts", [])

        #  Handle missing / weak answers
        if not is_text_sufficient(student_answer):
            logger.warning(f"[EMPTY] {label} has insufficient text")

            results.append({
                "sub_question_id": q["sub_question_id"],
                "extracted_text": student_answer,
                "similarity_score": 0.0,
                "ai_marks": 0.0,
                "confidence": 0.0,
                "feedback": {
                    "strengths": "",
                    "weakness": "Answer is too short or missing",
                    "suggestions": "Provide a complete answer",
                    "overall_feedback": "Insufficient answer",
                    "key_concepts_missed": key_concepts
                }
            })
            continue

        #  STEP 5 — Embedding
        student_embedding = generate_embedding(student_answer)

        #  STEP 6 — Similarity
        similarity = cosine_similarity(model_embedding, student_embedding)

        #  STEP 7 — Marks
        marks = calculate_marks(similarity, max_marks)

        #  STEP 8 — Feedback
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
            "extracted_text": student_answer,
            "similarity_score": similarity,
            "ai_marks": marks,
            "confidence": ocr_confidence,
            "feedback": feedback
        })

    # STEP 9 — Final payload
    payload = {
        "task_id": task_id,
        "results": results
    }

    #  STEP 10 — Callback to Java
    if callback_url:
        try:
            with httpx.Client(timeout=10.0) as client:
                client.post(callback_url, json=payload)

            logger.info(f"[CALLBACK] Success for task {task_id}")

        except Exception as e:
            logger.error(f"[CALLBACK] Failed for {task_id}: {e}")

    logger.info(f"[PIPELINE] Completed task {task_id}")

    return payload