package com.therapy.audio;

import com.therapy.audio.dto.AudioUploadResponse;
import com.therapy.session.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioService {

    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024L; // 25MB

    private final WhisperApiClient whisperApiClient;
    private final ElevenLabsApiClient elevenLabsApiClient;
    private final StorageService storageService;
    private final SessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;

    /**
     * Receives an audio file from the patient, transcribes it, saves both the raw
     * audio and the transcript as a SessionMessage, and returns the transcription.
     */
    @Transactional
    public AudioUploadResponse uploadAndTranscribe(UUID sessionId, UUID patientId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de audio está vacío");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("El archivo de audio supera el límite de 25MB");
        }

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada"));

        if (!session.getPatient().getId().equals(patientId)) {
            throw new SecurityException("No autorizado");
        }
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("La sesión ya no está activa");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Error al leer el archivo de audio", e);
        }

        String mimeType = file.getContentType() != null ? file.getContentType() : "audio/webm";

        // Store raw audio
        String audioKey = storageService.storeAudio(bytes, mimeType, patientId, sessionId);
        log.debug("Stored patient audio: {}", audioKey);

        // Transcribe with Whisper
        WhisperApiClient.TranscriptionResult result = whisperApiClient.transcribe(bytes, mimeType, "es");
        log.debug("Transcription: {} chars, confidence: {}", result.text().length(), result.confidence());

        // Persist as AUDIO_TRANSCRIPT message
        int nextSeq = messageRepository.findMaxSequenceNumberBySessionId(sessionId) + 1;
        SessionMessage msg = SessionMessage.builder()
                .session(session)
                .role(SessionMessage.Role.PATIENT)
                .contentType(SessionMessage.ContentType.AUDIO_TRANSCRIPT)
                .contentTextEnc(result.text())
                .audioS3Key(audioKey)
                .transcriptionConfidence(result.confidence())
                .sequenceNumber(nextSeq)
                .build();
        SessionMessage saved = messageRepository.save(msg);

        return new AudioUploadResponse(saved.getId(), result.text(), result.confidence());
    }

    /**
     * Generates TTS audio for an AI response message and returns a playback URL.
     * Called by AiOrchestratorService after streaming completes in AUDIO/MIXED sessions.
     */
    public String generateAndStoreTts(String text, UUID sessionId, UUID messageId) {
        if (!elevenLabsApiClient.isConfigured()) {
            log.warn("ElevenLabs not configured — skipping TTS for message {}", messageId);
            return null;
        }

        byte[] mp3 = elevenLabsApiClient.generateSpeech(text, "es");
        if (mp3.length == 0) {
            log.warn("ElevenLabs returned empty audio for message {}", messageId);
            return null;
        }

        String key = storageService.storeTtsAudio(mp3, sessionId, messageId);
        log.debug("Stored TTS audio: {}", key);

        // Return signed URL valid for 4 hours (enough for the session)
        return storageService.getAudioUrl(key, Duration.ofHours(4));
    }

    /**
     * Returns a presigned URL for a stored audio file (patient recording or TTS response).
     */
    public String getAudioUrl(String key) {
        return storageService.getAudioUrl(key, Duration.ofHours(1));
    }
}
