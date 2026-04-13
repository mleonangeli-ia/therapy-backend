package com.therapy.patient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Patient> findByResetPasswordToken(String resetPasswordToken);
}
