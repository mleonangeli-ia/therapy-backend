package com.therapy.session.dto;

import com.therapy.session.SessionModality;
import com.therapy.session.SessionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class SessionResponse {
    private UUID id;
    private int sessionNumber;
    private String title;
    private SessionStatus status;
    private SessionModality modality;
    private Short moodStart;
    private Short moodEnd;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private int turnCount;
    private boolean crisisFlag;
    private List<MessageResponse> messages;
}
