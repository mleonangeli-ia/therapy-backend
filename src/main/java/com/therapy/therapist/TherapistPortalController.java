package com.therapy.therapist;

import com.therapy.patient.Patient;
import com.therapy.patient.PatientRepository;
import com.therapy.session.*;
import com.therapy.session.dto.MessageResponse;
import com.therapy.session.dto.SessionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/therapist/portal")
@RequiredArgsConstructor
@PreAuthorize("hasRole('THERAPIST')")
public class TherapistPortalController {

    private final PatientRepository patientRepository;
    private final SessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final SessionService sessionService;

    @GetMapping("/patients")
    public ResponseEntity<List<PatientSummary>> getAllPatients() {
        List<PatientSummary> patients = patientRepository.findAll().stream()
                .map(p -> new PatientSummary(
                        p.getId(),
                        p.getFullName(),
                        p.getEmail(),
                        p.getCreatedAt().toString(),
                        p.isActive()
                ))
                .toList();
        return ResponseEntity.ok(patients);
    }

    @GetMapping("/patients/{patientId}/sessions")
    public ResponseEntity<List<SessionResponse>> getPatientSessions(@PathVariable UUID patientId) {
        List<SessionResponse> sessions = sessionRepository
                .findByPatientIdOrderByStartedAtDesc(patientId)
                .stream()
                .map(s -> sessionService.toResponse(s, List.of()))
                .toList();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<MessageResponse>> getSessionMessages(@PathVariable UUID sessionId) {
        List<MessageResponse> messages = messageRepository
                .findBySessionIdOrderBySequenceNumberAsc(sessionId)
                .stream()
                .map(m -> MessageResponse.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .contentType(m.getContentType())
                        .contentText(m.getContentTextEnc())
                        .sequenceNumber(m.getSequenceNumber())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();
        return ResponseEntity.ok(messages);
    }

    public record PatientSummary(UUID id, String fullName, String email, String createdAt, boolean isActive) {}
}
