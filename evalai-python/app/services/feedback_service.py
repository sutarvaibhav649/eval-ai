import os
from typing import List, Optional
from app.core.config import get_settings
from app.core.logger import get_logger

logger = get_logger(__name__)
settings = get_settings()


def generate_feedback(
    question_text: str,
    student_answer: str,
    model_answer: str,
    key_concepts: List[str],
    ai_marks: float,
    max_marks: float,
    sub_question_label: str
) -> dict:
    """
    Generates qualitative feedback for a student's answer using OpenRouter LLM.

    Returns a dict with:
    - strengths: what the student did well
    - weakness: what was missing or incorrect
    - suggestions: how to improve
    - overall_feedback: summary
    - key_concepts_missed: list of key concepts not covered
    """
    from openai import OpenAI

    client = OpenAI(
        base_url="https://openrouter.ai/api/v1",
        api_key=settings.openrouter_api_key
    )

    prompt = f"""You are an expert academic evaluator. Analyze this student's exam answer and provide constructive feedback.

Question ({sub_question_label}): {question_text}

Student's Answer: {student_answer}

Model Answer: {model_answer}

Key Concepts Required: {', '.join(key_concepts)}

Marks Awarded: {ai_marks}/{max_marks}

Provide feedback in the following JSON format only, no other text:
{{
    "strengths": "What the student answered correctly and well",
    "weakness": "What was missing, incorrect, or incomplete",
    "suggestions": "Specific advice on how to improve the answer",
    "overall_feedback": "A brief 1-2 sentence overall assessment",
    "key_concepts_missed": ["concept1", "concept2"]
}}

Be specific, constructive, and educational. Keep each field concise."""

    try:
        response = client.chat.completions.create(
            model=settings.openrouter_ocr_model,
            messages=[
                {
                    "role": "user",
                    "content": prompt
                }
            ],
            response_format={"type": "json_object"}
        )

        import json
        feedback_text = response.choices[0].message.content.strip()
        feedback = json.loads(feedback_text)

        logger.info(
            f"Feedback generated for {sub_question_label} | "
            f"marks: {ai_marks}/{max_marks}"
        )

        return {
            "strengths": feedback.get("strengths", ""),
            "weakness": feedback.get("weakness", ""),
            "suggestions": feedback.get("suggestions", ""),
            "overall_feedback": feedback.get("overall_feedback", ""),
            "key_concepts_missed": feedback.get("key_concepts_missed", [])
        }

    except Exception as e:
        logger.error(f"Feedback generation failed for {sub_question_label}: {e}")
        return {
            "strengths": "",
            "weakness": "",
            "suggestions": "",
            "overall_feedback": "Feedback generation failed",
            "key_concepts_missed": []
        }