package com.therapy.patient;

import com.therapy.common.exception.AppException;
import com.therapy.patient.dto.ChangePasswordRequest;
import com.therapy.patient.dto.PatientProfileResponse;
import com.therapy.patient.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;

    public PatientProfileResponse getProfile(UUID patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> AppException.notFound("Patient"));
        return PatientProfileResponse.from(patient);
    }

    @Transactional
    public PatientProfileResponse updateProfile(UUID patientId, UpdateProfileRequest request) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> AppException.notFound("Patient"));

        patient.setFullName(request.getFullName().trim());
        patient.setPhone(request.getPhone() == null || request.getPhone().isBlank()
                ? null : request.getPhone().trim());

        return PatientProfileResponse.from(patientRepository.save(patient));
    }

    @Transactional
    public void changePassword(UUID patientId, ChangePasswordRequest request) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> AppException.notFound("Patient"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), patient.getPasswordHash())) {
            throw AppException.badRequest("La contraseña actual es incorrecta");
        }

        if (passwordEncoder.matches(request.getNewPassword(), patient.getPasswordHash())) {
            throw AppException.badRequest("La nueva contraseña no puede ser igual a la actual");
        }

        patient.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        patientRepository.save(patient);
    }
}
