package com.enterpriseai.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class WorkspaceDtos {
    private WorkspaceDtos() {}

    public record CreateWorkspaceRequest(
            @NotBlank @Size(max = 120) String name
    ) {}

    public record WorkspaceResponse(
            UUID id,
            String name,
            UUID ownerId,
            String ownerName,
            String ownerEmail,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
