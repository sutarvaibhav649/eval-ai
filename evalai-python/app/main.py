from fastapi import FastAPI
from app.api.routes import ocr, embeddings, health
from app.core.logger import get_logger

logger = get_logger(__name__)

app = FastAPI(
    title="EvalAI Python Service",
    description="OCR extraction, embedding generation, and scoring service",
    version="1.0.0"
)

# Register routers with prefixes
app.include_router(health.router, prefix="/health", tags=["Health"])
app.include_router(embeddings.router, prefix="/embeddings", tags=["Embeddings"])
app.include_router(ocr.router, prefix="/ocr", tags=["OCR"])


@app.on_event("startup")
async def startup_event():
    logger.info("EvalAI Python service starting up...")
    logger.info("Routes registered: /health, /embeddings, /ocr")


@app.on_event("shutdown")
async def shutdown_event():
    logger.info("EvalAI Python service shutting down...")