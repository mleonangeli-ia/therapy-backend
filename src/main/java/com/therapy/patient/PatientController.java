package com.therapy.patient;

import com.therapy.patient.dto.ChangePasswordRequest;
import com.therapy.patient.dto.PatientProfileResponse;
import com.therapy.patient.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @GetMapping("/me")
    public ResponseEntity<PatientProfileResponse> getProfile(
            @AuthenticationPrincipal UUID patientId) {
        return ResponseEntity.ok(patientService.getProfile(patientId));
    }

    @PatchMapping("/me")
    public ResponseEntity<PatientProfileResponse> updateProfile(
            @AuthenticationPrincipal UUID patientId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(patientService.updateProfile(patientId, request));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal UUID patientId,
            @Valid @RequestBody ChangePasswordRequest request) {
        patientService.changePassword(patientId, request);
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada exitosamente"));
    }
}
