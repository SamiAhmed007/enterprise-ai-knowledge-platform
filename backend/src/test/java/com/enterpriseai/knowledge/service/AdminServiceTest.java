package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.domain.DocumentStatus;
import com.enterpriseai.knowledge.dto.AdminDtos.TokenUsage;
import com.enterpriseai.knowledge.repository.AdminAnalyticsRepository;
import com.enterpriseai.knowledge.repository.ChatSessionRepository;
import com.enterpriseai.knowledge.repository.DocumentRepository;
import com.enterpriseai.knowledge.repository.UserRepository;
import com.enterpriseai.knowledge.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminServiceTest {
    @Test
    void analyticsCombinesPlatformCountsStatusesActivityFailuresAndTokens() {
        UserRepository users = mock(UserRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        DocumentRepository documents = mock(DocumentRepository.class);
        ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        AdminAnalyticsRepository analytics = mock(AdminAnalyticsRepository.class);
        EnumMap<DocumentStatus, Long> statuses = new EnumMap<>(DocumentStatus.class);
        for (DocumentStatus status : DocumentStatus.values()) statuses.put(status, 0L);
        statuses.put(DocumentStatus.READY, 8L);
        statuses.put(DocumentStatus.FAILED, 2L);
        when(users.count()).thenReturn(4L);
        when(workspaces.count()).thenReturn(6L);
        when(documents.count()).thenReturn(10L);
        when(sessions.count()).thenReturn(12L);
        when(analytics.documentStatusCounts()).thenReturn(statuses);
        when(analytics.recentActivity()).thenReturn(List.of());
        when(analytics.failedIngestions()).thenReturn(List.of());
        when(analytics.tokenUsage()).thenReturn(new TokenUsage(1_000, 500, 1_500, true));
        AdminService service = new AdminService(
                users,
                documents,
                sessions,
                mock(DocumentService.class),
                workspaces,
                mock(WorkspaceService.class),
                analytics);

        var result = service.analytics();

        assertThat(result.totalUsers()).isEqualTo(4);
        assertThat(result.totalDocuments()).isEqualTo(10);
        assertThat(result.documentsByStatus().get(DocumentStatus.FAILED)).isEqualTo(2);
        assertThat(result.tokenUsage().totalTokens()).isEqualTo(1_500);
    }
}
