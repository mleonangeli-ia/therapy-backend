package com.therapy.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SessionContextRepository extends JpaRepository<SessionContext, UUID> {

    @Query("SELECT c FROM SessionContext c WHERE c.patient.id = :patientId ORDER BY c.sessionNumber ASC")
    List<SessionContext> findByPatientIdOrderBySessionNumber(UUID patientId);

    boolean existsByPatientIdAndSessionNumber(UUID patientId, int sessionNumber);
}
