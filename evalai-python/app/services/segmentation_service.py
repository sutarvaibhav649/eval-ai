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
    Splits OCR-extracted full text into per-sub-question segments.

    Uses regex to find sub-question labels and splits text at each label.
    Only looks for labels present in expected_labels — prevents false positives
    from student body text that might accidentally match the pattern.

    Args:
        full_text:       complete OCR text from all pages
        expected_labels: list of labels Java sent (e.g. ['1a', '1b', '2a'])

    Returns:
        dict mapping label → extracted text for that sub-question
        e.g. {'1a': 'Mitosis is...', '1b': 'DNA replication...'}
    """
    if not full_text or not expected_labels:
        return {}

    # Find all label matches in the text
    matches = []
    for match in LABEL_PATTERN.finditer(full_text):
        q_num = int(match.group(1))
        letter = match.group(2).lower()
        label = normalize_label(q_num, letter)

        # Only process labels we expect — prevents false positives
        if label in expected_labels:
            matches.append({
                "label": label,
                "start": match.start(),
                "end": match.end()
            })

    if not matches:
        logger.warning(f"No expected labels found in text. Expected: {expected_labels}")
        return {}

    # Sort matches by position in text
    matches.sort(key=lambda x: x["start"])

    # Split text at each label boundary
    segments = {}
    for i, match in enumerate(matches):
        label = match["label"]
        text_start = match["end"]

        # Text ends at start of next label or end of document
        if i + 1 < len(matches):
            text_end = matches[i + 1]["start"]
        else:
            text_end = len(full_text)

        segment_text = full_text[text_start:text_end].strip()
        segments[label] = segment_text
        logger.debug(f"Segmented label '{label}': {len(segment_text)} chars")

    return segments


def is_text_sufficient(text: str, min_words: int = 10) -> bool:
    """
    Checks if extracted text has enough content to be meaningful.
    Texts under 10 words are considered too short for evaluation.

    Args:
        text:      extracted text
        min_words: minimum word count threshold (default 10)

    Returns:
        True if text is sufficient, False otherwise
    """
    if not text:
        return False
    word_count = len(text.split())
    return word_count >= min_words