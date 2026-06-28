package com.enterpriseai.knowledge.repository;

import com.enterpriseai.knowledge.domain.KnowledgeDocument;
import com.enterpriseai.knowledge.domain.DocumentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {
    @EntityGraph(attributePaths = {"owner", "workspace"})
    List<KnowledgeDocument> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    @EntityGraph(attributePaths = {"owner", "workspace"})
    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"owner", "workspace"})
    Optional<KnowledgeDocument> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE KnowledgeDocument document
            SET document.status = com.enterpriseai.knowledge.domain.DocumentStatus.PROCESSING,
                document.errorMessage = null,
                document.processedAt = null
            WHERE document.id = :id
              AND document.workspace.id = :workspaceId
              AND document.status = com.enterpriseai.knowledge.domain.DocumentStatus.FAILED
            """)
    int markFailedForRetry(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    long countByWorkspaceId(UUID workspaceId);
    long countByStatus(DocumentStatus status);
}
