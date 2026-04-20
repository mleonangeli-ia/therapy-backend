package com.therapy.report;

import com.therapy.audio.StorageService;
import com.therapy.common.exception.AppException;
import com.therapy.report.dto.ReportResponse;
import com.therapy.session.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    // Reports are now transcript-only — no Claude analysis needed

    private final StorageService storageService;
    private final SessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final SessionReportRepository reportRepository;
    private final TemplateEngine templateEngine;

    /**
     * Creates a PENDING report record for a session.
     * Called automatically when a session ends (from SessionService).
     */
    @Transactional
    public SessionReport createPendingReport(Session session) {
        // Idempotent: if report already exists, return it
        return reportRepository.findBySessionId(session.getId())
                .orElseGet(() -> reportRepository.save(
                        SessionReport.builder()
                                .session(session)
                                .patient(session.getPatient())
                                .status(SessionReport.Status.PENDING)
                                .build()
                ));
    }

    /**
     * Triggers async PDF generation for a session report.
     * Can be called manually (retry) or automatically after session ends.
     */
    @Transactional
    public ReportResponse triggerGeneration(UUID reportId, UUID patientId) {
        SessionReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new AppException("Reporte no encontrado", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        if (!report.getPatient().getId().equals(patientId)) {
            throw new AppException("No autorizado", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        if (report.getStatus() == SessionReport.Status.GENERATING) {
            return toResponse(report);
        }

        report.setStatus(SessionReport.Status.GENERATING);
        report.setErrorMessage(null);
        reportRepository.save(report);

        generatePdfAsync(reportId);

        return toResponse(report);
    }

    /**
     * Trigger generation by sessionId (convenience method called after session ends).
     */
    @Transactional
    public void triggerGenerationForSession(UUID sessionId) {
        reportRepository.findBySessionId(sessionId).ifPresent(report -> {
            if (report.getStatus() == SessionReport.Status.PENDING
                    || report.getStatus() == SessionReport.Status.FAILED) {
                report.setStatus(SessionReport.Status.GENERATING);
                report.setErrorMessage(null);
                reportRepository.save(report);
                generatePdfAsync(report.getId());
            }
        });
    }

    @Async
    public void generatePdfAsync(UUID reportId) {
        try {
            SessionReport report = reportRepository.findById(reportId)
                    .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

            Session session = report.getSession();
            List<SessionMessage> messages = messageRepository
                    .findBySessionIdOrderBySequenceNumberAsc(session.getId());

            // Build transcript entries for the template
            List<Map<String, String>> transcript = buildTranscriptEntries(messages);

            // Render HTML template with Thymeleaf
            Context ctx = buildThymeleafContext(session, report, transcript);
            String html = templateEngine.process("session-report", ctx);

            // Convert HTML → PDF with Flying Saucer
            byte[] pdfBytes = renderPdf(html);

            // Store PDF
            String key = storageService.storeReport(pdfBytes, session.getPatient().getId(), session.getId());

            // Update report record
            report.setStatus(SessionReport.Status.COMPLETED);
            report.setS3Key(key);
            report.setGeneratedAt(OffsetDateTime.now());
            reportRepository.save(report);

            log.info("Report generated for session {} — {} bytes", session.getId(), pdfBytes.length);

        } catch (Exception e) {
            log.error("Report generation failed for report {}", reportId, e);
            reportRepository.findById(reportId).ifPresent(r -> {
                r.setStatus(SessionReport.Status.FAILED);
                r.setErrorMessage(e.getMessage());
                reportRepository.save(r);
            });
        }
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getPatientReports(UUID patientId) {
        return reportRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID reportId, UUID patientId) {
        SessionReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new AppException("Reporte no encontrado", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!report.getPatient().getId().equals(patientId)) {
            throw new AppException("No autorizado", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        return toResponse(report);
    }

    @Transactional
    public byte[] downloadReport(UUID reportId, UUID patientId) {
        SessionReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new AppException("Reporte no encontrado", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!report.getPatient().getId().equals(patientId)) {
            throw new AppException("No autorizado", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        if (report.getStatus() != SessionReport.Status.COMPLETED || report.getS3Key() == null) {
            throw new AppException("El reporte aún no está disponible", HttpStatus.CONFLICT, "CONFLICT");
        }

        report.setDownloadCount(report.getDownloadCount() + 1);
        reportRepository.save(report);

        return storageService.getReportBytes(report.getS3Key());
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private List<Map<String, String>> buildTranscriptEntries(List<SessionMessage> messages) {
        List<Map<String, String>> entries = new ArrayList<>();
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        for (SessionMessage msg : messages) {
            if (msg.getRole() == SessionMessage.Role.SYSTEM) continue;
            String speaker = msg.getRole() == SessionMessage.Role.PATIENT ? "Paciente" : "Terapeuta IA";
            String text = msg.getContentTextEnc() != null ? msg.getContentTextEnc() : "[audio sin transcripción]";
            String time = msg.getCreatedAt() != null ? msg.getCreatedAt().format(timeFmt) : "";
            entries.add(Map.of(
                    "role", msg.getRole().name(),
                    "speaker", speaker,
                    "text", text,
                    "time", time
            ));
        }
        return entries;
    }

    private Context buildThymeleafContext(Session session, SessionReport report, List<Map<String, String>> transcript) {
        Context ctx = new Context(new Locale("es", "AR"));
        ctx.setVariable("session", session);
        ctx.setVariable("report", report);
        ctx.setVariable("transcript", transcript);
        ctx.setVariable("messageCount", transcript.size());
        ctx.setVariable("patientName", session.getPatient().getFullName());
        ctx.setVariable("sessionNumber", session.getSessionNumber());
        ctx.setVariable("generatedAt", OffsetDateTime.now()
                .format(DateTimeFormatter.ofPattern("d 'de' MMMM yyyy 'a las' HH:mm",
                        new Locale("es", "AR"))));
        ctx.setVariable("moodStart", session.getMoodStart());
        ctx.setVariable("moodEnd", session.getMoodEnd());
        ctx.setVariable("durationMinutes", session.getDurationSeconds() != null
                ? session.getDurationSeconds() / 60 : null);
        return ctx;
    }

    private byte[] renderPdf(String html) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        }
    }

    private ReportResponse toResponse(SessionReport r) {
        return new ReportResponse(
                r.getId(),
                r.getSession().getId(),
                r.getSession().getSessionNumber(),
                r.getStatus(),
                r.getGeneratedAt(),
                r.getDownloadCount(),
                r.getErrorMessage()
        );
    }
}
