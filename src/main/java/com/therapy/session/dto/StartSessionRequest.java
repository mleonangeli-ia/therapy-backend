package com.therapy.session.dto;

import com.therapy.session.SessionModality;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class StartSessionRequest {

    @NotNull
    private UUID packId;

    @NotNull
    private SessionModality modality;

    @Min(1) @Max(10)
    private Short moodStart;
}
