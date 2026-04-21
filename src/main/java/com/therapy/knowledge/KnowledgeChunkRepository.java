package com.therapy.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {

    /**
     * Semantic similarity search using pgvector cosine distance.
     * Returns the top-K most relevant chunks across all documents.
     */
    @Query(value = """
            SELECT kc.id, kc.document_id, kc.chunk_index, kc.content, kc.token_count, kc.created_at
            FROM knowledge_chunks kc
            JOIN knowledge_documents kd ON kd.id = kc.document_id
            WHERE kd.status = 'READY'
              AND kc.embedding IS NOT NULL
            ORDER BY kc.embedding <=> cast(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<KnowledgeChunk> findSimilarChunks(
            @Param("embedding") String embedding,
            @Param("limit") int limit);

    /**
     * Store the embedding for a chunk (native SQL since JPA doesn't map vector type).
     */
    @Modifying
    @Query(value = """
            UPDATE knowledge_chunks
            SET embedding = cast(:embedding AS vector)
            WHERE id = :chunkId
            """, nativeQuery = true)
    void updateEmbedding(
            @Param("chunkId") UUID chunkId,
            @Param("embedding") String embedding);

    List<KnowledgeChunk> findByDocumentIdOrderByChunkIndex(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
