package com.therapy.appointment.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class UpdateAppointmentRequest {
    private OffsetDateTime scheduledAt;
    private Integer durationMinutes;
    private String notes;
    private UUID therapistId;
}
