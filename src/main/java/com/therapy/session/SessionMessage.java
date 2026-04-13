package com.therapy.session;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "session_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMessage {

    public enum Role {
        PATIENT, ASSISTANT, SYSTEM
    }

    public enum ContentType {
        TEXT, AUDIO_TRANSCRIPT, AUDIO_RESPONSE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    @Builder.Default
    private ContentType contentType = ContentType.TEXT;

    @Column(name = "content_text_enc")
    private String contentTextEnc;

    @Column(name = "audio_s3_key")
    private String audioS3Key;

    @Column(name = "audio_duration_ms")
    private Integer audioDurationMs;

    @Column(name = "transcription_confidence")
    private Double transcriptionConfidence;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
