package com.therapy.knowledge;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "knowledge_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "total_chunks")
    @Builder.Default
    private int totalChunks = 0;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "PROCESSING";

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
