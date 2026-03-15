import os
from typing import List, Tuple
from app.core.logger import get_logger

# Disable OneDNN which causes crashes on Windows
os.environ["PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK"] = "True"
os.environ["FLAGS_use_mkldnn"] = "0"        # disable OneDNN/MKL-DNN
os.environ["FLAGS_onednn_cpu_detection"] = "0"

logger = get_logger(__name__)

_paddle_ocr = None


def get_paddle_ocr():
    global _paddle_ocr
    if _paddle_ocr is None:
        logger.info("Initializing PaddleOCR...")
        from paddleocr import PaddleOCR
        _paddle_ocr = PaddleOCR(
            lang='en',
            use_gpu=False,      
            enable_mkldnn=False 
        )
    return _paddle_ocr


def extract_text_from_image(image_path: str) -> Tuple[str, float]:
    if not os.path.exists(image_path):
        raise FileNotFoundError(f"Image not found: {image_path}")

    ocr_engine = get_paddle_ocr()

    try:
        result = ocr_engine.ocr(image_path, cls=True)
    except TypeError:
        # PaddleOCR 3.x removed cls parameter
        result = ocr_engine.ocr(image_path)
    except Exception as e:
        return "", 0.0

    if not result or result[0] is None:
        return "", 0.0

    texts = []
    confidences = []

    for page in result:
        if not page:
            continue
        for line in page:
            try:
                text = line[1][0]
                confidence = line[1][1]
                if str(text).strip():
                    texts.append(str(text).strip())
                    confidences.append(float(confidence))
            except (IndexError, TypeError) as e:
                logger.error(f"Error parsing line: {e}")

    full_text = " ".join(texts)
    avg_confidence = (
        sum(confidences) / len(confidences) if confidences else 0.0
    )
    return full_text, avg_confidence


def extract_text_from_images(image_paths: List[str]) -> Tuple[str, float]:
    all_texts = []
    all_confidences = []

    for image_path in sorted(image_paths):
        text, confidence = extract_text_from_image(image_path)
        if text:
            all_texts.append(text)
            all_confidences.append(confidence)

    full_text = "\n".join(all_texts)
    avg_confidence = (
        sum(all_confidences) / len(all_confidences)
        if all_confidences else 0.0
    )
    return full_text, avg_confidence