package com.therapy.session;

import com.therapy.pack.Pack;
import com.therapy.patient.Patient;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pack_id", nullable = false)
    private Pack pack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "session_number", nullable = false)
    private int sessionNumber;

    @Enumerated(EnumType.STRING)
    @Column
    @Builder.Default
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Enumerated(EnumType.STRING)
    @Column
    @Builder.Default
    private SessionModality modality = SessionModality.TEXT;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "title", length = 30)
    private String title;

    @Column(name = "turn_count", nullable = false)
    @Builder.Default
    private int turnCount = 0;

    @Column(name = "ai_model_used")
    private String aiModelUsed;

    @Column(name = "therapeutic_focus")
    private String therapeuticFocus;

    @Column(name = "mood_start")
    private Short moodStart;

    @Column(name = "mood_end")
    private Short moodEnd;

    @Column(name = "crisis_flag", nullable = false)
    @Builder.Default
    private boolean crisisFlag = false;

    @Column(name = "crisis_details_enc")
    private String crisisDetailsEnc;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
