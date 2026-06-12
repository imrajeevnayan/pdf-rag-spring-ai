package com.example.pdfrag.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @Column(name = "document_id", nullable = false, updatable = false)
    private String documentId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    protected DocumentEntity() {
        // Required by JPA
    }

    public DocumentEntity(String documentId, String filename, Instant createdAt, int chunkCount) {
        this.documentId = documentId;
        this.filename = filename;
        this.createdAt = createdAt;
        this.chunkCount = chunkCount;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }
}
