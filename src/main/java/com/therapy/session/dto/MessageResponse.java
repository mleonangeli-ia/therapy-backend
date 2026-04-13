package com.therapy.session.dto;

import com.therapy.session.SessionMessage;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class MessageResponse {
    private UUID id;
    private SessionMessage.Role role;
    private SessionMessage.ContentType contentType;
    private String contentText;
    private int sequenceNumber;
    private OffsetDateTime createdAt;
}
