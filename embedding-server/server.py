#!/usr/bin/env python3
"""Local OpenAI-compatible embeddings server using sentence-transformers.

Exposes POST /v1/embeddings with the same request/response schema as OpenAI,
so the Java backend can use it by just changing the base URL.

Model: paraphrase-multilingual-mpnet-base-v2 (768 dims, Spanish-optimized).
"""
from typing import List, Union
from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
import uvicorn

MODEL_NAME = "paraphrase-multilingual-mpnet-base-v2"
PORT = 8090

app = FastAPI()
print(f"Loading model: {MODEL_NAME}...")
model = SentenceTransformer(MODEL_NAME)
print(f"Model loaded. Dimension: {model.get_sentence_embedding_dimension()}")


class EmbeddingRequest(BaseModel):
    model: str
    input: Union[str, List[str]]


@app.post("/v1/embeddings")
def embeddings(req: EmbeddingRequest):
    texts = [req.input] if isinstance(req.input, str) else req.input
    vectors = model.encode(texts, convert_to_numpy=True, show_progress_bar=False)
    data = [
        {"object": "embedding", "index": i, "embedding": vec.tolist()}
        for i, vec in enumerate(vectors)
    ]
    total_tokens = sum(len(t) // 4 for t in texts)
    return {
        "object": "list",
        "data": data,
        "model": req.model,
        "usage": {"prompt_tokens": total_tokens, "total_tokens": total_tokens},
    }


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME, "dim": model.get_sentence_embedding_dimension()}


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=PORT, log_level="info")
