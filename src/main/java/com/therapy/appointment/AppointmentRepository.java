package com.therapy.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByPatientIdOrderByScheduledAtDesc(UUID patientId);

    List<Appointment> findByTherapistIdOrderByScheduledAtAsc(UUID therapistId);

    List<Appointment> findByTherapistIdAndScheduledAtBetween(
            UUID therapistId, OffsetDateTime from, OffsetDateTime to);

    List<Appointment> findByPackId(UUID packId);
}
