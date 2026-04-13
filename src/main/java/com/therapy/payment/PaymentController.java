package com.therapy.payment;

import com.therapy.payment.dto.CreatePreferenceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-preference")
    public ResponseEntity<Map<String, String>> createPreference(
            @AuthenticationPrincipal UUID patientId,
            @Valid @RequestBody CreatePreferenceRequest request) {
        Map<String, String> response = paymentService.createPreference(patientId, request.getPackTypeId());
        return ResponseEntity.ok(response);
    }

    /**
     * MercadoPago IPN webhook - must be public (no auth).
     * MP calls this asynchronously when payment status changes.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> payload,
                                         @RequestHeader(value = "X-Signature", required = false) String signature) {
        log.debug("Received MP webhook: {}", payload.get("type"));
        try {
            paymentService.processWebhook(payload, signature);
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            // Always return 200 to MP so they don't retry
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mock-purchase")
    public ResponseEntity<Map<String, String>> mockPurchase(
            @AuthenticationPrincipal UUID patientId,
            @RequestBody Map<String, String> body) {
        UUID packTypeId = UUID.fromString(body.get("packTypeId"));
        return ResponseEntity.ok(paymentService.mockPurchase(patientId, packTypeId));
    }

    @GetMapping("/status/{packId}")
    public ResponseEntity<Map<String, String>> getPaymentStatus(
            @PathVariable UUID packId,
            @AuthenticationPrincipal UUID patientId) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(packId, patientId));
    }
}
