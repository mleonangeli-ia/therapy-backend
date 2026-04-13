package com.therapy.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<PaymentEvent> findByMpPaymentIdAndEventType(String mpPaymentId, PaymentEvent.EventType eventType);
}
