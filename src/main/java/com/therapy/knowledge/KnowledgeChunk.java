package com.therapy.knowledge;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "knowledge_chunks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "token_count")
    private Integer tokenCount;

    // Embedding stored/queried via native SQL — no JPA mapping needed for vector type

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
