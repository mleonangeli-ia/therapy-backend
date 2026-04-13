package com.therapy.audio.dto;

import java.util.UUID;

public record AudioUploadResponse(
        UUID messageId,
        String transcription,
        double confidence
) {}
