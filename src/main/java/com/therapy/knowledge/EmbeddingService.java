package com.therapy.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Generates text embeddings via the OpenAI Embeddings API.
 * Uses text-embedding-3-small (1536 dimensions) — cheap and effective for RAG.
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final String EMBEDDINGS_URL = "http://localhost:8090/v1/embeddings";
    private static final String MODEL = "paraphrase-multilingual-mpnet-base-v2";
    private static final MediaType JSON_TYPE = MediaType.get("application/json");

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public EmbeddingService(
            @Value("${openai.api-key}") String apiKey,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Generate an embedding for a single text input.
     * Returns the raw float array (1536 dimensions).
     */
    public float[] embed(String text) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", MODEL,
                    "input", text
            ));

            Request.Builder builder = new Request.Builder()
                    .url(EMBEDDINGS_URL)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body, JSON_TYPE));
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            Request request = builder.build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new RuntimeException("OpenAI Embeddings API error: " + response.code());
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = objectMapper.readValue(response.body().string(), Map.class);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");

                @SuppressWarnings("unchecked")
                List<Number> embeddingList = (List<Number>) data.get(0).get("embedding");

                float[] embedding = new float[embeddingList.size()];
                for (int i = 0; i < embeddingList.size(); i++) {
                    embedding[i] = embeddingList.get(i).floatValue();
                }
                return embedding;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    /**
     * Convert a float array to pgvector's string format: [0.1,0.2,...,0.3]
     */
    public String toPgVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
