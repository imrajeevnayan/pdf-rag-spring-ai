package com.example.pdfrag.store;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stores PDF document metadata in H2 (via Spring Data JPA).
 * The actual embeddings live in the vector store; this just tracks what was uploaded.
 */
@Component
public class DocumentRegistry {

    public record DocumentInfo(String documentId, String filename, Instant createdAt, int chunkCount) {}

    private final DocumentRepository repository;

    public DocumentRegistry(DocumentRepository repository) {
        this.repository = repository;
    }

    /** Generate a fresh document id (used to tag chunks before they are persisted). */
    public String newId() {
        return UUID.randomUUID().toString();
    }

    /** Persist metadata for an ingested document. */
    public DocumentInfo save(String documentId, String filename, int chunkCount) {
        DocumentEntity entity = new DocumentEntity(documentId, filename, Instant.now(), chunkCount);
        repository.save(entity);
        return toInfo(entity);
    }

    public DocumentInfo getOrThrow(String documentId) {
        return repository.findById(documentId)
                .map(this::toInfo)
                .orElseThrow(() -> new IllegalArgumentException("Unknown documentId: " + documentId));
    }

    public List<DocumentInfo> list() {
        return repository.findAll().stream()
                .map(this::toInfo)
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .toList();
    }

    private DocumentInfo toInfo(DocumentEntity e) {
        return new DocumentInfo(e.getDocumentId(), e.getFilename(), e.getCreatedAt(), e.getChunkCount());
    }
}
