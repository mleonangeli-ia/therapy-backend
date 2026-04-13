package com.therapy.report.dto;

import com.therapy.session.SessionReport;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        UUID sessionId,
        int sessionNumber,
        SessionReport.Status status,
        OffsetDateTime generatedAt,
        int downloadCount,
        String errorMessage
) {}
