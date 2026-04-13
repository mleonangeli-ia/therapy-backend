package com.therapy.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionReportRepository extends JpaRepository<SessionReport, UUID> {

    Optional<SessionReport> findBySessionId(UUID sessionId);

    List<SessionReport> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
