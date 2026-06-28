package com.enterpriseai.knowledge.dto;

import com.enterpriseai.knowledge.domain.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {
    private ChatDtos() {}

    public record AskRequest(
            UUID sessionId,
            @NotBlank @Size(max = 8000) String question
    ) {}

    public record Citation(
            UUID documentId,
            String documentName,
            int chunkIndex,
            Integer pageNumber,
            String excerpt,
            double score,
            double vectorScore,
            double keywordScore,
            String retrievalMethod
    ) {}

    public record AskResponse(UUID sessionId, String answer, List<Citation> citations) {}

    public record StreamDelta(String content) {}

    public record StreamSources(List<Citation> citations) {}

    public record StreamComplete(UUID sessionId, List<Citation> citations) {}

    public record StreamError(String message, String correlationId) {}

    public record MessageResponse(
            UUID id,
            MessageRole role,
            String content,
            List<Citation> citations,
            Instant createdAt
    ) {}

    public record SessionSummary(
            UUID id,
            UUID workspaceId,
            String title,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record SessionDetail(
            UUID id,
            UUID workspaceId,
            String title,
            Instant createdAt,
            Instant updatedAt,
            List<MessageResponse> messages
    ) {}
}
