from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.api.routes import ocr, embeddings, health
from app.core.logger import get_logger
from app.core.config import get_settings
from app.services.embedding_service import get_model
from app.services.ocr_service import get_paddle_ocr

logger = get_logger(__name__)
settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    FIX: @app.on_event("startup"/"shutdown") is deprecated in FastAPI.
    Use lifespan context manager instead.

    Startup logic runs before `yield`.
    Shutdown logic runs after `yield`.
    """
    #  Startup
    logger.info("EvalAI Python service starting up...")
    logger.info("Routes registered: /health, /embeddings, /ocr")
    logger.info("Preloading models...")

    get_model()  # Always preload embedding model

    if settings.ocr_mode.lower() == "paddle":
        get_paddle_ocr()
        logger.info("Embedding + PaddleOCR models loaded successfully")
    else:
        logger.info(
            f"Embedding model loaded | OCR mode: {settings.ocr_mode} (no preload needed)"
        )

    yield  # Application runs here

    #  Shutdown
    logger.info("EvalAI Python service shutting down...")


app = FastAPI(
    title="EvalAI Python Service",
    description="OCR extraction, embedding generation, and scoring service",
    version="1.0.0",
    lifespan=lifespan,  # FIX: use lifespan instead of on_event
)

# Register routers
app.include_router(health.router, prefix="/health", tags=["Health"])
app.include_router(embeddings.router, prefix="/embeddings", tags=["Embeddings"])
app.include_router(ocr.router, prefix="/ocr", tags=["OCR"])