package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.config.AppProperties;
import com.enterpriseai.knowledge.domain.DocumentStatus;
import com.enterpriseai.knowledge.domain.KnowledgeDocument;
import com.enterpriseai.knowledge.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentProcessorTest {
    @TempDir
    Path uploadDirectory;

    @Test
    void indexesInTheBackgroundAndMarksDocumentReady() throws Exception {
        DocumentRepository documents = mock(DocumentRepository.class);
        TextExtractionService extraction = mock(TextExtractionService.class);
        AiClient aiClient = mock(AiClient.class);
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeDocument document = processingDocument();
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));
        when(extraction.extractPages(any(Path.class), eq("policy.pdf"))).thenReturn(List.of(
                new TextExtractionService.ExtractedPage(3, "Retention is seven years.")));
        when(aiClient.embedding(anyString())).thenReturn(List.of(0.1, 0.2));

        processor(documents, extraction, aiClient, vectorStore).process(document.getId());

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(document.getProcessedAt()).isNotNull();
        assertThat(document.getErrorMessage()).isNull();
        verify(vectorStore).deleteByDocument(document.getId());
        verify(vectorStore).save(
                eq(document.getId()), eq(0), eq(3),
                eq("Retention is seven years."), eq(List.of(0.1, 0.2)));
        verify(documents).save(document);
    }

    @Test
    void cleansPartialChunksAndStoresAnActionableFailure() throws Exception {
        DocumentRepository documents = mock(DocumentRepository.class);
        TextExtractionService extraction = mock(TextExtractionService.class);
        AiClient aiClient = mock(AiClient.class);
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeDocument document = processingDocument();
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));
        when(extraction.extractPages(any(Path.class), anyString()))
                .thenThrow(new IOException("encrypted PDF cannot be read"));

        processor(documents, extraction, aiClient, vectorStore).process(document.getId());

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getProcessedAt()).isNull();
        assertThat(document.getErrorMessage())
                .isEqualTo("Ingestion failed: encrypted PDF cannot be read");
        verify(vectorStore, times(2)).deleteByDocument(document.getId());
        verify(vectorStore, never()).save(any(), anyInt(), any(), anyString(), anyList());
        verify(documents).save(document);
    }

    private DocumentProcessor processor(
            DocumentRepository documents,
            TextExtractionService extraction,
            AiClient aiClient,
            VectorStore vectorStore
    ) {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:*")),
                new AppProperties.Jwt("test-secret-that-is-at-least-32-characters", 60_000),
                new AppProperties.Storage(uploadDirectory.toString()),
                new AppProperties.Ai(
                        "openai", "", "https://api.openai.com/v1",
                        "chat", "embedding", 2, "2024-10-21"),
                new AppProperties.Retrieval(5, 0.7, 0.3, 0.15),
                new AppProperties.RateLimit(20, 30),
                new AppProperties.BootstrapAdmin("", "", "")
        );
        return new DocumentProcessor(documents, extraction, aiClient, vectorStore, properties);
    }

    private KnowledgeDocument processingDocument() {
        return KnowledgeDocument.builder()
                .id(UUID.randomUUID())
                .originalName("policy.pdf")
                .storedName("stored-policy.pdf")
                .status(DocumentStatus.PROCESSING)
                .build();
    }
}
