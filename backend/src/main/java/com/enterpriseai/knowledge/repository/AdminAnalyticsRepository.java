package com.enterpriseai.knowledge.repository;

import com.enterpriseai.knowledge.domain.DocumentStatus;
import com.enterpriseai.knowledge.dto.AdminDtos.FailedIngestion;
import com.enterpriseai.knowledge.dto.AdminDtos.RecentActivity;
import com.enterpriseai.knowledge.dto.AdminDtos.TokenUsage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Repository
public class AdminAnalyticsRepository {
    private static final int ACTIVITY_LIMIT = 12;
    private static final int FAILED_INGESTION_LIMIT = 20;

    private final JdbcClient jdbc;

    public AdminAnalyticsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<DocumentStatus, Long> documentStatusCounts() {
        EnumMap<DocumentStatus, Long> counts = new EnumMap<>(DocumentStatus.class);
        for (DocumentStatus status : DocumentStatus.values()) {
            counts.put(status, 0L);
        }

        jdbc.sql("""
                        SELECT status, COUNT(*) AS total
                        FROM documents
                        GROUP BY status
                        """)
                .query((resultSet, rowNumber) -> Map.entry(
                        DocumentStatus.valueOf(resultSet.getString("status")),
                        resultSet.getLong("total")))
                .list()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    public List<RecentActivity> recentActivity() {
        return jdbc.sql("""
                        SELECT activity_type, description, actor_name, actor_email, occurred_at
                        FROM (
                            SELECT 'USER_REGISTERED' AS activity_type,
                                   'Joined the platform' AS description,
                                   user_account.name AS actor_name,
                                   user_account.email AS actor_email,
                                   user_account.created_at AS occurred_at
                            FROM app_users user_account
                            UNION ALL
                            SELECT 'WORKSPACE_CREATED',
                                   'Created workspace "' || workspace.name || '"',
                                   owner.name,
                                   owner.email,
                                   workspace.created_at
                            FROM workspaces workspace
                            JOIN app_users owner ON owner.id = workspace.owner_id
                            UNION ALL
                            SELECT 'DOCUMENT_UPLOADED',
                                   'Uploaded "' || document.original_name || '"',
                                   owner.name,
                                   owner.email,
                                   document.created_at
                            FROM documents document
                            JOIN app_users owner ON owner.id = document.owner_id
                            UNION ALL
                            SELECT 'CHAT_STARTED',
                                   'Started chat "' || session.title || '"',
                                   user_account.name,
                                   user_account.email,
                                   session.created_at
                            FROM chat_sessions session
                            JOIN app_users user_account ON user_account.id = session.user_id
                        ) activity
                        ORDER BY occurred_at DESC
                        LIMIT :limit
                        """)
                .param("limit", ACTIVITY_LIMIT)
                .query((resultSet, rowNumber) -> new RecentActivity(
                        resultSet.getString("activity_type"),
                        resultSet.getString("description"),
                        resultSet.getString("actor_name"),
                        resultSet.getString("actor_email"),
                        resultSet.getTimestamp("occurred_at").toInstant()))
                .list();
    }

    public List<FailedIngestion> failedIngestions() {
        return jdbc.sql("""
                        SELECT document.id,
                               document.original_name,
                               workspace.name AS workspace_name,
                               owner.email AS owner_email,
                               document.error_message,
                               COALESCE(document.processed_at, document.created_at) AS failed_at
                        FROM documents document
                        JOIN workspaces workspace ON workspace.id = document.workspace_id
                        JOIN app_users owner ON owner.id = document.owner_id
                        WHERE document.status = 'FAILED'
                        ORDER BY failed_at DESC
                        LIMIT :limit
                        """)
                .param("limit", FAILED_INGESTION_LIMIT)
                .query((resultSet, rowNumber) -> new FailedIngestion(
                        resultSet.getObject("id", java.util.UUID.class),
                        resultSet.getString("original_name"),
                        resultSet.getString("workspace_name"),
                        resultSet.getString("owner_email"),
                        resultSet.getString("error_message"),
                        resultSet.getTimestamp("failed_at").toInstant()))
                .list();
    }

    public TokenUsage tokenUsage() {
        return jdbc.sql("""
                        SELECT
                            COALESCE((SELECT SUM(token_estimate) FROM document_chunks), 0) AS embedding_tokens,
                            COALESCE((SELECT CEIL(SUM(LENGTH(content)) / 4.0) FROM chat_messages), 0) AS chat_tokens
                        """)
                .query((resultSet, rowNumber) -> {
                    long embeddingTokens = resultSet.getLong("embedding_tokens");
                    long chatTokens = resultSet.getLong("chat_tokens");
                    return new TokenUsage(
                            embeddingTokens,
                            chatTokens,
                            embeddingTokens + chatTokens,
                            true);
                })
                .single();
    }
}
