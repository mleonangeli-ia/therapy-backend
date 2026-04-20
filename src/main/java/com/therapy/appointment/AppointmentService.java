package com.therapy.appointment;

import com.therapy.appointment.dto.AppointmentResponse;
import com.therapy.appointment.dto.CreateAppointmentRequest;
import com.therapy.common.exception.AppException;
import com.therapy.pack.Pack;
import com.therapy.pack.PackRepository;
import com.therapy.pack.PackStatus;
import com.therapy.patient.Patient;
import com.therapy.patient.PatientRepository;
import com.therapy.therapist.Therapist;
import com.therapy.therapist.TherapistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PackRepository packRepository;
    private final PatientRepository patientRepository;
    private final TherapistRepository therapistRepository;

    @Transactional
    public AppointmentResponse create(UUID patientId, CreateAppointmentRequest req) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> AppException.notFound("Paciente"));

        Pack pack = packRepository.findById(req.getPackId())
                .orElseThrow(() -> AppException.notFound("Pack"));

        if (!pack.getPatient().getId().equals(patientId)) {
            throw AppException.forbidden();
        }
        if (pack.getStatus() != PackStatus.ACTIVE) {
            throw AppException.badRequest("El pack no está activo");
        }

        // Pick first active therapist (single-therapist MVP)
        Therapist therapist = therapistRepository.findAll().stream()
                .filter(Therapist::isActive)
                .findFirst()
                .orElseThrow(() -> new AppException(
                        "No hay terapeutas disponibles en este momento",
                        HttpStatus.SERVICE_UNAVAILABLE, "NO_THERAPIST"));

        Appointment appointment = Appointment.builder()
                .pack(pack)
                .patient(patient)
                .therapist(therapist)
                .scheduledAt(req.getScheduledAt())
                .notes(req.getNotes())
                .build();

        appointment = appointmentRepository.save(appointment);
        return toResponse(appointment);
    }

    public List<AppointmentResponse> getByPatient(UUID patientId) {
        return appointmentRepository.findByPatientIdOrderByScheduledAtDesc(patientId)
                .stream().map(this::toResponse).toList();
    }

    public List<AppointmentResponse> getByTherapist(UUID therapistId) {
        return appointmentRepository.findByTherapistIdOrderByScheduledAtAsc(therapistId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AppointmentResponse cancel(UUID appointmentId, UUID principalId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> AppException.notFound("Turno"));

        boolean isOwner = appt.getPatient().getId().equals(principalId)
                || appt.getTherapist().getId().equals(principalId);
        if (!isOwner) {
            throw AppException.forbidden();
        }
        if (appt.getStatus() == AppointmentStatus.CANCELLED) {
            throw AppException.badRequest("El turno ya fue cancelado");
        }

        appt.setStatus(AppointmentStatus.CANCELLED);
        return toResponse(appointmentRepository.save(appt));
    }

    @Transactional
    public AppointmentResponse confirm(UUID appointmentId, UUID therapistId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> AppException.notFound("Turno"));

        if (!appt.getTherapist().getId().equals(therapistId)) {
            throw AppException.forbidden();
        }

        appt.setStatus(AppointmentStatus.CONFIRMED);
        return toResponse(appointmentRepository.save(appt));
    }

    private AppointmentResponse toResponse(Appointment a) {
        return AppointmentResponse.builder()
                .id(a.getId())
                .packId(a.getPack().getId())
                .packName(a.getPack().getPackType().getName())
                .patientId(a.getPatient().getId())
                .patientName(a.getPatient().getFullName())
                .patientEmail(a.getPatient().getEmail())
                .therapistId(a.getTherapist().getId())
                .therapistName(a.getTherapist().getFullName())
                .scheduledAt(a.getScheduledAt())
                .durationMinutes(a.getDurationMinutes())
                .status(a.getStatus().name())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
