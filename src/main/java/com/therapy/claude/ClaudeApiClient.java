package com.therapy.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ClaudeApiClient {

    private static final String BASE_URL = "https://api.groq.com/openai/v1";
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String mainModel;
    private final String fastModel;

    public ClaudeApiClient(
            @Value("${groq.api-key}") String apiKey,
            @Value("${groq.model-main}") String mainModel,
            @Value("${groq.model-fast}") String fastModel,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.mainModel = mainModel;
        this.fastModel = fastModel;
        this.objectMapper = objectMapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Streaming call to Groq for therapeutic responses.
     * Filters out <think>...</think> reasoning blocks emitted by reasoning models
     * like qwen-qwq-32b before forwarding tokens to the caller.
     */
    public void streamTherapeuticResponse(
            String systemPrompt,
            List<ClaudeMessage> messages,
            int maxTokens,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Exception> onError) {

        try {
            Map<String, Object> requestBody = buildRequest(systemPrompt, messages, maxTokens, 0.7, true);
            String body = objectMapper.writeValueAsString(requestBody);

            Request httpRequest = new Request.Builder()
                    .url(BASE_URL + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("content-type", "application/json")
                    .post(RequestBody.create(body, JSON))
                    .build();

            client.newCall(httpRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("Groq streaming request failed", e);
                    onError.accept(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        String err = "Groq API error: " + response.code();
                        log.error(err);
                        onError.accept(new RuntimeException(err));
                        return;
                    }
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody == null) {
                            onComplete.run();
                            return;
                        }
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(responseBody.byteStream()));
                        // Only apply thinking filter for models that emit <think> blocks
                        boolean needsFilter = mainModel.contains("qwen") || mainModel.contains("qwq");
                        ThinkingFilter filter = needsFilter ? new ThinkingFilter() : null;
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6).trim();
                                if ("[DONE]".equals(data)) break;
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> event = objectMapper.readValue(data, Map.class);
                                    String text = extractStreamText(event);
                                    if (text != null && !text.isEmpty()) {
                                        if (filter != null) {
                                            String filtered = filter.process(text);
                                            if (!filtered.isEmpty()) onToken.accept(filtered);
                                        } else {
                                            onToken.accept(text);
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                        // Flush any remaining chars from the thinking filter buffer
                        if (filter != null) {
                            String remaining = filter.flush();
                            if (!remaining.isEmpty()) onToken.accept(remaining);
                        }
                        onComplete.run();
                    } catch (IOException e) {
                        log.error("Error reading Groq stream", e);
                        onError.accept(e);
                    }
                }
            });
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    /**
     * Stateful filter that suppresses tokens inside <think>...</think> blocks.
     * Handles the case where tags are split across multiple SSE tokens.
     */
    private static class ThinkingFilter {
        private boolean insideThink = false;
        // Buffer to detect partial opening/closing tags split across tokens
        private final StringBuilder tagBuffer = new StringBuilder();

        String process(String token) {
            StringBuilder output = new StringBuilder();
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                tagBuffer.append(c);

                if (!insideThink) {
                    // Check if tagBuffer ends with <think>
                    if (tagBuffer.toString().endsWith("<think>")) {
                        // Discard everything from <think> onward in the buffer
                        String buffered = tagBuffer.toString();
                        String before = buffered.substring(0, buffered.length() - "<think>".length());
                        output.append(before);
                        tagBuffer.setLength(0);
                        insideThink = true;
                    } else if (tagBuffer.length() > 10) {
                        // Safe to flush: <think> is 7 chars, keep 7 in buffer as lookahead
                        int flush = tagBuffer.length() - 7;
                        output.append(tagBuffer, 0, flush);
                        tagBuffer.delete(0, flush);
                    }
                } else {
                    // Inside <think>: check if tagBuffer ends with </think>
                    if (tagBuffer.toString().endsWith("</think>")) {
                        tagBuffer.setLength(0);
                        insideThink = false;
                    } else if (tagBuffer.length() > 15) {
                        // Discard safely, keep 8 chars lookahead for </think>
                        tagBuffer.delete(0, tagBuffer.length() - 8);
                    }
                }
            }

            // If not inside a think block, flush remaining safe buffer content
            if (!insideThink && tagBuffer.length() > 7) {
                int flush = tagBuffer.length() - 7;
                output.append(tagBuffer, 0, flush);
                tagBuffer.delete(0, flush);
            }

            return output.toString();
        }

        /** Flush all remaining buffered content at end of stream. */
        String flush() {
            if (!insideThink && tagBuffer.length() > 0) {
                String remaining = tagBuffer.toString();
                tagBuffer.setLength(0);
                return remaining;
            }
            tagBuffer.setLength(0);
            return "";
        }
    }

    /**
     * Synchronous call for fast classification (crisis detection).
     */
    public String classifySync(String systemPrompt, String userMessage, int maxTokens) {
        try {
            Map<String, Object> requestBody = buildRequest(
                    systemPrompt,
                    List.of(ClaudeMessage.user(userMessage)),
                    maxTokens,
                    0.0,
                    false);
            String body = objectMapper.writeValueAsString(requestBody);

            Request httpRequest = new Request.Builder()
                    .url(BASE_URL + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("content-type", "application/json")
                    .post(RequestBody.create(body, JSON))
                    .build();

            try (Response response = client.newCall(httpRequest).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("Groq classification failed: {}", response.code());
                    return "0";
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = objectMapper.readValue(response.body().string(), Map.class);
                String text = extractSyncText(responseMap);
                return text != null ? text.trim() : "0";
            }
        } catch (Exception e) {
            log.error("Groq classification error", e);
            return "0";
        }
    }

    /**
     * Synchronous call for post-session context compression.
     */
    public String compressSync(String systemPrompt, String content) {
        return classifySync(systemPrompt, content, 2048);
    }

    private Map<String, Object> buildRequest(String systemPrompt, List<ClaudeMessage> messages,
                                              int maxTokens, double temperature, boolean stream) {
        List<Map<String, Object>> msgs = new java.util.ArrayList<>();
        msgs.add(Map.of("role", "system", "content", systemPrompt));
        msgs.addAll(messages.stream()
                .map(m -> Map.<String, Object>of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList()));

        return Map.of(
                "model", stream ? mainModel : fastModel,
                "messages", msgs,
                "max_tokens", maxTokens,
                "temperature", temperature,
                "stream", stream
        );
    }

    @SuppressWarnings("unchecked")
    private String extractStreamText(Map<String, Object> event) {
        try {
            List<?> choices = (List<?>) event.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            Map<?, ?> delta = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("delta");
            if (delta == null) return null;
            return (String) delta.get("content");
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractSyncText(Map<String, Object> response) {
        try {
            List<?> choices = (List<?>) response.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            if (message == null) return null;
            return (String) message.get("content");
        } catch (Exception e) {
            return null;
        }
    }
}
