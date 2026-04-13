package com.therapy.ai;

import com.therapy.audio.AudioService;
import com.therapy.claude.ClaudeApiClient;
import com.therapy.claude.ClaudeMessage;
import com.therapy.session.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates AI responses for therapy sessions.
 *
 * Key scalability design:
 * - NO @Transactional on the main method — DB connections are NOT held during AI streaming
 * - Each DB operation uses its own short transaction via SessionPersistenceService
 * - Distributed processing lock stored in Redis (not in-memory), enabling horizontal scaling
 * - Message history bounded to last 20 messages, preventing unbounded DB reads
 *
 * Connection lifecycle per message:
 *   1. Short TX: load + validate session          (~5ms, connection released)
 *   2. Short TX: save patient message             (~5ms, connection released)
 *   3. NO DB: AI streaming                        (10-30s, ZERO connections held)
 *   4. Short TX: save AI message + update turns   (~5ms, connection released)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiOrchestratorService {

    private static final int  CRISIS_THRESHOLD_ALERT   = 9;  // emergencia real: plan suicida, autolesión activa
    private static final int  CRISIS_THRESHOLD_CAUTION = 6;  // señales de riesgo: respuesta más cuidadosa
    private static final int  MAX_TOKENS               = 8192;
    private static final long PROCESSING_LOCK_TTL_SEC  = 90; // max streaming time + buffer

    private static final String LOCK_PREFIX = "session:processing:";

    private final ClaudeApiClient           claudeApiClient;
    private final AudioService              audioService;
    private final SessionPersistenceService persistence;
    private final SessionContextService     contextService;
    private final TherapeuticPromptBuilder  promptBuilder;
    private final SimpMessagingTemplate     messagingTemplate;
    private final StringRedisTemplate       redisTemplate;

    /**
     * Main entry point: process a patient message and stream the AI response.
     *
     * NOT @Transactional — DB connections are held only during the brief persistence
     * calls, not during the entire AI streaming window.
     */
    public void processPatientMessage(UUID sessionId, UUID patientId, String messageText) {
        String lockKey = LOCK_PREFIX + sessionId;

        // Redis SETNX — atomic "set if absent". Returns true only if we got the lock.
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", PROCESSING_LOCK_TTL_SEC, TimeUnit.SECONDS);

        if (!Boolean.TRUE.equals(acquired)) {
            sendError(sessionId, "Esperá que termine la respuesta anterior");
            return;
        }

        try {
            // 1. Load + validate — short read-only TX, connection released immediately
            Session session = persistence.loadAndValidate(sessionId, patientId);

            // 2. Save patient message — short TX, connection released immediately
            persistence.savePatientMessage(sessionId, messageText);

            // 3. Crisis detection (fast sync call, no DB connection held)
            int crisisScore = detectCrisis(messageText);
            log.debug("Crisis score for session {}: {}", sessionId, crisisScore);

            if (crisisScore >= CRISIS_THRESHOLD_ALERT) {
                handleCrisis(session, sessionId, crisisScore);
                return; // lock released in handleCrisis
            }

            // 4. Build context — bounded history, short read-only TX
            List<SessionContext> previousContexts =
                    contextService.loadPreviousContexts(patientId);

            String systemPrompt = promptBuilder.buildSystemPrompt(
                    session.getPatient().getFullName(),
                    session.getSessionNumber(),
                    session.getPack().getSessionsTotal(),
                    previousContexts);

            if (crisisScore >= CRISIS_THRESHOLD_CAUTION) {
                systemPrompt += promptBuilder.buildCrisisResponseAddendum();
            }

            // Bounded history — last 20 messages only, short read-only TX
            List<SessionMessage> history = persistence.loadRecentHistory(sessionId);
            List<ClaudeMessage> claudeMessages = promptBuilder.buildMessageHistory(history);

            // 5. Notify frontend: AI is thinking
            sendStatus(sessionId, "PROCESSING");

            // 6. Stream AI response — DB connections are FREE during this window
            StringBuilder fullResponse = new StringBuilder();
            UUID aiMsgId = UUID.randomUUID();
            SessionModality modality = session.getModality();

            claudeApiClient.streamTherapeuticResponse(
                    systemPrompt,
                    claudeMessages,
                    MAX_TOKENS,
                    token -> {
                        fullResponse.append(token);
                        sendStreamChunk(sessionId, aiMsgId, token, false);
                    },
                    () -> {
                        String responseText = fullResponse.toString();

                        // 7. Persist AI response — short TX, connection released immediately
                        persistence.saveAiMessage(sessionId, modality, responseText);
                        persistence.incrementTurnCount(sessionId);

                        sendStreamChunk(sessionId, aiMsgId, "", true);
                        sendStatus(sessionId, "READY");

                        if (modality != SessionModality.TEXT) {
                            generateTtsAsync(sessionId, aiMsgId, responseText);
                        }

                        releaseLock(lockKey);
                    },
                    error -> {
                        log.error("AI streaming error for session {}", sessionId, error);
                        sendError(sessionId, "Error al procesar tu mensaje. Por favor intentá de nuevo.");
                        releaseLock(lockKey);
                    }
            );

        } catch (Exception e) {
            log.error("Error processing message for session {}", sessionId, e);
            sendError(sessionId, "Error interno. Por favor intentá de nuevo.");
            releaseLock(lockKey);
        }
    }

    @Async
    public void generateTtsAsync(UUID sessionId, UUID messageId, String text) {
        try {
            String audioUrl = audioService.generateAndStoreTts(text, sessionId, messageId);
            if (audioUrl != null) {
                messagingTemplate.convertAndSend(
                        "/topic/session/" + sessionId,
                        Map.of(
                                "type",      "AUDIO_RESPONSE",
                                "messageId", messageId.toString(),
                                "audioUrl",  audioUrl
                        )
                );
            }
        } catch (Exception e) {
            log.warn("TTS generation failed for message {} — continuing without audio", messageId, e);
        }
    }

    private int detectCrisis(String message) {
        try {
            String result = claudeApiClient.classifySync(
                    promptBuilder.getCrisisDetectionPrompt(), message, 5);
            return Integer.parseInt(result.replaceAll("[^0-9]", "").trim().substring(0, 1));
        } catch (Exception e) {
            log.warn("Crisis detection failed, defaulting to 0", e);
            return 0;
        }
    }

    private void handleCrisis(Session session, UUID sessionId, int score) {
        log.warn("CRISIS DETECTED - session {} - score {}", sessionId, score);

        persistence.flagCrisis(sessionId, score);

        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                Map.of(
                        "type",            "CRISIS_DETECTED",
                        "message",         "Estoy aquí con vos. Tu bienestar es lo más importante ahora mismo.",
                        "crisisResources", List.of(
                                "Centro de Asistencia al Suicida (CAS): 135 (gratuito, 24hs)",
                                "Línea de Salud Mental: 0800-999-0091",
                                "Emergencias: 911"
                        )
                )
        );

        releaseLock(LOCK_PREFIX + sessionId);
    }

    private void releaseLock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("Failed to release Redis lock {}", lockKey, e);
        }
    }

    private void sendStreamChunk(UUID sessionId, UUID messageId, String token, boolean isFinal) {
        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                Map.of(
                        "type",      "STREAM_CHUNK",
                        "messageId", messageId.toString(),
                        "token",     token,
                        "isFinal",   isFinal
                )
        );
    }

    private void sendStatus(UUID sessionId, String status) {
        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                Map.of("type", "STATUS", "status", status)
        );
    }

    private void sendError(UUID sessionId, String message) {
        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                Map.of("type", "ERROR", "message", message)
        );
    }
}
