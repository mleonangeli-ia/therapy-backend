package com.therapy.pack;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PackRepository extends JpaRepository<Pack, UUID> {

    List<Pack> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    @Query("SELECT p FROM Pack p WHERE p.patient.id = :patientId AND p.status = 'ACTIVE' ORDER BY p.activatedAt DESC LIMIT 1")
    Optional<Pack> findActivePackByPatientId(UUID patientId);

    Optional<Pack> findByMpExternalRef(String mpExternalRef);

    Optional<Pack> findByMpPaymentId(String mpPaymentId);
}
