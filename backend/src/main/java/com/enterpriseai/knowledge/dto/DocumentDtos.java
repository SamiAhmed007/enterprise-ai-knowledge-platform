package com.enterpriseai.knowledge.dto;

import com.enterpriseai.knowledge.domain.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

public final class DocumentDtos {
    private DocumentDtos() {}

    public record DocumentResponse(
            UUID id,
            String name,
            String contentType,
            long sizeBytes,
            DocumentStatus status,
            String errorMessage,
            Instant createdAt,
            Instant processedAt,
            UUID workspaceId,
            String workspaceName,
            String ownerName,
            String ownerEmail
    ) {}
}
