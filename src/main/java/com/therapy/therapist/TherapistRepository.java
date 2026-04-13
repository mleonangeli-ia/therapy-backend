package com.therapy.therapist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TherapistRepository extends JpaRepository<Therapist, UUID> {
    Optional<Therapist> findByEmail(String email);
    boolean existsByEmail(String email);
}
