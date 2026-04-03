import os
import time
import base64
import httpx
from typing import List, Tuple
from app.core.logger import get_logger
from concurrent.futures import ThreadPoolExecutor, as_completed


logger = get_logger(__name__)

# ── Mode selection ────────────────────────────────────────────────────────────
OCR_MODE = os.getenv("OCR_MODE", "openrouter").lower()  # "openrouter" | "paddle"
OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY", "")
OPENROUTER_OCR_MODEL = os.getenv("OPENROUTER_OCR_MODEL", "meta-llama/llama-4-scout:free")

# ── PaddleOCR singleton (only initialized if OCR_MODE=paddle) ─────────────────
_paddle_ocr = None


def get_paddle_ocr():
    global _paddle_ocr
    if _paddle_ocr is None:
        os.environ["PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK"] = "True"
        os.environ["FLAGS_use_mkldnn"] = "0"
        os.environ["FLAGS_onednn_cpu_detection"] = "0"
        logger.info("Initializing PaddleOCR...")
        from paddleocr import PaddleOCR
        _paddle_ocr = PaddleOCR(lang='en')
    return _paddle_ocr


# ── OpenRouter OCR ────────────────────────────────────────────────────────────
def _ocr_with_openrouter(image_path: str) -> Tuple[str, float]:
    with open(image_path, "rb") as f:
        image_data = base64.b64encode(f.read()).decode("utf-8")

    ext = os.path.splitext(image_path)[1].lower()
    media_type = "image/png" if ext == ".png" else "image/jpeg"

    payload = {
        "model": OPENROUTER_OCR_MODEL,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:{media_type};base64,{image_data}"
                        }
                    },
                    {
                        "type": "text",
                        "text": (
                            "You are an OCR engine. Extract ALL handwritten and printed text "
                            "from this answer sheet image exactly as written. "
                            "Preserve paragraph structure. Output only the extracted text, "
                            "no explanations or commentary."
                        )
                    }
                ]
            }
        ]
    }

    response = httpx.post(
        "https://openrouter.ai/api/v1/chat/completions",
        headers={
            "Authorization": f"Bearer {OPENROUTER_API_KEY}",
            "Content-Type": "application/json",
        },
        json=payload,
        timeout=60.0,
    )
    response.raise_for_status()
    text = response.json()["choices"][0]["message"]["content"].strip()
    return text, 1.0  # OpenRouter doesn't return confidence scores


# ── PaddleOCR ─────────────────────────────────────────────────────────────────
def _ocr_with_paddle(image_path: str) -> Tuple[str, float]:
    ocr_engine = get_paddle_ocr()

    try:
        result = ocr_engine.ocr(image_path, cls=True)
    except TypeError:
        result = ocr_engine.ocr(image_path)

    if not result or result[0] is None:
        return "", 0.0

    texts, confidences = [], []
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
            except (IndexError, TypeError):
                pass

    full_text = " ".join(texts)
    avg_conf = sum(confidences) / len(confidences) if confidences else 0.0
    return full_text, avg_conf


# ── Public API ────────────────────────────────────────────────────────────────
def extract_text_from_image(image_path: str) -> Tuple[str, float]:
    start = time.time()
    logger.info(f"[OCR] Starting OCR for: {os.path.basename(image_path)} | mode={OCR_MODE}")

    if not os.path.exists(image_path):
        raise FileNotFoundError(f"Image not found: {image_path}")

    try:
        if OCR_MODE == "paddle":
            text, confidence = _ocr_with_paddle(image_path)
        else:
            text, confidence = _ocr_with_openrouter(image_path)
    except Exception as e:
        logger.error(f"[OCR] Failed for {image_path}: {e}")
        return "", 0.0

    logger.info(
        f"[OCR] Completed {os.path.basename(image_path)} | "
        f"text_len={len(text)} | time={time.time() - start:.2f}s"
    )
    return text, confidence


def extract_text_from_images(image_paths: List[str]) -> Tuple[str, float]:
    batch_start = time.time()
    logger.info(f"[OCR-BATCH] Starting OCR for {len(image_paths)} images | mode={OCR_MODE}")

    results = {}

    with ThreadPoolExecutor(max_workers=2) as executor:
        future_to_idx = {
            executor.submit(extract_text_from_image, path): idx
            for idx, path in enumerate(sorted(image_paths))
        }

        for future in as_completed(future_to_idx):
            idx = future_to_idx[future]
            try:
                text, confidence = future.result()
                results[idx] = (text, confidence)
                logger.info(f"[OCR-BATCH] Page {idx+1} done")
            except Exception as e:
                logger.error(f"[OCR-BATCH] Page {idx+1} failed: {e}")
                results[idx] = ("", 0.0)

    # Reassemble in correct page order
    all_texts = [results[i][0] for i in sorted(results) if results[i][0]]
    all_confidences = [results[i][1] for i in sorted(results) if results[i][0]]

    full_text = "\n".join(all_texts)
    avg_confidence = sum(all_confidences) / len(all_confidences) if all_confidences else 0.0

    logger.info(
        f"[OCR-BATCH] Completed {len(image_paths)} pages | "
        f"total_time={time.time() - batch_start:.2f}s | avg_conf={avg_confidence:.2f}"
    )

    return full_text, avg_confidence