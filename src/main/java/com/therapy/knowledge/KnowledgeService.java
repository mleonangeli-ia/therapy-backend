package com.therapy.knowledge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private static final int SEARCH_TOP_K = 5;

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final PdfProcessingService pdfService;
    private final EmbeddingService embeddingService;
    private final KnowledgeProcessingService processingService;

    /**
     * Upload a PDF document: save metadata, then process asynchronously.
     * Reads file bytes eagerly because MultipartFile is request-scoped
     * and won't be available when the @Async method runs.
     */
    @Transactional
    public KnowledgeDocument uploadDocument(String title, MultipartFile file) {
        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Error al leer el archivo PDF", e);
        }

        KnowledgeDocument doc = KnowledgeDocument.builder()
                .title(title)
                .fileName(file.getOriginalFilename())
                .fileSizeBytes(file.getSize())
                .status("PROCESSING")
                .build();
        doc = documentRepository.save(doc);

        processingService.processDocumentAsync(doc.getId(), pdfBytes);
        return doc;
    }

    /**
     * Semantic search: find the most relevant knowledge chunks for a given query.
     * Used by the AI orchestrator to inject therapeutic knowledge into the prompt.
     */
    @Transactional(readOnly = true)
    public List<KnowledgeChunk> searchRelevantChunks(String query) {
        float[] queryEmbedding = embeddingService.embed(query);
        String pgVector = embeddingService.toPgVectorString(queryEmbedding);
        return chunkRepository.findSimilarChunks(pgVector, SEARCH_TOP_K);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> listDocuments() {
        return documentRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void deleteDocument(UUID documentId) {
        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.deleteById(documentId);
    }

    @Transactional(readOnly = true)
    public boolean hasKnowledgeBase() {
        return !documentRepository.findByStatusOrderByCreatedAtDesc("READY").isEmpty();
    }
}
