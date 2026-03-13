from sentence_transformers import SentenceTransformer
from app.core.config import get_settings
from app.core.logger import get_logger
from typing import List

logger = get_logger(__name__)
settings = get_settings()

# Load model once at startup — expensive operation
# all-MiniLM-L6-v2 produces 384-dimensional embeddings
_model: SentenceTransformer = None

def get_model() -> SentenceTransformer:
    """
    Lazy-loads the embedding model on first call.
    Keeps model in memory for subsequent calls — avoids reloading.
    """
    global _model
    if _model is None:
        logger.info(f"Loading embedding model: {settings.embedding_model}")
        _model = SentenceTransformer(settings.embedding_model)
        logger.info("Embedding model loaded successfully")
    return _model


def generate_embedding(text: str) -> List[float]:
    """
    Generates a 384-dimensional embedding vector for the given text.
    Uses all-MiniLM-L6-v2 via sentence-transformers.

    Args:
        text: input text to embed

    Returns:
        list of 384 float values
    """
    model = get_model()
    embedding = model.encode(text, convert_to_numpy=True)
    return embedding.tolist()