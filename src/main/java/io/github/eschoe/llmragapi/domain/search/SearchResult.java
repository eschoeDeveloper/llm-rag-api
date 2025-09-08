package io.github.eschoe.llmragapi.domain.search;

import java.util.Map;

public class SearchResult {

    private String id;
    private String content;
    private double score;
    private Map<String, Object> metadata;
    private String source;

    public SearchResult(String id, String content, double score, Map<String, Object> metadata, String source) {
        this.id = id;
        this.content = content;
        this.score = score;
        this.metadata = metadata;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

}
