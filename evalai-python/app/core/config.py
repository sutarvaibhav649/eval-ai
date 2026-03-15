from pydantic_settings import BaseSettings
from pydantic import Field
from functools import lru_cache


class Settings(BaseSettings):
    # Server
    app_host: str = "0.0.0.0"
    app_port: int = 8000
    app_env: str = "development"

    # Redis
    redis_url: str = "redis://localhost:6379/0"

    # Java callback
    java_base_url: str = "http://localhost:8081"

    # OCR
    ocr_mode: str = "PADDLE"
    gemini_api_key: str = ""

    # OpenRouter
    openrouter_api_key: str = ""
    openrouter_ocr_model: str = "meta-llama/llama-4-scout"

    # Upload path
    upload_base_path: str = "../upload"

    # Embedding model
    embedding_model: str = "all-MiniLM-L6-v2"

    model_config = {
        "env_file": ".env",
        "env_file_encoding": "utf-8",
        "extra": "ignore"
    }


@lru_cache()
def get_settings() -> Settings:
    return Settings()