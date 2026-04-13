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
@Table(name = "session_contexts",
       uniqueConstraints = @UniqueConstraint(columnNames = {"patient_id", "session_number"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContext {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pack_id", nullable = false)
    private Pack pack;

    @Column(name = "session_number", nullable = false)
    private int sessionNumber;

    @Column(name = "summary_enc", columnDefinition = "TEXT")
    private String summaryEnc;

    @Column(name = "key_themes_enc", columnDefinition = "TEXT")
    private String keyThemesEnc;

    @Column(name = "emotional_state_enc", columnDefinition = "TEXT")
    private String emotionalStateEnc;

    @Column(name = "progress_notes_enc", columnDefinition = "TEXT")
    private String progressNotesEnc;

    @Column(name = "therapeutic_goals_enc", columnDefinition = "TEXT")
    private String therapeuticGoalsEnc;

    @Column(name = "patient_vocabulary_enc", columnDefinition = "TEXT")
    private String patientVocabularyEnc;

    @Column(name = "risk_factors_enc", columnDefinition = "TEXT")
    private String riskFactorsEnc;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
