package io.github.eschoe.llmragapi.domain.document;

import java.util.Map;

public class DocumentUploadRequest {
    private String title;
    private String description;
    private String category;
    private Map<String, Object> metadata;
    private String sessionId;

    public DocumentUploadRequest() {}

    public DocumentUploadRequest(String title, String description) {
        this.title = title;
        this.description = description;
    }

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
