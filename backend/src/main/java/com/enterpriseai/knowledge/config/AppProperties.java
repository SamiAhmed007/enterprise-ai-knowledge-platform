package com.enterpriseai.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Jwt jwt,
        Storage storage,
        Ai ai,
        Retrieval retrieval,
        RateLimit rateLimit,
        BootstrapAdmin bootstrapAdmin
) {
    public record Cors(List<String> allowedOriginPatterns) {}
    public record Jwt(String secret, long expirationMs) {
        public Jwt {
            if (secret == null || secret.length() < 32) {
                throw new IllegalArgumentException("JWT secret must contain at least 32 characters");
            }
            if (expirationMs < 60_000) {
                throw new IllegalArgumentException("JWT expiration must be at least 60 seconds");
            }
        }
    }
    public record Storage(String uploadDir) {}
    public record Ai(
            String provider,
            String apiKey,
            String baseUrl,
            String chatModel,
            String embeddingModel,
            int embeddingDimensions,
            String azureApiVersion
    ) {}
    public record Retrieval(
            int topK,
            double vectorWeight,
            double keywordWeight,
            double minimumScore
    ) {}
    public record RateLimit(
            int chatRequestsPerMinute,
            int documentUploadsPerHour
    ) {
        public RateLimit {
            if (chatRequestsPerMinute < 1) {
                throw new IllegalArgumentException("Chat requests per minute must be at least 1");
            }
            if (documentUploadsPerHour < 1) {
                throw new IllegalArgumentException("Document uploads per hour must be at least 1");
            }
        }
    }
    public record BootstrapAdmin(String email, String password, String name) {}
}
