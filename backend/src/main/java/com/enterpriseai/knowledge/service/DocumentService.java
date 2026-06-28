package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.config.AppProperties;
import com.enterpriseai.knowledge.domain.AppUser;
import com.enterpriseai.knowledge.domain.DocumentStatus;
import com.enterpriseai.knowledge.domain.KnowledgeDocument;
import com.enterpriseai.knowledge.domain.Workspace;
import com.enterpriseai.knowledge.dto.DocumentDtos.DocumentResponse;
import com.enterpriseai.knowledge.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Set<String> ALLOWED = Set.of("pdf", "txt", "docx");
    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    private final DocumentRepository documents;
    private final DocumentProcessor processor;
    private final VectorStore vectorStore;
    private final WorkspaceService workspaces;
    private final DocumentFailureService failures;
    private final Path uploadRoot;

    public DocumentService(
            DocumentRepository documents,
            DocumentProcessor processor,
            VectorStore vectorStore,
            WorkspaceService workspaces,
            DocumentFailureService failures,
            AppProperties properties
    ) throws IOException {
        this.documents = documents;
        this.processor = processor;
        this.vectorStore = vectorStore;
        this.workspaces = workspaces;
        this.failures = failures;
        this.uploadRoot = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot);
    }

    @Transactional
    public DocumentResponse upload(AppUser owner, UUID workspaceId, MultipartFile file) {
        Workspace workspace = workspaces.requireAccessible(owner, workspaceId);
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The maximum upload size is 25 MB");
        }
        String originalName = Paths.get(file.getOriginalFilename() == null ? "document" : file.getOriginalFilename())
                .getFileName().toString();
        if (originalName.isBlank() || originalName.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file name is invalid or too long");
        }
        String extension = TextExtractionService.extension(originalName);
        if (!ALLOWED.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF, TXT, and DOCX are supported");
        }
        String storedName = UUID.randomUUID() + "." + extension;
        try {
            file.transferTo(uploadRoot.resolve(storedName));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the uploaded file");
        }
        KnowledgeDocument document;
        try {
            document = documents.save(KnowledgeDocument.builder()
                    .owner(owner)
                    .workspace(workspace)
                    .originalName(originalName)
                    .storedName(storedName)
                    .contentType(file.getContentType())
                    .sizeBytes(file.getSize())
                    .status(DocumentStatus.UPLOADED)
                    .build());
            document.setStatus(DocumentStatus.PROCESSING);
            document.setErrorMessage(null);
            document.setProcessedAt(null);
            document = documents.save(document);
        } catch (RuntimeException ex) {
            try {
                Files.deleteIfExists(uploadRoot.resolve(storedName));
            } catch (IOException ignored) {
                // Preserve the original persistence error.
            }
            throw ex;
        }
        dispatchAfterCommit(document.getId());
        return toResponse(document);
    }

    @Transactional
    public DocumentResponse retry(AppUser user, UUID workspaceId, UUID id) {
        KnowledgeDocument document = requireAccessible(user, workspaceId, id);
        if (document.getStatus() != DocumentStatus.FAILED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only failed documents can be retried");
        }
        if (!Files.isRegularFile(uploadRoot.resolve(document.getStoredName()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The stored file is unavailable. Delete this document and upload it again.");
        }
        if (documents.markFailedForRetry(id, workspaceId) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Document status changed before the retry could start");
        }
        document.setStatus(DocumentStatus.PROCESSING);
        document.setErrorMessage(null);
        document.setProcessedAt(null);
        dispatchAfterCommit(id);
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(AppUser user, UUID workspaceId) {
        workspaces.requireAccessible(user, workspaceId);
        return documents.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(AppUser user, UUID workspaceId, UUID id) {
        KnowledgeDocument document = requireAccessible(user, workspaceId, id);
        vectorStore.deleteByDocument(id);
        documents.delete(document);
        try {
            Files.deleteIfExists(uploadRoot.resolve(document.getStoredName()));
        } catch (IOException ex) {
            log.warn("Deleted document {} from the database but could not remove stored file {}",
                    id, document.getStoredName(), ex);
        }
    }

    private KnowledgeDocument requireAccessible(AppUser user, UUID workspaceId, UUID id) {
        workspaces.requireAccessible(user, workspaceId);
        KnowledgeDocument document = documents.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return document;
    }

    private void dispatchAfterCommit(UUID documentId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(documentId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(documentId);
            }
        });
    }

    private void dispatch(UUID documentId) {
        try {
            processor.process(documentId);
        } catch (RuntimeException exception) {
            log.error("Could not dispatch document {} for background processing", documentId, exception);
            failures.markDispatchFailed(documentId);
        }
    }

    public DocumentResponse toResponse(KnowledgeDocument document) {
        return new DocumentResponse(
                document.getId(), document.getOriginalName(), document.getContentType(),
                document.getSizeBytes(), document.getStatus(), document.getErrorMessage(),
                document.getCreatedAt(), document.getProcessedAt(),
                document.getWorkspace().getId(), document.getWorkspace().getName(),
                document.getOwner().getName(), document.getOwner().getEmail()
        );
    }
}
