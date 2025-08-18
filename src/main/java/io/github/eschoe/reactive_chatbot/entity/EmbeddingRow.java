package io.github.eschoe.reactive_chatbot.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table(name = "embeddings", schema = "chatbot")
public class EmbeddingRow {

    @Id
    private Long id;
    private String title;
    private String content;
    // r2dbc는 배열/json 변환 허용 -> 간단한 float[]으로 관리
    private Float[] embedding;
    @Column("created_at")
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Float[] embedding) {
        this.embedding = embedding;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
