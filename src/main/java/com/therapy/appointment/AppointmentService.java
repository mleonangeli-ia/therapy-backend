package com.therapy.appointment;

import com.therapy.appointment.dto.AppointmentResponse;
import com.therapy.appointment.dto.CreateAppointmentRequest;
import com.therapy.common.exception.AppException;
import com.therapy.pack.Pack;
import com.therapy.pack.PackRepository;
import com.therapy.pack.PackStatus;
import com.therapy.patient.Patient;
import com.therapy.patient.PatientRepository;
import com.therapy.therapist.TherapistRepository;
import lombok.RequiredArgsConstructor;
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

        // No therapist assigned yet — therapist claims from portal
        Appointment appointment = Appointment.builder()
                .pack(pack)
                .patient(patient)
                .therapist(null)
                .scheduledAt(req.getScheduledAt())
                .status(AppointmentStatus.PENDING)
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

    /** All unassigned appointments — visible to all therapists */
    public List<AppointmentResponse> getUnassigned() {
        return appointmentRepository.findByTherapistIsNullOrderByScheduledAtAsc()
                .stream().map(this::toResponse).toList();
    }

    /** Therapist claims an unassigned appointment */
    @Transactional
    public AppointmentResponse claim(UUID appointmentId, UUID therapistId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> AppException.notFound("Turno"));

        if (appt.getTherapist() != null) {
            throw AppException.badRequest("Este turno ya fue tomado por otro profesional");
        }

        var therapist = therapistRepository.findById(therapistId)
                .orElseThrow(() -> AppException.notFound("Terapeuta"));

        appt.setTherapist(therapist);
        appt.setStatus(AppointmentStatus.CONFIRMED);
        return toResponse(appointmentRepository.save(appt));
    }

    @Transactional
    public AppointmentResponse cancel(UUID appointmentId, UUID principalId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> AppException.notFound("Turno"));

        boolean isOwner = appt.getPatient().getId().equals(principalId)
                || (appt.getTherapist() != null && appt.getTherapist().getId().equals(principalId));
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

        if (appt.getTherapist() != null && !appt.getTherapist().getId().equals(therapistId)) {
            throw AppException.forbidden();
        }

        // Also assign therapist if not yet assigned
        if (appt.getTherapist() == null) {
            var therapist = therapistRepository.findById(therapistId)
                    .orElseThrow(() -> AppException.notFound("Terapeuta"));
            appt.setTherapist(therapist);
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
                .therapistId(a.getTherapist() != null ? a.getTherapist().getId() : null)
                .therapistName(a.getTherapist() != null ? a.getTherapist().getFullName() : null)
                .scheduledAt(a.getScheduledAt())
                .durationMinutes(a.getDurationMinutes())
                .status(a.getStatus().name())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
