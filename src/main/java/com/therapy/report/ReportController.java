package com.therapy.report;

import com.therapy.report.dto.ReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * GET /reports
     * Returns all reports for the authenticated patient.
     */
    @GetMapping
    public ResponseEntity<List<ReportResponse>> listReports(
            @AuthenticationPrincipal UUID patientId) {
        return ResponseEntity.ok(reportService.getPatientReports(patientId));
    }

    /**
     * GET /reports/{reportId}
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<ReportResponse> getReport(
            @AuthenticationPrincipal UUID patientId,
            @PathVariable UUID reportId) {
        return ResponseEntity.ok(reportService.getReport(reportId, patientId));
    }

    /**
     * POST /reports/generate/{reportId}
     * Triggers (or retries) PDF generation for the given report.
     */
    @PostMapping("/generate/{reportId}")
    public ResponseEntity<ReportResponse> generateReport(
            @AuthenticationPrincipal UUID patientId,
            @PathVariable UUID reportId) {
        return ResponseEntity.accepted().body(reportService.triggerGeneration(reportId, patientId));
    }

    /**
     * GET /reports/{reportId}/download
     * Streams the PDF bytes directly to the client (works for both local and S3 storage).
     */
    @GetMapping("/{reportId}/download")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal UUID patientId,
            @PathVariable UUID reportId) {
        byte[] pdfBytes = reportService.downloadReport(reportId, patientId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-sesion.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
