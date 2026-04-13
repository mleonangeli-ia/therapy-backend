package com.therapy.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class WhisperApiClient {

    private static final String WHISPER_URL = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final long MAX_AUDIO_SIZE_BYTES = 25 * 1024 * 1024; // 25MB (Whisper limit)

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public WhisperApiClient(
            @Value("${groq.api-key}") String apiKey,
            @Value("${openai.whisper-model}") String model,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public record TranscriptionResult(String text, double confidence) {}

    /**
     * Transcribes audio bytes using OpenAI Whisper.
     *
     * @param audioBytes   raw audio data
     * @param mimeType     e.g. "audio/webm", "audio/mp4", "audio/wav"
     * @param language     ISO language code hint (e.g. "es"), or null for auto-detect
     * @return TranscriptionResult with text and confidence
     */
    public TranscriptionResult transcribe(byte[] audioBytes, String mimeType, String language) {
        if (audioBytes.length > MAX_AUDIO_SIZE_BYTES) {
            throw new IllegalArgumentException("Audio file too large (max 25MB)");
        }

        String extension = mimeTypeToExtension(mimeType);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("file", "audio." + extension,
                        RequestBody.create(audioBytes, MediaType.parse(mimeType)))
                .addFormDataPart("language", language != null ? language : "es")
                .build();

        Request request = new Request.Builder()
                .url(WHISPER_URL)
                .header("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("Whisper API error: {}", response.code());
                throw new RuntimeException("Whisper API error: " + response.code());
            }

            JsonNode root = objectMapper.readTree(response.body().string());
            String text = root.path("text").asText("").trim();

            // verbose_json includes per-segment confidence via no_speech_prob
            double confidence = 1.0 - root.path("segments")
                    .findValues("no_speech_prob")
                    .stream()
                    .mapToDouble(JsonNode::asDouble)
                    .average()
                    .orElse(0.0);

            log.debug("Whisper transcription: {} chars, confidence: {:.2f}", text.length(), confidence);
            return new TranscriptionResult(text, Math.max(0, Math.min(1, confidence)));

        } catch (IOException e) {
            log.error("Whisper API call failed", e);
            throw new RuntimeException("Error transcribing audio", e);
        }
    }

    private String mimeTypeToExtension(String mimeType) {
        if (mimeType == null) return "webm";
        return switch (mimeType.toLowerCase()) {
            case "audio/webm", "audio/webm;codecs=opus" -> "webm";
            case "audio/mp4", "audio/m4a"              -> "mp4";
            case "audio/wav", "audio/wave"              -> "wav";
            case "audio/ogg"                            -> "ogg";
            case "audio/mpeg", "audio/mp3"             -> "mp3";
            default -> "webm";
        };
    }
}
