-- ============================================================
-- V14: Knowledge base for RAG (Retrieval-Augmented Generation)
-- Stores book/document chunks with vector embeddings for
-- semantic search during therapy sessions.
-- ============================================================

-- Enable pgvector extension for embedding storage and similarity search
CREATE EXTENSION IF NOT EXISTS "vector";

-- ============================================================
-- KNOWLEDGE DOCUMENTS (metadata about uploaded PDFs/books)
-- ============================================================
CREATE TABLE knowledge_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(500) NOT NULL,
    file_name       VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT,
    total_chunks    INT NOT NULL DEFAULT 0,
    status          VARCHAR(50) NOT NULL DEFAULT 'PROCESSING',
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_knowledge_documents_updated_at
    BEFORE UPDATE ON knowledge_documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ============================================================
-- KNOWLEDGE CHUNKS (text segments with embeddings)
-- ============================================================
CREATE TABLE knowledge_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL,
    token_count     INT,
    embedding       vector(1536),   -- OpenAI text-embedding-3-small dimensions
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_chunks_document ON knowledge_chunks(document_id, chunk_index);

-- HNSW index for fast approximate nearest neighbor search
CREATE INDEX idx_knowledge_chunks_embedding ON knowledge_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
