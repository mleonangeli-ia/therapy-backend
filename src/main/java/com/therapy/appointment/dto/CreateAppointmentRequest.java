package com.therapy.appointment.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class CreateAppointmentRequest {
    @NotNull
    private UUID packId;

    @NotNull
    @Future
    private OffsetDateTime scheduledAt;

    private String notes;
}
