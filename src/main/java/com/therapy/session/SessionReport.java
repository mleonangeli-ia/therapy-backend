package com.therapy.session;

import com.therapy.patient.Patient;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "session_reports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionReport {

    public enum Status {
        PENDING, GENERATING, COMPLETED, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "s3_key")
    private String s3Key;

    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private int downloadCount = 0;

    @Column(name = "report_data_enc", columnDefinition = "TEXT")
    private String reportDataEnc;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
