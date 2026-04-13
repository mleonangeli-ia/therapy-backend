package com.therapy.session.ws;

import com.therapy.ai.AiOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SessionWebSocketController {

    private final AiOrchestratorService orchestrator;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Patient sends a text message during a session.
     * Destination: /app/session/{sessionId}/message
     * Payload: { "content": "texto del mensaje" }
     */
    @MessageMapping("/session/{sessionId}/message")
    public void handleMessage(
            @DestinationVariable String sessionId,
            @Payload Map<String, String> payload,
            Principal principal) {

        if (principal == null) {
            log.warn("Unauthenticated WS message for session {}", sessionId);
            return;
        }

        String content = payload.get("content");
        if (content == null || content.isBlank()) return;
        if (content.length() > 5000) {
            messagingTemplate.convertAndSend(
                    "/topic/session/" + sessionId,
                    Map.of("type", "ERROR", "message", "Mensaje demasiado largo (máx 5000 caracteres)"));
            return;
        }

        UUID patientId = UUID.fromString(principal.getName());
        UUID sessionUUID = UUID.fromString(sessionId);

        log.debug("WS message received - session={}, patient={}", sessionId, patientId);
        orchestrator.processPatientMessage(sessionUUID, patientId, content.trim());
    }

    /**
     * Patient signals they stopped typing (optional, for UX).
     * Destination: /app/session/{sessionId}/typing
     */
    @MessageMapping("/session/{sessionId}/typing")
    public void handleTyping(
            @DestinationVariable String sessionId,
            @Payload Map<String, Boolean> payload,
            Principal principal) {
        // Could be used for bi-directional typing indicators if needed
    }
}
