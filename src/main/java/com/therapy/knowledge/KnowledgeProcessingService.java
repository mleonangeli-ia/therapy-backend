package com.therapy.knowledge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Handles async PDF processing (text extraction, chunking, embedding).
 * Extracted from KnowledgeService to ensure @Async works correctly
 * (Spring proxies don't intercept self-invocations).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeProcessingService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final PdfProcessingService pdfService;
    private final EmbeddingService embeddingService;

    @Async
    public void processDocumentAsync(UUID documentId, byte[] pdfBytes) {
        try {
            String text = pdfService.extractText(pdfBytes);

            List<String> chunks = pdfService.chunkText(text);
            log.info("Document {} split into {} chunks", documentId, chunks.size());

            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);

                KnowledgeChunk chunk = KnowledgeChunk.builder()
                        .document(documentRepository.getReferenceById(documentId))
                        .chunkIndex(i)
                        .content(chunkText)
                        .tokenCount(pdfService.estimateTokens(chunkText))
                        .build();
                chunk = chunkRepository.save(chunk);

                float[] embedding = embeddingService.embed(chunkText);
                chunkRepository.updateEmbedding(chunk.getId(), embeddingService.toPgVectorString(embedding));

                if ((i + 1) % 10 == 0) {
                    log.info("Document {}: processed {}/{} chunks", documentId, i + 1, chunks.size());
                }
            }

            KnowledgeDocument doc = documentRepository.findById(documentId).orElseThrow();
            doc.setStatus("READY");
            doc.setTotalChunks(chunks.size());
            documentRepository.save(doc);

            log.info("Document {} fully processed: {} chunks with embeddings", documentId, chunks.size());

        } catch (Exception e) {
            log.error("Failed to process document {}", documentId, e);
            documentRepository.findById(documentId).ifPresent(doc -> {
                doc.setStatus("FAILED");
                doc.setErrorMessage(e.getMessage());
                documentRepository.save(doc);
            });
        }
    }
}
