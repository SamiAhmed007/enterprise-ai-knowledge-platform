package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.config.AppProperties;
import com.enterpriseai.knowledge.domain.AppUser;
import com.enterpriseai.knowledge.domain.DocumentStatus;
import com.enterpriseai.knowledge.domain.KnowledgeDocument;
import com.enterpriseai.knowledge.domain.Role;
import com.enterpriseai.knowledge.domain.Workspace;
import com.enterpriseai.knowledge.dto.DocumentDtos.DocumentResponse;
import com.enterpriseai.knowledge.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentServiceTest {
    @TempDir
    Path uploadDirectory;
    private final UUID workspaceId = UUID.randomUUID();

    @Test
    void rejectsUnsupportedExtensionsBeforePersisting() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentService service = service(repository);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(12L);
        when(file.getOriginalFilename()).thenReturn("payload.exe");

        assertThatThrownBy(() -> service.upload(mock(AppUser.class), workspaceId, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only PDF, TXT, and DOCX");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsFilesLargerThanConfiguredHttpLimit() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentService service = service(repository);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(25L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> service.upload(mock(AppUser.class), workspaceId, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("maximum upload size");
        verifyNoInteractions(repository);
    }

    @Test
    void uploadReturnsProcessingAndDispatchesBackgroundWork() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentProcessor processor = mock(DocumentProcessor.class);
        DocumentService service = service(repository, processor);
        MultipartFile file = mock(MultipartFile.class);
        AppUser owner = AppUser.builder().id(UUID.randomUUID()).role(Role.USER).build();
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(42L);
        when(file.getOriginalFilename()).thenReturn("handbook.txt");
        when(file.getContentType()).thenReturn("text/plain");
        doNothing().when(file).transferTo(any(Path.class));
        when(repository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            if (document.getId() == null) document.setId(UUID.randomUUID());
            return document;
        });

        DocumentResponse response = service.upload(owner, workspaceId, file);

        assertThat(response.status()).isEqualTo(DocumentStatus.PROCESSING);
        verify(repository, times(2)).save(any(KnowledgeDocument.class));
        verify(processor).process(response.id());
    }

    @Test
    void marksDocumentFailedWhenBackgroundQueueRejectsDispatch() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentProcessor processor = mock(DocumentProcessor.class);
        DocumentFailureService failures = mock(DocumentFailureService.class);
        DocumentService service = service(repository, processor, failures);
        MultipartFile file = mock(MultipartFile.class);
        AppUser owner = AppUser.builder().id(UUID.randomUUID()).role(Role.USER).build();
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(42L);
        when(file.getOriginalFilename()).thenReturn("handbook.txt");
        doNothing().when(file).transferTo(any(Path.class));
        when(repository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            if (document.getId() == null) document.setId(UUID.randomUUID());
            return document;
        });
        doThrow(new IllegalStateException("executor saturated"))
                .when(processor).process(any(UUID.class));

        DocumentResponse response = service.upload(owner, workspaceId, file);

        assertThat(response.status()).isEqualTo(DocumentStatus.PROCESSING);
        verify(failures).markDispatchFailed(response.id());
        verify(repository, times(2)).save(any(KnowledgeDocument.class));
    }

    @Test
    void retryClearsFailureAndQueuesTheExistingFile() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentProcessor processor = mock(DocumentProcessor.class);
        DocumentService service = service(repository, processor);
        AppUser owner = AppUser.builder().id(UUID.randomUUID()).role(Role.USER).build();
        KnowledgeDocument document = failedDocument(owner);
        Files.writeString(uploadDirectory.resolve(document.getStoredName()), "retry me");
        when(repository.findByIdAndWorkspaceId(document.getId(), workspaceId)).thenReturn(Optional.of(document));
        when(repository.markFailedForRetry(document.getId(), workspaceId)).thenReturn(1);

        DocumentResponse response = service.retry(owner, workspaceId, document.getId());

        assertThat(response.status()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(response.errorMessage()).isNull();
        verify(processor).process(document.getId());
    }

    @Test
    void retryRejectsDocumentsThatAreNotFailed() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentProcessor processor = mock(DocumentProcessor.class);
        DocumentService service = service(repository, processor);
        AppUser owner = AppUser.builder().id(UUID.randomUUID()).role(Role.USER).build();
        KnowledgeDocument document = failedDocument(owner);
        document.setStatus(DocumentStatus.READY);
        when(repository.findByIdAndWorkspaceId(document.getId(), workspaceId)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.retry(owner, workspaceId, document.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only failed documents");
        verifyNoInteractions(processor);
    }

    private DocumentService service(DocumentRepository repository) throws Exception {
        return service(repository, mock(DocumentProcessor.class));
    }

    private DocumentService service(
            DocumentRepository repository,
            DocumentProcessor processor
    ) throws Exception {
        return service(repository, processor, mock(DocumentFailureService.class));
    }

    private DocumentService service(
            DocumentRepository repository,
            DocumentProcessor processor,
            DocumentFailureService failures
    ) throws Exception {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:*")),
                new AppProperties.Jwt("test-secret-that-is-at-least-32-characters", 60_000),
                new AppProperties.Storage(uploadDirectory.toString()),
                new AppProperties.Ai(
                        "openai", "", "https://api.openai.com/v1",
                        "chat", "embedding", 1536, "2024-10-21"),
                new AppProperties.Retrieval(5, 0.7, 0.3, 0.15),
                new AppProperties.RateLimit(20, 30),
                new AppProperties.BootstrapAdmin("", "", "")
        );
        WorkspaceService workspaceService = mock(WorkspaceService.class);
        when(workspaceService.requireAccessible(any(AppUser.class), eq(workspaceId)))
                .thenAnswer(invocation -> Workspace.builder()
                        .id(workspaceId)
                        .name("Test Workspace")
                        .owner(invocation.getArgument(0))
                        .build());
        return new DocumentService(
                repository,
                processor,
                mock(VectorStore.class),
                workspaceService,
                failures,
                properties);
    }

    private KnowledgeDocument failedDocument(AppUser owner) {
        return KnowledgeDocument.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .workspace(Workspace.builder()
                        .id(workspaceId)
                        .name("Test Workspace")
                        .owner(owner)
                        .build())
                .originalName("handbook.txt")
                .storedName("stored-handbook.txt")
                .contentType("text/plain")
                .sizeBytes(8)
                .status(DocumentStatus.FAILED)
                .errorMessage("Ingestion failed: invalid content")
                .build();
    }
}
