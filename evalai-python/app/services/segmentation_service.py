import re
from typing import Dict, List, Optional
from app.core.logger import get_logger

logger = get_logger(__name__)

# Regex pattern to detect sub-question labels
# Matches formats: 1a, 1b, Q1a, Q.1a, (1a), 1-a, 1.a, a), (a)
LABEL_PATTERN = re.compile(
    r'(?:^|\n)\s*'                          # start of line
    r'(?:Q\.?|Question\.?)?\s*'             # optional Q or Question prefix
    r'(?:\()?'                              # optional opening bracket
    r'(\d+)\s*'                             # question number (e.g. 1, 2)
    r'[-\.\s]?'                             # optional separator
    r'([a-zA-Z])'                           # sub-question letter (e.g. a, b)
    r'(?:\))?'                              # optional closing bracket
    r'[\s\.\):\-]*',                        # optional trailing separator
    re.MULTILINE | re.IGNORECASE
)


def normalize_label(question_number: int, letter: str) -> str:
    """
    Normalizes a detected label to standard format.
    Example: question_number=1, letter='a' → '1a'
    """
    return f"{question_number}{letter.lower()}"


def segment_text_by_labels(
    full_text: str,
    expected_labels: List[str]
) -> Dict[str, str]:
    """
    Splits OCR text into per-sub-question segments.
    Supports label formats: Q.1, Q.2, 1a, 1b, Q1, Q2, (1a), 1-a etc.
    """
    if not full_text or not expected_labels:
        return {}

    # Build a pattern for each expected label
    # This finds the exact label in text regardless of surrounding punctuation
    segments = {}
    positions = []

    for label in expected_labels:
        # Escape the label for regex and build flexible pattern
        # Q.1 → matches Q.1, Q.1., Q. 1, Q1 etc.
        # 1a  → matches 1a, 1.a, (1a), 1-a etc.

        if label.upper().startswith('Q.') or label.upper().startswith('Q'):
            # Format: Q.1, Q.2, Q1, Q2
            num = re.sub(r'[^0-9]', '', label)  # extract just the number
            pattern = re.compile(
                r'(?:^|\n)\s*'
                r'Q\s*\.?\s*' + re.escape(num) + r'[.\s:\-)]',
                re.MULTILINE | re.IGNORECASE
            )
        else:
            # Format: 1a, 1b, 2a etc.
            pattern = re.compile(
                r'(?:^|\n)\s*'
                r'(?:Q\.?|Question\.?)?\s*'
                r'(?:\()?' + re.escape(label[0]) + r'\s*'
                r'[-\.\s]?' + re.escape(label[1:]) + r'(?:\))?'
                r'[\s\.\):\-]*',
                re.MULTILINE | re.IGNORECASE
            )

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
        logger.warning(
            f"No expected labels found in text. Expected: {expected_labels}"
        )
        return {}

    # Sort by position
    positions.sort(key=lambda x: x["start"])

    # Extract text between label positions
    for i, pos in enumerate(positions):
        label = pos["label"]
        text_start = pos["end"]
        text_end = positions[i + 1]["start"] if i + 1 < len(positions) else len(full_text)
        segment = full_text[text_start:text_end].strip()
        segments[label] = segment
        logger.info(f"Segment '{label}': {len(segment)} chars")

    return segments


def is_text_sufficient(text: str, min_words: int = 10) -> bool:
    """
    Checks if extracted text has enough content to be meaningful.
    """
    if not text:
        return False
    return len(text.split()) >= min_words