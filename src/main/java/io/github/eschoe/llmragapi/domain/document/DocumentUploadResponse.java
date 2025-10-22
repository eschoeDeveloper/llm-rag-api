package io.github.eschoe.llmragapi.domain.document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class DocumentUploadResponse {
    private String documentId;
    private String title;
    private String status;
    private int totalChunks;
    private int processedChunks;
    private LocalDateTime uploadedAt;
    private List<String> errors;
    private Map<String, Object> metadata;

    public DocumentUploadResponse() {}

    public DocumentUploadResponse(String documentId, String title, String status) {
        this.documentId = documentId;
        this.title = title;
        this.status = status;
        this.uploadedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    public int getProcessedChunks() { return processedChunks; }
    public void setProcessedChunks(int processedChunks) { this.processedChunks = processedChunks; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public enum Status {
        UPLOADING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
