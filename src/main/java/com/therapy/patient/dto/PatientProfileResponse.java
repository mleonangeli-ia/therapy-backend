package com.therapy.patient.dto;

import com.therapy.patient.Patient;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PatientProfileResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        String countryCode,
        String timezone,
        String language,
        OffsetDateTime createdAt
) {
    public static PatientProfileResponse from(Patient p) {
        return PatientProfileResponse.builder()
                .id(p.getId())
                .email(p.getEmail())
                .fullName(p.getFullName())
                .phone(p.getPhone())
                .countryCode(p.getCountryCode())
                .timezone(p.getTimezone())
                .language(p.getLanguage())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
