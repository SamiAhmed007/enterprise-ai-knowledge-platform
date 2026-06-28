package com.enterpriseai.knowledge.controller;

import com.enterpriseai.knowledge.dto.DocumentDtos.DocumentResponse;
import com.enterpriseai.knowledge.service.CurrentUserService;
import com.enterpriseai.knowledge.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/documents")
public class DocumentController {
    private final DocumentService documents;
    private final CurrentUserService currentUser;

    public DocumentController(DocumentService documents, CurrentUserService currentUser) {
        this.documents = documents;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<DocumentResponse> list(
            Authentication authentication,
            @PathVariable UUID workspaceId
    ) {
        return documents.list(currentUser.require(authentication), workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse upload(
            Authentication authentication,
            @PathVariable UUID workspaceId,
            @RequestPart("file") MultipartFile file
    ) {
        return documents.upload(currentUser.require(authentication), workspaceId, file);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse retry(
            Authentication authentication,
            @PathVariable UUID workspaceId,
            @PathVariable UUID id
    ) {
        return documents.retry(currentUser.require(authentication), workspaceId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            Authentication authentication,
            @PathVariable UUID workspaceId,
            @PathVariable UUID id
    ) {
        documents.delete(currentUser.require(authentication), workspaceId, id);
    }
}
