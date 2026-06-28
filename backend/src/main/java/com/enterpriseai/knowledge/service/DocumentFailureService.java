package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.domain.DocumentStatus;
import com.enterpriseai.knowledge.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DocumentFailureService {
    private final DocumentRepository documents;

    public DocumentFailureService(DocumentRepository documents) {
        this.documents = documents;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDispatchFailed(UUID documentId) {
        documents.findById(documentId).ifPresent(document -> {
            if (document.getStatus() != DocumentStatus.PROCESSING) return;
            document.setStatus(DocumentStatus.FAILED);
            document.setProcessedAt(null);
            document.setErrorMessage(
                    "Ingestion failed: the processing queue is temporarily unavailable. Please retry.");
            documents.save(document);
        });
    }
}
