from fastapi import APIRouter
from app.core.config import get_settings

router = APIRouter()
settings = get_settings()


@router.get("")
async def health_check():
    """
    Health check endpoint.
    Used by Docker and Java to verify Python service is running.
    """
    return {
        "status": "healthy",
        "service": "evalai-python",
        "ocr_mode": settings.ocr_mode,
        "embedding_model": settings.embedding_model
    }