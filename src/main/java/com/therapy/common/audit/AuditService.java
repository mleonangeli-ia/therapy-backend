package com.therapy.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final JdbcClient jdbcClient;

    @Async
    public void log(String eventType, UUID actorId, String resourceType, UUID resourceId,
                    String ipAddress, String userAgent) {
        try {
            jdbcClient.sql("""
                INSERT INTO audit_logs (event_type, actor_id, patient_id, resource_type, resource_id, ip_address, user_agent)
                VALUES (:eventType, :actorId, :actorId, :resourceType, :resourceId,
                        :ipAddress, :userAgent)
                """)
                    .param("eventType", eventType)
                    .param("actorId", actorId)
                    .param("resourceType", resourceType)
                    .param("resourceId", resourceId)
                    .param("ipAddress", ipAddress)
                    .param("userAgent", userAgent)
                    .update();
        } catch (Exception e) {
            log.error("Failed to write audit log for event {}", eventType, e);
        }
    }
}
