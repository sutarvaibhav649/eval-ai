import os
from pathlib import Path
from typing import List, Tuple, Optional
from app.core.config import get_settings
from app.core.logger import get_logger

logger = get_logger(__name__)
settings = get_settings()

# Lazy-load PaddleOCR — heavy import
_paddle_ocr = None


def get_paddle_ocr():
    """
    Lazy-loads PaddleOCR on first call.
    lang='en' for English text recognition.
    use_angle_cls=True handles rotated text.
    """
    global _paddle_ocr
    if _paddle_ocr is None:
        logger.info("Loading PaddleOCR model...")
        from paddleocr import PaddleOCR
        _paddle_ocr = PaddleOCR(use_angle_cls=True, lang='en', show_log=False)
        logger.info("PaddleOCR loaded successfully")
    return _paddle_ocr


def extract_text_from_image(image_path: str) -> Tuple[str, float]:
    """
    Extracts text from a single image using PaddleOCR.

    Args:
        image_path: path to the image file (PNG/JPG)

    Returns:
        tuple of (extracted_text, confidence_score)
        confidence_score is average confidence across all detected text blocks
    """
    if not os.path.exists(image_path):
        raise FileNotFoundError(f"Image not found: {image_path}")

    ocr = get_paddle_ocr()
    result = ocr.ocr(image_path, cls=True)

    if not result or not result[0]:
        logger.warning(f"No text detected in image: {image_path}")
        return "", 0.0

    # Extract text and confidence from each detected block
    texts = []
    confidences = []

    for line in result[0]:
        if line and len(line) >= 2:
            text = line[1][0]       # extracted text
            confidence = line[1][1]  # confidence score

            if text.strip():
                texts.append(text.strip())
                confidences.append(confidence)

    full_text = " ".join(texts)
    avg_confidence = sum(confidences) / len(confidences) if confidences else 0.0

    logger.debug(
        f"Extracted {len(texts)} text blocks from {image_path} "
        f"with avg confidence {avg_confidence:.3f}"
    )

    return full_text, avg_confidence


def extract_text_from_images(image_paths: List[str]) -> Tuple[str, float]:
    """
    Extracts and concatenates text from multiple page images.
    Pages are processed in order and text is joined with newlines.

    Args:
        image_paths: ordered list of image paths (page_1.png, page_2.png...)

    Returns:
        tuple of (full_text_all_pages, average_confidence_across_pages)
    """
    all_texts = []
    all_confidences = []

    for image_path in sorted(image_paths):
        try:
            text, confidence = extract_text_from_image(image_path)
            if text:
                all_texts.append(text)
                all_confidences.append(confidence)
            logger.info(f"Processed page: {image_path} | confidence: {confidence:.3f}")
        except Exception as e:
            logger.error(f"Failed to process image {image_path}: {e}")

    full_text = "\n".join(all_texts)
    avg_confidence = (
        sum(all_confidences) / len(all_confidences)
        if all_confidences else 0.0
    )

    return full_text, avg_confidence