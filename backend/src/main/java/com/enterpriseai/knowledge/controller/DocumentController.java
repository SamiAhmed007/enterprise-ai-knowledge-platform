package com.enterpriseai.knowledge.controller;

import com.enterpriseai.knowledge.dto.DocumentDtos.DocumentResponse;
import com.enterpriseai.knowledge.dto.DocumentDtos.RenameDocumentRequest;
import com.enterpriseai.knowledge.service.CurrentUserService;
import com.enterpriseai.knowledge.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

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

    @PatchMapping("/{id}")
    public DocumentResponse rename(
            Authentication authentication,
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @Valid @RequestBody RenameDocumentRequest request
    ) {
        return documents.rename(currentUser.require(authentication), workspaceId, id, request.name());
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(
            Authentication authentication,
            @PathVariable UUID workspaceId,
            @PathVariable UUID id
    ) {
        DocumentService.DocumentPreview preview =
                documents.preview(currentUser.require(authentication), workspaceId, id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(preview.contentType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(preview.name(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(new PathResource(preview.path()));
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
