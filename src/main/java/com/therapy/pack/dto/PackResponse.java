package com.therapy.pack.dto;

import com.therapy.pack.PackStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class PackResponse {
    private UUID id;
    private PackStatus status;
    private int sessionsUsed;
    private int sessionsTotal;
    private int sessionsRemaining;
    private OffsetDateTime purchasedAt;
    private OffsetDateTime activatedAt;
    private OffsetDateTime expiresAt;
    private PackTypeResponse packType;
}
