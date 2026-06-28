package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.domain.DocumentStatus;
import com.enterpriseai.knowledge.dto.AdminDtos.AnalyticsOverview;
import com.enterpriseai.knowledge.dto.AdminDtos.AdminUser;
import com.enterpriseai.knowledge.dto.AdminDtos.DashboardStats;
import com.enterpriseai.knowledge.dto.DocumentDtos.DocumentResponse;
import com.enterpriseai.knowledge.repository.AdminAnalyticsRepository;
import com.enterpriseai.knowledge.repository.ChatSessionRepository;
import com.enterpriseai.knowledge.repository.DocumentRepository;
import com.enterpriseai.knowledge.repository.UserRepository;
import com.enterpriseai.knowledge.repository.WorkspaceRepository;
import com.enterpriseai.knowledge.dto.WorkspaceDtos.WorkspaceResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class AdminService {
    private final UserRepository users;
    private final DocumentRepository documents;
    private final ChatSessionRepository sessions;
    private final DocumentService documentService;
    private final WorkspaceRepository workspaces;
    private final WorkspaceService workspaceService;
    private final AdminAnalyticsRepository analytics;

    public AdminService(
            UserRepository users,
            DocumentRepository documents,
            ChatSessionRepository sessions,
            DocumentService documentService,
            WorkspaceRepository workspaces,
            WorkspaceService workspaceService,
            AdminAnalyticsRepository analytics
    ) {
        this.users = users;
        this.documents = documents;
        this.sessions = sessions;
        this.documentService = documentService;
        this.workspaces = workspaces;
        this.workspaceService = workspaceService;
        this.analytics = analytics;
    }

    @Cacheable(value = "admin-stats", key = "'global'")
    public DashboardStats stats() {
        return new DashboardStats(
                users.count(), workspaces.count(), documents.count(),
                documents.countByStatus(DocumentStatus.READY), sessions.count());
    }

    @Transactional(readOnly = true)
    public AnalyticsOverview analytics() {
        return new AnalyticsOverview(
                users.count(),
                workspaces.count(),
                documents.count(),
                analytics.documentStatusCounts(),
                sessions.count(),
                analytics.recentActivity(),
                analytics.failedIngestions(),
                analytics.tokenUsage());
    }

    @Transactional(readOnly = true)
    public List<AdminUser> users() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(com.enterpriseai.knowledge.domain.AppUser::getCreatedAt).reversed())
                .map(user -> new AdminUser(
                        user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> documents() {
        return documents.findAllByOrderByCreatedAtDesc().stream()
                .map(documentService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> workspaces() {
        return workspaces.findAllByOrderByCreatedAtAsc().stream()
                .map(workspaceService::toResponse)
                .toList();
    }
}
