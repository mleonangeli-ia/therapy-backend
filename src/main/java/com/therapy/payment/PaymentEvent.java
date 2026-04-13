package com.therapy.payment;

import com.therapy.pack.Pack;
import com.therapy.patient.Patient;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    public enum EventType {
        PREFERENCE_CREATED,
        PAYMENT_APPROVED,
        PAYMENT_REJECTED,
        PAYMENT_PENDING,
        PAYMENT_CANCELLED,
        REFUND_REQUESTED,
        REFUND_COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pack_id", nullable = false)
    private Pack pack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "mp_payment_id")
    private String mpPaymentId;

    @Column(name = "mp_status")
    private String mpStatus;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    private String currency;

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
