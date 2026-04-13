package com.therapy.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank
    @Size(min = 2, max = 255)
    private String fullName;

    @Size(max = 50)
    private String phone;
}
