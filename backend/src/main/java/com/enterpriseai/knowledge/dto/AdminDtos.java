package com.enterpriseai.knowledge.dto;

import com.enterpriseai.knowledge.domain.DocumentStatus;
import com.enterpriseai.knowledge.domain.Role;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}

    public record DashboardStats(
            long users,
            long workspaces,
            long documents,
            long readyDocuments,
            long chatSessions
    )
            implements Serializable {}

    public record AnalyticsOverview(
            long totalUsers,
            long totalWorkspaces,
            long totalDocuments,
            Map<DocumentStatus, Long> documentsByStatus,
            long totalChatSessions,
            List<RecentActivity> recentActivity,
            List<FailedIngestion> failedIngestions,
            TokenUsage tokenUsage
    ) {}

    public record RecentActivity(
            String type,
            String description,
            String actorName,
            String actorEmail,
            Instant occurredAt
    ) {}

    public record FailedIngestion(
            UUID documentId,
            String documentName,
            String workspaceName,
            String ownerEmail,
            String errorMessage,
            Instant failedAt
    ) {}

    public record TokenUsage(
            long embeddingTokens,
            long chatTokens,
            long totalTokens,
            boolean approximate
    ) {}

    public record AdminUser(UUID id, String name, String email, Role role, Instant createdAt) {}
}
