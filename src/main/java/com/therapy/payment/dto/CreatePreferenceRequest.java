package com.therapy.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreatePreferenceRequest {

    @NotNull
    private UUID packTypeId;
}
