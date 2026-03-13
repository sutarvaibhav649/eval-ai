import numpy as np
from typing import List
from app.core.logger import get_logger


logger = get_logger(__name__)

def cosine_similarity(vec1: List[float], vec2: List[float]) -> float:
    """
    Calculates cosine similarity between two embedding vectors.
    Returns a value between 0.0 (no similarity) and 1.0 (identical).

    Args:
        vec1: model answer embedding (384-dim)
        vec2: student answer embedding (384-dim)

    Returns:
        float similarity score between 0.0 and 1.0
    """
    a = np.array(vec1)
    b = np.array(vec2)

    # Guard against zero vectors
    norm_a = np.linalg.norm(a)
    norm_b = np.linalg.norm(b)

    if norm_a == 0 or norm_b == 0:
        logger.warning("Zero vector encountered in cosine similarity calculation")
        return 0.0

    return float(np.dot(a, b) / (norm_a * norm_b))


def calculate_marks(
    similarity_score: float,
    max_marks: float,
    threshold: float = 0.5
) -> float:
    """
    Converts a cosine similarity score to marks.

    Scoring logic:
    - similarity >= 0.85 → full marks
    - similarity >= threshold (0.5) → proportional marks
    - similarity < threshold → 0 marks

    Args:
        similarity_score: cosine similarity between 0.0 and 1.0
        max_marks:        maximum marks for this sub-question
        threshold:        minimum similarity to get any marks (default 0.5)

    Returns:
        float marks awarded, rounded to 1 decimal place
    """
    if similarity_score >= 0.85:
        # Near-perfect answer — full marks
        return round(max_marks, 1)

    if similarity_score >= threshold:
        # Proportional scoring between threshold and 0.85
        # Maps similarity range [threshold, 0.85] to marks range [0, max_marks]
        proportion = (similarity_score - threshold) / (0.85 - threshold)
        return round(proportion * max_marks, 1)

    # Below threshold — no marks
    return 0.0