package com.therapy.claude;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeMessage {
    private String role;
    private String content;

    public static ClaudeMessage user(String content) {
        return ClaudeMessage.builder().role("user").content(content).build();
    }

    public static ClaudeMessage assistant(String content) {
        return ClaudeMessage.builder().role("assistant").content(content).build();
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Request {
        private String model;
        private int max_tokens;
        private String system;
        private List<ClaudeMessage> messages;
        private boolean stream;
        private double temperature;
    }

    @Data
    public static class StreamEvent {
        private String type;
        private Delta delta;
        private Usage usage;

        @Data
        public static class Delta {
            private String type;
            private String text;
        }

        @Data
        public static class Usage {
            private int input_tokens;
            private int output_tokens;
        }
    }

    @Data
    public static class SyncResponse {
        private String id;
        private String model;
        private List<ContentBlock> content;
        private Usage usage;

        @Data
        public static class ContentBlock {
            private String type;
            private String text;
        }

        @Data
        public static class Usage {
            private int input_tokens;
            private int output_tokens;
        }

        public String getFirstText() {
            if (content == null || content.isEmpty()) return "";
            return content.stream()
                    .filter(b -> "text".equals(b.getType()))
                    .map(ContentBlock::getText)
                    .findFirst()
                    .orElse("");
        }
    }
}
