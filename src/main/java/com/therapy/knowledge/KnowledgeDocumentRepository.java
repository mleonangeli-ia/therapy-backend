package com.therapy.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    List<KnowledgeDocument> findByStatusOrderByCreatedAtDesc(String status);

    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();
}
