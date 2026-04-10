import re
from typing import Dict, List, Optional
from app.core.logger import get_logger

logger = get_logger(__name__)


def normalize_label(question_number: int, letter: str) -> str:
    """Normalize a detected label. Example: (1, 'a') → '1a'"""
    return f"{question_number}{letter.lower()}"


def segment_text_by_labels(
    full_text: str,
    expected_labels: List[str]
) -> Dict[str, str]:
    """
    Splits OCR text into per-sub-question segments.

    FIX: The original code had a critical bug — for non-Q format labels (e.g. '1a'),
    it used a generic capture-group regex but NEVER validated that the captured
    number+letter matched the expected label. So '1a' could match '2b' if '2b'
    appeared first in the text.

    Fix: For each expected label, build a SPECIFIC regex that matches ONLY that
    label (e.g., for '1a' → match exactly '1' followed by 'a', not any digit+letter).
    """
    if not full_text or not expected_labels:
        return {}

    segments = {}
    positions = []

    for label in expected_labels:
        pattern = _build_label_pattern(label)
        match = pattern.search(full_text)

        if match:
            positions.append({
                "label": label,
                "start": match.start(),
                "end": match.end()
            })
        else:
            logger.warning(f"Label '{label}' not found in text")

    if not positions:
        logger.warning("[SEGMENTATION FAILED] No labels found — using fallback split")
        return _fallback_split(full_text, expected_labels)

    # Sort by position in document
    positions.sort(key=lambda x: x["start"])

    # Extract text between consecutive label positions
    for i, pos in enumerate(positions):
        label = pos["label"]
        text_start = pos["end"]
        text_end = (
            positions[i + 1]["start"] if i + 1 < len(positions) else len(full_text)
        )
        segment = full_text[text_start:text_end].strip()
        segments[label] = segment
        logger.info(f"Segment '{label}': {len(segment)} chars")

    return segments


def _build_label_pattern(label: str) -> re.Pattern:
    """
    FIX: Build a SPECIFIC regex for each label instead of a generic one.

    Handles formats:
      Q.1, Q1      → Q-prefix numeric labels
      1a, 1b, 2a   → number+letter sub-question labels (SPECIFIC match)
    """
    label_upper = label.upper()

    if label_upper.startswith('Q'):
        # Format: Q.1, Q.2, Q1, Q2 — extract number only
        num = re.sub(r'[^0-9]', '', label)
        return re.compile(
            r'(?:^|\n)\s*'
            r'Q\s*\.?\s*' + re.escape(num) + r'[.\s:\-)\n]',
            re.MULTILINE | re.IGNORECASE
        )
    else:
        # FIX: For labels like '1a', '2b' — match the EXACT number and letter.
        # Old code: generic (\d+)...([a-zA-Z]) which matched ANY digit+letter.
        # New code: escape and match exactly the digits and letter in this label.

        # Split label into numeric prefix and letter suffix
        # e.g. '1a' → num='1', letter='a'
        #      '10b' → num='10', letter='b'
        match = re.match(r'^(\d+)([a-zA-Z])$', label)
        if match:
            num = re.escape(match.group(1))
            letter = re.escape(match.group(2))
            return re.compile(
                r'(?:^|\n)\s*'
                r'(?:Q\.?|Question\.?)?\s*'
                r'(?:\()?'
                + num +                    # EXACT question number
                r'\s*[-\.\s]?\s*'
                + letter +                 # EXACT sub-question letter
                r'(?:\))?'
                r'[\s\.\):\-]*',
                re.MULTILINE | re.IGNORECASE
            )
        else:
            # Fallback for unusual label formats — escape and match literally
            logger.warning(f"Unusual label format '{label}' — using literal match")
            return re.compile(
                r'(?:^|\n)\s*' + re.escape(label) + r'[\s\.\):\-]*',
                re.MULTILINE | re.IGNORECASE
            )


def _fallback_split(full_text: str, expected_labels: List[str]) -> Dict[str, str]:
    """
    Naive fallback: split text equally across all labels.
    Used only when no labels are detected in the OCR text.
    """
    words = full_text.split()
    chunk_size = max(1, len(words) // len(expected_labels))
    segments = {}

    for i, label in enumerate(expected_labels):
        start = i * chunk_size
        end = (
            (i + 1) * chunk_size if i < len(expected_labels) - 1 else len(words)
        )
        segments[label] = " ".join(words[start:end])

    return segments


def is_text_sufficient(text: str) -> bool:
    """Returns True if extracted text has enough content to evaluate."""
    if not text:
        return False

    text = text.strip()

    if len(text) < 8:
        return False

    words = text.split()

    if len(words) < 3:
        return False

    # Reject single-character noise like: "a b c d"
    meaningful_words = [w for w in words if len(w) > 1]
    if len(meaningful_words) < 2:
        return False

    return True