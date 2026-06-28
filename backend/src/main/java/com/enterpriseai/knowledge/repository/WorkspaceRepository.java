package com.enterpriseai.knowledge.repository;

import com.enterpriseai.knowledge.domain.Workspace;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    @EntityGraph(attributePaths = "owner")
    List<Workspace> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    @EntityGraph(attributePaths = "owner")
    List<Workspace> findAllByOrderByCreatedAtAsc();

    @EntityGraph(attributePaths = "owner")
    Optional<Workspace> findWithOwnerById(UUID id);

    boolean existsByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);
}
