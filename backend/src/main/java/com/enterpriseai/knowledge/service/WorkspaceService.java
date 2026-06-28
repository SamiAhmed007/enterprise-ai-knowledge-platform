package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.domain.AppUser;
import com.enterpriseai.knowledge.domain.Role;
import com.enterpriseai.knowledge.domain.Workspace;
import com.enterpriseai.knowledge.dto.WorkspaceDtos.CreateWorkspaceRequest;
import com.enterpriseai.knowledge.dto.WorkspaceDtos.WorkspaceResponse;
import com.enterpriseai.knowledge.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {
    public static final String DEFAULT_NAME = "Default Workspace";
    private final WorkspaceRepository workspaces;

    public WorkspaceService(WorkspaceRepository workspaces) {
        this.workspaces = workspaces;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listAccessible(AppUser user) {
        List<Workspace> result = user.getRole() == Role.ADMIN
                ? workspaces.findAllByOrderByCreatedAtAsc()
                : workspaces.findAllByOwnerIdOrderByCreatedAtAsc(user.getId());
        return result.stream().map(this::toResponse).toList();
    }

    @Transactional
    public WorkspaceResponse create(AppUser owner, CreateWorkspaceRequest request) {
        String name = request.name().strip();
        if (workspaces.existsByOwnerIdAndNameIgnoreCase(owner.getId(), name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A workspace with this name already exists");
        }
        return toResponse(workspaces.save(Workspace.builder().owner(owner).name(name).build()));
    }

    @Transactional
    public Workspace ensureDefault(AppUser owner) {
        return workspaces.findAllByOwnerIdOrderByCreatedAtAsc(owner.getId()).stream()
                .findFirst()
                .orElseGet(() -> workspaces.save(
                        Workspace.builder().owner(owner).name(DEFAULT_NAME).build()));
    }

    @Transactional(readOnly = true)
    public Workspace requireAccessible(AppUser user, UUID workspaceId) {
        Workspace workspace = workspaces.findWithOwnerById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Workspace not found"));
        if (user.getRole() != Role.ADMIN && !workspace.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
        return workspace;
    }

    public WorkspaceResponse toResponse(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getOwner().getId(),
                workspace.getOwner().getName(),
                workspace.getOwner().getEmail(),
                workspace.getCreatedAt(),
                workspace.getUpdatedAt());
    }
}
