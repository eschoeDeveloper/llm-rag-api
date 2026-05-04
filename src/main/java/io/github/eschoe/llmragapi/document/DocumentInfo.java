package io.github.eschoe.llmragapi.document;

import java.time.LocalDateTime;

/** 업로드된 문서 메타데이터 (Redis 저장 단위). */
public class DocumentInfo {
    private String id;
    private String title;
    private String description;
    private String category;
    private int totalChunks;
    private LocalDateTime uploadedAt;

    public DocumentInfo() {}

    public DocumentInfo(String id, String title, String description, String category,
                        int totalChunks, LocalDateTime uploadedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.totalChunks = totalChunks;
        this.uploadedAt = uploadedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
