package com.therapy.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therapy.claude.ClaudeApiClient;
import com.therapy.session.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionContextService {

    private final SessionContextRepository contextRepository;
    private final SessionMessageRepository messageRepository;
    private final SessionRepository sessionRepository;
    private final ClaudeApiClient claudeApiClient;
    private final TherapeuticPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public List<SessionContext> loadPreviousContexts(UUID patientId) {
        return contextRepository.findByPatientIdOrderBySessionNumber(patientId);
    }

    /**
     * Runs asynchronously after a session ends.
     * Compresses the full session transcript into a structured context object.
     */
    @Async
    @Transactional
    public void compressAndSaveSessionContext(UUID sessionId) {
        try {
            Session session = sessionRepository.findById(sessionId).orElse(null);
            if (session == null) return;

            // Already compressed?
            if (contextRepository.existsByPatientIdAndSessionNumber(
                    session.getPatient().getId(), session.getSessionNumber())) {
                log.debug("Context already exists for session {}", sessionId);
                return;
            }

            List<SessionMessage> messages =
                    messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId);

            if (messages.isEmpty()) return;

            // Build transcript for Claude
            String transcript = buildTranscript(messages);

            // Call Claude Opus for compression (not Haiku — quality matters here)
            String compressionPrompt = promptBuilder.getContextCompressionPrompt();
            String jsonResult = claudeApiClient.compressSync(compressionPrompt, transcript);

            // Parse and save
            saveContext(session, jsonResult);
            log.info("Session context compressed for session {} (patient {})",
                    sessionId, session.getPatient().getId());

        } catch (Exception e) {
            log.error("Failed to compress session context for {}", sessionId, e);
        }
    }

    private void saveContext(Session session, String jsonResult) {
        try {
            JsonNode node = objectMapper.readTree(jsonResult);
            SessionContext context = SessionContext.builder()
                    .patient(session.getPatient())
                    .pack(session.getPack())
                    .sessionNumber(session.getSessionNumber())
                    .summaryEnc(getText(node, "session_summary"))
                    .keyThemesEnc(getArray(node, "key_themes"))
                    .emotionalStateEnc(getText(node, "emotional_state"))
                    .progressNotesEnc(getText(node, "progress_notes"))
                    .therapeuticGoalsEnc(getArray(node, "therapeutic_goals"))
                    .patientVocabularyEnc(getObject(node, "patient_vocabulary"))
                    .riskFactorsEnc(getArray(node, "risk_factors"))
                    .build();
            contextRepository.save(context);
        } catch (Exception e) {
            log.error("Failed to parse compression result: {}", jsonResult, e);
            // Save raw as summary fallback
            SessionContext context = SessionContext.builder()
                    .patient(session.getPatient())
                    .pack(session.getPack())
                    .sessionNumber(session.getSessionNumber())
                    .summaryEnc(jsonResult)
                    .build();
            contextRepository.save(context);
        }
    }

    private String buildTranscript(List<SessionMessage> messages) {
        return messages.stream()
                .filter(m -> m.getRole() != SessionMessage.Role.SYSTEM)
                .filter(m -> m.getContentTextEnc() != null)
                .map(m -> {
                    String role = m.getRole() == SessionMessage.Role.PATIENT ? "PACIENTE" : "TERAPEUTA IA";
                    return role + ": " + m.getContentTextEnc();
                })
                .collect(Collectors.joining("\n\n"));
    }

    private String getText(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null && !n.isNull() ? n.asText() : null;
    }

    private String getArray(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return "[]";
        try { return objectMapper.writeValueAsString(n); } catch (Exception e) { return "[]"; }
    }

    private String getObject(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return "{}";
        try { return objectMapper.writeValueAsString(n); } catch (Exception e) { return "{}"; }
    }
}
