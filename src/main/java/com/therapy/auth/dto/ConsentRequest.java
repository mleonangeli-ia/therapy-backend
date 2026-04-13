package com.therapy.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConsentRequest {

    @NotBlank
    private String consentVersion;

    private boolean accepted;
}
