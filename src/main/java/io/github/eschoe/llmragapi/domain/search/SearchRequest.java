package io.github.eschoe.llmragapi.domain.search;

public class SearchRequest {
    private String query;
    private int topK = 10;
    private double threshold = 0.7;

    public SearchRequest() {}

    public SearchRequest(String query, int topK, double threshold) {
        this.query = query;
        this.topK = topK;
        this.threshold = threshold;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
}