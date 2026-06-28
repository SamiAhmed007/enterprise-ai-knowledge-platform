package com.enterpriseai.knowledge.repository;

import com.enterpriseai.knowledge.domain.ChatSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    List<ChatSession> findAllByWorkspaceIdAndUserIdOrderByUpdatedAtDesc(UUID workspaceId, UUID userId);

    @EntityGraph(attributePaths = {"messages", "workspace"})
    Optional<ChatSession> findWithMessagesByIdAndWorkspaceIdAndUserId(
            UUID id,
            UUID workspaceId,
            UUID userId
    );
}
