from pydantic import BaseModel
from typing import List

class EmbeddingRequest(BaseModel):
    text: str

class EmbeddingResponse(BaseModel):
    text: str
    embedding: List[float]
    dimensions: int