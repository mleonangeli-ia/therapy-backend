# Local Embedding Server

OpenAI-compatible embeddings endpoint backed by `sentence-transformers`.
Used by the Java backend for RAG over the therapeutic knowledge base.

## Model

`paraphrase-multilingual-mpnet-base-v2` — 768 dimensions, multilingual
(optimized for Spanish). Runs locally, zero API cost, private.

## Run

```bash
pip3 install -r requirements.txt
python3 server.py
```

Server listens on `http://127.0.0.1:8090`.

## Endpoints

- `POST /v1/embeddings` — OpenAI-compatible embedding endpoint
- `GET /health` — health check

## Schema

The `knowledge_chunks.embedding` column must be `vector(768)`.
