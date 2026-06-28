package com.enterpriseai.knowledge.controller;

import com.enterpriseai.knowledge.dto.WorkspaceDtos.CreateWorkspaceRequest;
import com.enterpriseai.knowledge.dto.WorkspaceDtos.WorkspaceResponse;
import com.enterpriseai.knowledge.service.CurrentUserService;
import com.enterpriseai.knowledge.service.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaces;
    private final CurrentUserService currentUser;

    public WorkspaceController(WorkspaceService workspaces, CurrentUserService currentUser) {
        this.workspaces = workspaces;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<WorkspaceResponse> list(Authentication authentication) {
        return workspaces.listAccessible(currentUser.require(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(
            Authentication authentication,
            @Valid @RequestBody CreateWorkspaceRequest request
    ) {
        return workspaces.create(currentUser.require(authentication), request);
    }
}
