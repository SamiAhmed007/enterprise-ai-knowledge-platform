package com.enterpriseai.knowledge.dto;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        Instant timestamp,
        Map<String, String> validationErrors
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(
                status,
                error,
                message,
                path,
                org.slf4j.MDC.get("correlationId"),
                Instant.now(),
                Map.of());
    }
}
