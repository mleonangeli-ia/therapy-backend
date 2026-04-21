package com.therapy.knowledge;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * Upload a PDF document to the knowledge base.
     * Processing (text extraction + embedding) happens asynchronously.
     */
    @PostMapping("/documents")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("title") String title,
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo está vacío"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solo se aceptan archivos PDF"));
        }

        KnowledgeDocument doc = knowledgeService.uploadDocument(title, file);

        return ResponseEntity.accepted().body(Map.of(
                "id", doc.getId(),
                "title", doc.getTitle(),
                "fileName", doc.getFileName(),
                "status", doc.getStatus(),
                "message", "Documento recibido. El procesamiento se realiza en segundo plano."
        ));
    }

    /**
     * List all documents in the knowledge base.
     */
    @GetMapping("/documents")
    public ResponseEntity<List<KnowledgeDocument>> listDocuments() {
        return ResponseEntity.ok(knowledgeService.listDocuments());
    }

    /**
     * Delete a document and all its chunks.
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        knowledgeService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
