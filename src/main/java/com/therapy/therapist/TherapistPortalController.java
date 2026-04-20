package com.therapy.therapist;

import com.therapy.patient.Patient;
import com.therapy.patient.PatientRepository;
import com.therapy.report.ReportService;
import com.therapy.session.*;
import com.therapy.session.dto.MessageResponse;
import com.therapy.session.dto.SessionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.OptionalDouble;
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
    private final ReportService reportService;
    private final TherapistRepository therapistRepository;

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

    @GetMapping("/stats")
    public ResponseEntity<PlatformStats> getStats() {
        List<Session> allSessions = sessionRepository.findAll();
        List<Patient> allPatients = patientRepository.findAll();

        long totalPatients  = allPatients.size();
        long totalSessions  = allSessions.size();
        long completed      = allSessions.stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).count();
        long inProgress     = allSessions.stream().filter(s -> s.getStatus() == SessionStatus.IN_PROGRESS).count();
        long abandoned      = allSessions.stream().filter(s -> s.getStatus() == SessionStatus.ABANDONED).count();
        long crisisCount    = allSessions.stream().filter(Session::isCrisisFlag).count();

        OptionalDouble avgDuration = allSessions.stream()
                .filter(s -> s.getDurationSeconds() != null)
                .mapToInt(Session::getDurationSeconds)
                .average();

        OptionalDouble avgMoodStart = allSessions.stream()
                .filter(s -> s.getMoodStart() != null)
                .mapToInt(Session::getMoodStart)
                .average();

        OptionalDouble avgMoodEnd = allSessions.stream()
                .filter(s -> s.getMoodEnd() != null)
                .mapToInt(Session::getMoodEnd)
                .average();

        long totalTurns = allSessions.stream().mapToLong(Session::getTurnCount).sum();

        return ResponseEntity.ok(new PlatformStats(
                totalPatients,
                totalSessions,
                completed,
                inProgress,
                abandoned,
                crisisCount,
                avgDuration.isPresent() ? Math.round(avgDuration.getAsDouble()) : null,
                avgMoodStart.isPresent() ? Math.round(avgMoodStart.getAsDouble() * 10.0) / 10.0 : null,
                avgMoodEnd.isPresent() ? Math.round(avgMoodEnd.getAsDouble() * 10.0) / 10.0 : null,
                totalTurns
        ));
    }

    @GetMapping("/sessions/{sessionId}/clinical-report")
    public ResponseEntity<java.util.Map<String, Object>> getClinicalReport(@PathVariable UUID sessionId) {
        java.util.Map<String, Object> data = reportService.getClinicalAnalysis(sessionId);
        if (data == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/patients/countries")
    public ResponseEntity<List<CountryStat>> getPatientsByCountry() {
        List<CountryStat> stats = patientRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        p -> p.getCountryCode() != null ? p.getCountryCode() : "AR",
                        java.util.stream.Collectors.counting()
                ))
                .entrySet().stream()
                .map(e -> new CountryStat(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .toList();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/therapists")
    public ResponseEntity<List<TherapistSummary>> listTherapists() {
        List<TherapistSummary> list = therapistRepository.findAll().stream()
                .filter(Therapist::isActive)
                .filter(t -> t.getLicenseNumber() != null && !t.getLicenseNumber().isBlank())
                .map(t -> new TherapistSummary(t.getId(), t.getFullName(), t.getLicenseNumber()))
                .toList();
        return ResponseEntity.ok(list);
    }

    public record TherapistSummary(UUID id, String fullName, String licenseNumber) {}

    public record CountryStat(String countryCode, long count) {}

    public record PlatformStats(
            long totalPatients,
            long totalSessions,
            long completedSessions,
            long inProgressSessions,
            long abandonedSessions,
            long crisisSessions,
            Long avgDurationSeconds,
            Double avgMoodStart,
            Double avgMoodEnd,
            long totalTurns
    ) {}

    public record PatientSummary(UUID id, String fullName, String email, String createdAt, boolean isActive) {}
}
