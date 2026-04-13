package com.therapy.audio;

import com.therapy.audio.dto.AudioUploadResponse;
import com.therapy.audio.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/audio")
@RequiredArgsConstructor
public class AudioController {

    private final AudioService audioService;
    private final StorageService storageService;

    /**
     * POST /audio/upload?sessionId={sessionId}
     * Multipart upload of a patient audio recording.
     * Returns the transcribed text and message ID.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AudioUploadResponse> upload(
            @AuthenticationPrincipal UUID patientId,
            @RequestParam UUID sessionId,
            @RequestPart("file") MultipartFile file) {

        AudioUploadResponse response = audioService.uploadAndTranscribe(sessionId, patientId, file);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /audio/file?key={key}
     * Serves local audio files in dev mode (when storage.local=true).
     * In production, use the presigned S3 URL instead.
     */
    @GetMapping("/file")
    public ResponseEntity<Resource> serveLocalFile(@RequestParam String key) {
        // Resolve key against the local base dir
        Path filePath = Path.of(System.getProperty("java.io.tmpdir"), "therapy-storage", key);
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = key.endsWith(".mp3") ? "audio/mpeg"
                : key.endsWith(".mp4") ? "audio/mp4"
                : key.endsWith(".pdf") ? "application/pdf"
                : "audio/webm";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(resource);
    }

    /**
     * GET /audio/response/{messageId}?sessionId={sessionId}
     * Returns the presigned URL for a stored TTS response audio.
     */
    @GetMapping("/response/{messageId}")
    public ResponseEntity<java.util.Map<String, String>> getResponseUrl(
            @AuthenticationPrincipal UUID patientId,
            @PathVariable UUID messageId,
            @RequestParam UUID sessionId) {

        // Key convention: responses/{sessionId}/{messageId}.mp3
        String key = "responses/%s/%s.mp3".formatted(sessionId, messageId);
        String url = audioService.getAudioUrl(key);
        return ResponseEntity.ok(java.util.Map.of("url", url));
    }
}
