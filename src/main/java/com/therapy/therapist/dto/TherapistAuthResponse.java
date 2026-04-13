package com.therapy.therapist.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TherapistAuthResponse {
    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private UUID therapistId;
    private String fullName;
    private String email;
}
