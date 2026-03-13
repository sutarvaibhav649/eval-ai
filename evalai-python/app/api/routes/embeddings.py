from fastapi import APIRouter
from app.schemas.embedding_schema import EmbeddingRequest, EmbeddingResponse
from app.services.embedding_service import generate_embedding
from app.core.logger import get_logger

logger = get_logger(__name__)
router = APIRouter()


@router.post("/generate", response_model=EmbeddingResponse)
async def generate_embedding_route(request: EmbeddingRequest):
    """
    Generates a 384-dimensional embedding for the given text.
    Called by Java when faculty submits a model answer.

    Returns the embedding vector that Java stores in pgvector.
    """
    logger.info(f"Generating embedding for text of length {len(request.text)}")
    embedding = generate_embedding(request.text)
    return EmbeddingResponse(
        text=request.text,
        embedding=embedding,
        dimensions=len(embedding)
    )