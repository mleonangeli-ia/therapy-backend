package com.therapy.appointment.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class AppointmentResponse {
    private UUID id;
    private UUID packId;
    private String packName;
    private UUID patientId;
    private String patientName;
    private String patientEmail;
    private UUID therapistId;
    private String therapistName;
    private OffsetDateTime scheduledAt;
    private int durationMinutes;
    private String status;
    private String notes;
    private OffsetDateTime createdAt;
}
