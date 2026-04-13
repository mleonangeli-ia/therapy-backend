package com.therapy.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles all DB writes during a session as short, independent transactions.
 * This decouples database connections from long-running AI streaming calls,
 * allowing the connection pool to be used by other requests while streaming.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionPersistenceService {

    private static final int MAX_HISTORY_MESSAGES = 20;

    private final SessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;

    /**
     * Loads and validates the session in a short read-only transaction.
     * Returns a detached entity — safe to use outside a transaction.
     */
    @Transactional(readOnly = true)
    public Session loadAndValidate(UUID sessionId, UUID patientId) {
        // JOIN FETCH patient + pack so the detached entity can be read outside a transaction
        Session session = sessionRepository.findByIdEager(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada"));

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("La sesión ya no está activa");
        }
        if (!session.getPatient().getId().equals(patientId)) {
            throw new SecurityException("No autorizado");
        }
        return session;
    }

    /**
     * Saves the patient's message in its own short transaction.
     * DB connection is acquired and released immediately after this call.
     */
    @Transactional
    public SessionMessage savePatientMessage(UUID sessionId, String content) {
        int nextSeq = messageRepository.findMaxSequenceNumberBySessionId(sessionId) + 1;
        Session sessionRef = sessionRepository.getReferenceById(sessionId);

        SessionMessage msg = SessionMessage.builder()
                .session(sessionRef)
                .role(SessionMessage.Role.PATIENT)
                .contentType(SessionMessage.ContentType.TEXT)
                .contentTextEnc(content)
                .sequenceNumber(nextSeq)
                .build();

        return messageRepository.save(msg);
    }

    /**
     * Saves the AI response in its own short transaction.
     * Called only after streaming completes.
     */
    @Transactional
    public SessionMessage saveAiMessage(UUID sessionId, SessionModality modality, String content) {
        int nextSeq = messageRepository.findMaxSequenceNumberBySessionId(sessionId) + 1;
        Session sessionRef = sessionRepository.getReferenceById(sessionId);

        SessionMessage.ContentType contentType = modality == SessionModality.TEXT
                ? SessionMessage.ContentType.TEXT
                : SessionMessage.ContentType.AUDIO_RESPONSE;

        SessionMessage msg = SessionMessage.builder()
                .session(sessionRef)
                .role(SessionMessage.Role.ASSISTANT)
                .contentType(contentType)
                .contentTextEnc(content)
                .sequenceNumber(nextSeq)
                .build();

        return messageRepository.save(msg);
    }

    /**
     * Increments the session turn count in its own short transaction.
     */
    @Transactional
    public void incrementTurnCount(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(s -> {
            s.setTurnCount(s.getTurnCount() + 1);
            sessionRepository.save(s);
        });
    }

    /**
     * Flags a session as a crisis in its own short transaction.
     */
    @Transactional
    public void flagCrisis(UUID sessionId, int score) {
        sessionRepository.findById(sessionId).ifPresent(s -> {
            s.setCrisisFlag(true);
            s.setCrisisDetailsEnc("Crisis score: " + score);
            sessionRepository.save(s);
        });
    }

    /**
     * Loads the most recent N messages for context building.
     * Keeping history bounded prevents unbounded DB reads as sessions grow long.
     */
    @Transactional(readOnly = true)
    public List<SessionMessage> loadRecentHistory(UUID sessionId) {
        return messageRepository.findRecentBySessionId(sessionId, MAX_HISTORY_MESSAGES);
    }
}
