package com.therapy.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    /**
     * Eagerly loads patient and pack so the returned entity can be used
     * outside a transaction without LazyInitializationException.
     */
    @Query("SELECT s FROM Session s JOIN FETCH s.patient JOIN FETCH s.pack WHERE s.id = :id")
    Optional<Session> findByIdEager(UUID id);

    List<Session> findByPatientIdOrderByStartedAtDesc(UUID patientId);

    @Query("SELECT s FROM Session s WHERE s.patient.id = :patientId AND s.status = 'IN_PROGRESS'")
    Optional<Session> findInProgressByPatientId(UUID patientId);

    @Query("SELECT COUNT(s) FROM Session s WHERE s.pack.id = :packId AND s.status = 'COMPLETED'")
    int countCompletedByPackId(UUID packId);

    @Query("SELECT MAX(s.sessionNumber) FROM Session s WHERE s.pack.id = :packId")
    Optional<Integer> findMaxSessionNumberByPackId(UUID packId);

    List<Session> findByPackIdOrderBySessionNumberAsc(UUID packId);
}
