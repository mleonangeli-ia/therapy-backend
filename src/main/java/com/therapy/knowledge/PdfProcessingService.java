package com.therapy.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts text from PDFs and splits it into overlapping chunks
 * suitable for embedding and semantic search.
 */
@Slf4j
@Service
public class PdfProcessingService {

    private static final int CHUNK_SIZE_CHARS = 1500;    // ~375 tokens
    private static final int CHUNK_OVERLAP_CHARS = 200;  // overlap for context continuity

    /**
     * Extract all text from a PDF's raw bytes.
     * Writes to a temp file first so PDFBox uses memory-mapped I/O
     * instead of loading everything into heap (critical for large PDFs).
     */
    public String extractText(byte[] pdfBytes) throws IOException {
        Path tempFile = Files.createTempFile("therapy-pdf-", ".pdf");
        try {
            Files.write(tempFile, pdfBytes);
            try (PDDocument document = Loader.loadPDF(tempFile.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                log.info("Extracted {} characters from PDF", text.length());
                return text;
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Split text into overlapping chunks using a sliding window approach.
     * Tries to break at paragraph or sentence boundaries when possible.
     */
    public List<String> chunkText(String text) {
        // Normalize whitespace
        text = text.replaceAll("\\r\\n", "\n")
                   .replaceAll("[ \\t]+", " ")
                   .trim();

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE_CHARS, text.length());

            // Try to break at a paragraph boundary
            if (end < text.length()) {
                int paragraphBreak = text.lastIndexOf("\n\n", end);
                if (paragraphBreak > start + CHUNK_SIZE_CHARS / 2) {
                    end = paragraphBreak;
                } else {
                    // Try sentence boundary (period followed by space or newline)
                    int sentenceBreak = findLastSentenceBreak(text, start + CHUNK_SIZE_CHARS / 2, end);
                    if (sentenceBreak > 0) {
                        end = sentenceBreak;
                    }
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty() && chunk.length() > 50) {
                chunks.add(chunk);
            }

            // Advance with overlap
            start = end - CHUNK_OVERLAP_CHARS;
            if (start <= (end - CHUNK_SIZE_CHARS)) {
                start = end; // prevent infinite loop on very small remaining text
            }
        }

        log.info("Split text into {} chunks (avg {} chars/chunk)",
                chunks.size(),
                chunks.isEmpty() ? 0 : chunks.stream().mapToInt(String::length).average().orElse(0));

        return chunks;
    }

    private int findLastSentenceBreak(String text, int from, int to) {
        for (int i = to; i >= from; i--) {
            if (i < text.length() && text.charAt(i) == '.' &&
                i + 1 < text.length() && (text.charAt(i + 1) == ' ' || text.charAt(i + 1) == '\n')) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Rough token estimate: ~4 characters per token for Spanish text.
     */
    public int estimateTokens(String text) {
        return text.length() / 4;
    }
}
