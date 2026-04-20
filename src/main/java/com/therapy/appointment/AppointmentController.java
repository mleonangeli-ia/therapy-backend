package com.therapy.appointment;

import com.therapy.appointment.dto.AppointmentResponse;
import com.therapy.appointment.dto.CreateAppointmentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    // ── Patient endpoints ──

    @PostMapping("/appointments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> create(
            @AuthenticationPrincipal UUID patientId,
            @Valid @RequestBody CreateAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appointmentService.create(patientId, request));
    }

    @GetMapping("/appointments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<AppointmentResponse>> myAppointments(
            @AuthenticationPrincipal UUID patientId) {
        return ResponseEntity.ok(appointmentService.getByPatient(patientId));
    }

    @PatchMapping("/appointments/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID principalId) {
        return ResponseEntity.ok(appointmentService.cancel(id, principalId));
    }

    // ── Therapist endpoints ──

    /** Appointments assigned to this therapist */
    @GetMapping("/therapist/portal/appointments")
    @PreAuthorize("hasRole('THERAPIST')")
    public ResponseEntity<List<AppointmentResponse>> therapistAppointments(
            @AuthenticationPrincipal UUID therapistId) {
        return ResponseEntity.ok(appointmentService.getByTherapist(therapistId));
    }

    /** Unassigned appointments — any therapist can see and claim */
    @GetMapping("/therapist/portal/appointments/unassigned")
    @PreAuthorize("hasRole('THERAPIST')")
    public ResponseEntity<List<AppointmentResponse>> unassignedAppointments() {
        return ResponseEntity.ok(appointmentService.getUnassigned());
    }

    /** Therapist claims an unassigned appointment */
    @PatchMapping("/therapist/portal/appointments/{id}/claim")
    @PreAuthorize("hasRole('THERAPIST')")
    public ResponseEntity<AppointmentResponse> claim(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID therapistId) {
        return ResponseEntity.ok(appointmentService.claim(id, therapistId));
    }

    @PatchMapping("/therapist/portal/appointments/{id}/confirm")
    @PreAuthorize("hasRole('THERAPIST')")
    public ResponseEntity<AppointmentResponse> confirm(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID therapistId) {
        return ResponseEntity.ok(appointmentService.confirm(id, therapistId));
    }
}
