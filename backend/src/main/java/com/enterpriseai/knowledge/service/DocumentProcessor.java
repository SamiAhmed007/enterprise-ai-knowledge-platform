package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.config.AppProperties;
import com.enterpriseai.knowledge.domain.DocumentStatus;
import com.enterpriseai.knowledge.domain.KnowledgeDocument;
import com.enterpriseai.knowledge.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentProcessor {
    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);
    private final DocumentRepository documents;
    private final TextExtractionService extraction;
    private final AiClient aiClient;
    private final VectorStore vectorStore;
    private final Path uploadRoot;

    public DocumentProcessor(
            DocumentRepository documents,
            TextExtractionService extraction,
            AiClient aiClient,
            VectorStore vectorStore,
            AppProperties properties
    ) {
        this.documents = documents;
        this.extraction = extraction;
        this.aiClient = aiClient;
        this.vectorStore = vectorStore;
        this.uploadRoot = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
    }

    @Async
    public void process(UUID documentId) {
        KnowledgeDocument document = documents.findById(documentId).orElse(null);
        if (document == null) return;
        if (document.getStatus() != DocumentStatus.PROCESSING) {
            log.info("Skipping document {} because its status is {}", documentId, document.getStatus());
            return;
        }
        try {
            document.setErrorMessage(null);
            document.setProcessedAt(null);
            vectorStore.deleteByDocument(documentId);

            List<DocumentChunk> chunks = extraction
                    .extractPages(uploadRoot.resolve(document.getStoredName()), document.getOriginalName())
                    .stream()
                    .flatMap(page -> chunk(page.text(), page.pageNumber()).stream())
                    .toList();
            if (chunks.isEmpty()) throw new IllegalArgumentException("No readable text was found in this document");

            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunk chunk = chunks.get(i);
                vectorStore.save(
                        documentId,
                        i,
                        chunk.pageNumber(),
                        chunk.content(),
                        aiClient.embedding(chunk.content()));
            }
            document.setStatus(DocumentStatus.READY);
            document.setProcessedAt(Instant.now());
            documents.save(document);
            log.info("Processed document {} into {} chunks", documentId, chunks.size());
        } catch (Exception ex) {
            log.error("Document processing failed for {}", documentId, ex);
            try {
                vectorStore.deleteByDocument(documentId);
            } catch (Exception cleanupError) {
                log.warn("Could not clean partial chunks for failed document {}", documentId, cleanupError);
            }
            document.setStatus(DocumentStatus.FAILED);
            document.setProcessedAt(null);
            document.setErrorMessage(failureMessage(ex));
            documents.save(document);
        }
    }

    private List<DocumentChunk> chunk(String rawText, Integer pageNumber) {
        String text = rawText.replaceAll("\\s+", " ").trim();
        List<DocumentChunk> chunks = new ArrayList<>();
        int start = 0;
        int target = 1200;
        int overlap = 200;
        while (start < text.length()) {
            int end = Math.min(start + target, text.length());
            if (end < text.length()) {
                int sentence = text.lastIndexOf(". ", end);
                if (sentence > start + target / 2) end = sentence + 1;
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) chunks.add(new DocumentChunk(pageNumber, chunk));
            if (end == text.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return chunks;
    }

    private record DocumentChunk(Integer pageNumber, String content) {}

    private String failureMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        String detail = cause.getMessage();
        if (detail == null || detail.isBlank()) detail = cause.getClass().getSimpleName();
        detail = detail.replaceAll("[\\r\\n\\t]+", " ").trim();
        String message = "Ingestion failed: " + detail;
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
