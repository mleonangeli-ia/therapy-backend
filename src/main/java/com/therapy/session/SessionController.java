package com.therapy.session;

import com.therapy.session.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ResponseEntity<SessionResponse> startSession(
            @AuthenticationPrincipal UUID patientId,
            @Valid @RequestBody StartSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.startSession(patientId, request));
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> getMySessions(
            @AuthenticationPrincipal UUID patientId) {
        return ResponseEntity.ok(sessionService.getPatientSessions(patientId));
    }

    @GetMapping("/active")
    public ResponseEntity<SessionResponse> getActiveSession(
            @AuthenticationPrincipal UUID patientId) {
        return sessionService.getActiveSession(patientId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UUID patientId) {
        return ResponseEntity.ok(sessionService.getSession(sessionId, patientId));
    }

    @PatchMapping("/{sessionId}/title")
    public ResponseEntity<SessionResponse> updateTitle(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UUID patientId,
            @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(sessionService.updateTitle(sessionId, patientId, body.get("title")));
    }

    @PatchMapping("/{sessionId}/end")
    public ResponseEntity<SessionResponse> endSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UUID patientId,
            @Valid @RequestBody EndSessionRequest request) {
        return ResponseEntity.ok(sessionService.endSession(sessionId, patientId, request));
    }
}
