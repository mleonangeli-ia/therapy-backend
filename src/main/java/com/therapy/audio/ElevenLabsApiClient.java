package com.therapy.audio;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ElevenLabsApiClient {

    private static final String BASE_URL = "https://api.elevenlabs.io/v1";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String apiKey;
    private final String voiceId;

    public ElevenLabsApiClient(
            @Value("${elevenlabs.api-key}") String apiKey,
            @Value("${elevenlabs.voice-id}") String voiceId) {
        this.apiKey = apiKey;
        this.voiceId = voiceId;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Generates speech from text using ElevenLabs.
     *
     * @param text        the text to convert to speech
     * @param languageCode ISO-639 hint, e.g. "es"
     * @return raw MP3 bytes
     */
    public byte[] generateSpeech(String text, String languageCode) {
        if (text == null || text.isBlank()) return new byte[0];

        // Trim to ElevenLabs limit (5000 chars per call)
        String trimmed = text.length() > 4990 ? text.substring(0, 4990) + "..." : text;

        String jsonBody = """
                {
                  "text": %s,
                  "model_id": "eleven_multilingual_v2",
                  "voice_settings": {
                    "stability": 0.55,
                    "similarity_boost": 0.75,
                    "style": 0.15,
                    "use_speaker_boost": true
                  },
                  "language_code": "%s"
                }
                """.formatted(
                quoteJson(trimmed),
                languageCode != null ? languageCode : "es"
        );

        Request request = new Request.Builder()
                .url(BASE_URL + "/text-to-speech/" + voiceId + "/stream")
                .header("xi-api-key", apiKey)
                .header("Accept", "audio/mpeg")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("ElevenLabs API error: {}", response.code());
                return new byte[0];
            }
            return response.body().bytes();
        } catch (IOException e) {
            log.error("ElevenLabs API call failed", e);
            return new byte[0];
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("changeme");
    }

    private String quoteJson(String text) {
        // Escape text for JSON embedding
        return "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
