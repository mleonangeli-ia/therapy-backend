package com.therapy.pack;

import com.therapy.patient.Patient;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "packs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pack {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pack_type_id", nullable = false)
    private PackType packType;

    @Enumerated(EnumType.STRING)
    @Column
    @Builder.Default
    private PackStatus status = PackStatus.PENDING_PAYMENT;

    @Column(name = "sessions_used", nullable = false)
    @Builder.Default
    private int sessionsUsed = 0;

    @Column(name = "sessions_total", nullable = false)
    @Builder.Default
    private int sessionsTotal = 10;

    @Column(name = "purchased_at")
    private OffsetDateTime purchasedAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "mp_preference_id")
    private String mpPreferenceId;

    @Column(name = "mp_payment_id")
    private String mpPaymentId;

    @Column(name = "mp_payment_status")
    private String mpPaymentStatus;

    @Column(name = "mp_external_ref")
    private String mpExternalRef;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public int getSessionsRemaining() {
        return sessionsTotal - sessionsUsed;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(OffsetDateTime.now());
    }

    public boolean canStartSession() {
        return status == PackStatus.ACTIVE
                && getSessionsRemaining() > 0
                && !isExpired();
    }
}
