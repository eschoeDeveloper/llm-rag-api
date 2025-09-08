package io.github.eschoe.llmragapi.domain.search;

import java.util.List;
import java.util.Map;

public class SearchResponse {
    private List<SearchResult> results;
    private int totalCount;
    private double averageScore;
    private Map<String, Object> metadata;

    public SearchResponse() {}

    public SearchResponse(List<SearchResult> results, int totalCount, double averageScore, Map<String, Object> metadata) {
        this.results = results;
        this.totalCount = totalCount;
        this.averageScore = averageScore;
        this.metadata = metadata;
    }

    public List<SearchResult> getResults() { return results; }
    public void setResults(List<SearchResult> results) { this.results = results; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public double getAverageScore() { return averageScore; }
    public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
