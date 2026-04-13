package com.therapy.session;

import com.therapy.ai.SessionContextService;
import com.therapy.common.exception.AppException;
import com.therapy.pack.Pack;
import com.therapy.pack.PackRepository;
import com.therapy.pack.PackService;
import com.therapy.report.ReportService;
import com.therapy.session.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final PackRepository packRepository;
    private final PackService packService;
    private final SessionContextService contextService;
    private final ReportService reportService;

    @Value("${groq.model-main}")
    private String opusModel;

    @Transactional
    public SessionResponse startSession(UUID patientId, StartSessionRequest request) {
        // Check no other session in progress
        sessionRepository.findInProgressByPatientId(patientId).ifPresent(s -> {
            throw AppException.conflict("Ya tenés una sesión en progreso. Finalizala antes de iniciar otra.");
        });

        // Consume one session from the pack (validates ownership + availability)
        Pack pack = packService.consumeSession(request.getPackId(), patientId);

        // Determine session number
        int sessionNumber = sessionRepository.findMaxSessionNumberByPackId(pack.getId())
                .orElse(0) + 1;

        Session session = Session.builder()
                .pack(pack)
                .patient(pack.getPatient())
                .sessionNumber(sessionNumber)
                .modality(request.getModality())
                .moodStart(request.getMoodStart())
                .aiModelUsed(opusModel)
                .build();

        session = sessionRepository.save(session);

        log.info("Session {} started for patient {} (pack {})",
                sessionNumber, patientId, pack.getId());

        return toResponse(session, List.of());
    }

    @Transactional
    public SessionResponse endSession(UUID sessionId, UUID patientId, EndSessionRequest request) {
        Session session = getOwnedSession(sessionId, patientId);

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            // Already completed — return current state (idempotent, handles double-submit/retry)
            List<SessionMessage> messages =
                    messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId);
            return toResponse(session, messages);
        }

        session.setStatus(SessionStatus.COMPLETED);
        session.setEndedAt(OffsetDateTime.now());
        session.setMoodEnd(request.getMoodEnd());

        if (session.getStartedAt() != null) {
            session.setDurationSeconds(
                    (int) ChronoUnit.SECONDS.between(session.getStartedAt(), session.getEndedAt()));
        }

        session = sessionRepository.save(session);

        // Trigger async context compression
        contextService.compressAndSaveSessionContext(sessionId);

        // Create pending report and trigger async PDF generation
        reportService.createPendingReport(session);
        reportService.triggerGenerationForSession(sessionId);

        log.info("Session {} ended for patient {}", sessionId, patientId);
        return toResponse(session, List.of());
    }

    public SessionResponse getSession(UUID sessionId, UUID patientId) {
        Session session = getOwnedSession(sessionId, patientId);
        List<SessionMessage> messages =
                messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId);
        return toResponse(session, messages);
    }

    public List<SessionResponse> getPatientSessions(UUID patientId) {
        return sessionRepository.findByPatientIdOrderByStartedAtDesc(patientId)
                .stream()
                .map(s -> toResponse(s, List.of()))
                .toList();
    }

    public java.util.Optional<SessionResponse> getActiveSession(UUID patientId) {
        return sessionRepository.findInProgressByPatientId(patientId)
                .map(s -> {
                    List<SessionMessage> messages =
                            messageRepository.findBySessionIdOrderBySequenceNumberAsc(s.getId());
                    return toResponse(s, messages);
                });
    }

    @Transactional
    public SessionResponse updateTitle(UUID sessionId, UUID patientId, String title) {
        Session session = getOwnedSession(sessionId, patientId);
        session.setTitle(title == null || title.isBlank() ? null : title.strip());
        session = sessionRepository.save(session);
        return toResponse(session, List.of());
    }

    private Session getOwnedSession(UUID sessionId, UUID patientId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("Sesión"));
        if (!session.getPatient().getId().equals(patientId)) {
            throw AppException.forbidden();
        }
        return session;
    }

    public SessionResponse toResponse(Session session, List<SessionMessage> messages) {
        return SessionResponse.builder()
                .id(session.getId())
                .sessionNumber(session.getSessionNumber())
                .title(session.getTitle())
                .status(session.getStatus())
                .modality(session.getModality())
                .moodStart(session.getMoodStart())
                .moodEnd(session.getMoodEnd())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .turnCount(session.getTurnCount())
                .crisisFlag(session.isCrisisFlag())
                .messages(messages.stream().map(this::toMessageResponse).toList())
                .build();
    }

    MessageResponse toMessageResponse(SessionMessage msg) {
        return MessageResponse.builder()
                .id(msg.getId())
                .role(msg.getRole())
                .contentType(msg.getContentType())
                .contentText(msg.getContentTextEnc())
                .sequenceNumber(msg.getSequenceNumber())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}
