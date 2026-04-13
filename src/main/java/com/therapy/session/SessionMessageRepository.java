package com.therapy.session;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SessionMessageRepository extends JpaRepository<SessionMessage, UUID> {

    List<SessionMessage> findBySessionIdOrderBySequenceNumberAsc(UUID sessionId);

    @Query("SELECT COUNT(m) FROM SessionMessage m WHERE m.session.id = :sessionId AND m.role = 'PATIENT'")
    int countPatientMessagesBySessionId(UUID sessionId);

    @Query("SELECT COALESCE(MAX(m.sequenceNumber), 0) FROM SessionMessage m WHERE m.session.id = :sessionId")
    int findMaxSequenceNumberBySessionId(UUID sessionId);

    /**
     * Returns the most recent N messages ordered oldest-first, suitable for AI context.
     * Bounded query prevents loading unbounded history as sessions grow long.
     */
    @Query("""
            SELECT m FROM SessionMessage m
            WHERE m.session.id = :sessionId
            ORDER BY m.sequenceNumber DESC
            LIMIT :limit
            """)
    List<SessionMessage> findTopNBySessionIdOrderBySeqDesc(@Param("sessionId") UUID sessionId,
                                                            @Param("limit") int limit);

    default List<SessionMessage> findRecentBySessionId(UUID sessionId, int limit) {
        List<SessionMessage> reversed = findTopNBySessionIdOrderBySeqDesc(sessionId, limit);
        // Re-order ascending so AI receives conversation in correct chronological order
        return reversed.reversed();
    }
}
