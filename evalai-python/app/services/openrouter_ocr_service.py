import os
import base64
from typing import List, Tuple
from app.core.config import get_settings
from app.core.logger import get_logger

logger = get_logger(__name__)
settings = get_settings()


def encode_image_to_base64(image_path: str) -> str:
    """Encodes image file to base64 string."""
    with open(image_path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")


def extract_text_from_image_openrouter(image_path: str) -> Tuple[str, float]:
    """
    Extracts text from a single image using OpenRouter vision model.
    Returns (extracted_text, confidence).
    Confidence is fixed at 0.95 — vision models don't provide token confidence.
    """
    from openai import OpenAI

    # Normalize Windows backslashes
    image_path = os.path.normpath(image_path)

    if not os.path.exists(image_path):
        raise FileNotFoundError(f"Image not found: {image_path}")

    client = OpenAI(
        base_url="https://openrouter.ai/api/v1",
        api_key=settings.openrouter_api_key
    )

    image_data = encode_image_to_base64(image_path)
    mime_type = "image/png" if image_path.endswith(".png") else "image/jpeg"

    response = client.chat.completions.create(
        model=settings.openrouter_ocr_model,
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:{mime_type};base64,{image_data}"
                        }
                    },
                    {
                        "type": "text",
                        "text": """Extract all handwritten and printed text from this exam answer sheet image exactly as written.
                                Preserve the structure including question labels like Q.1, Q.2, 1a, 1b etc.
                                Return only the extracted text with no commentary, explanation, or markdown formatting.
                                Maintain original line breaks and paragraph structure."""
                    }
                ]
            }
        ]
    )

    extracted_text = response.choices[0].message.content.strip()

    return extracted_text, 0.95


def extract_text_from_images_openrouter(image_paths: List[str]) -> Tuple[str, float]:
    """
    Extracts and concatenates text from multiple page images using OpenRouter.
    """
    all_texts = []
    all_confidences = []

    for image_path in sorted(image_paths):
        try:
            text, confidence = extract_text_from_image_openrouter(image_path)
            if text:
                all_texts.append(text)
                all_confidences.append(confidence)
        except Exception as e:
            logger.error(
                f"OpenRouter OCR failed for {os.path.basename(image_path)}: {e}"
            )

    full_text = "\n".join(all_texts)
    avg_confidence = (
        sum(all_confidences) / len(all_confidences)
        if all_confidences else 0.0
    )

    return full_text, avg_confidence