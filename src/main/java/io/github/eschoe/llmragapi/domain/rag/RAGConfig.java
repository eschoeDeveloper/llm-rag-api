package io.github.eschoe.llmragapi.domain.rag;

public class RAGConfig {
    private int topK = 10;
    private double threshold = 0.7;
    private int maxTokens = 4000;
    private double temperature = 0.7;
    private String searchMode = "similarity";

    public RAGConfig() {}

    public RAGConfig(int topK, double threshold, int maxTokens, double temperature, String searchMode) {
        this.topK = topK;
        this.threshold = threshold;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.searchMode = searchMode;
    }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public String getSearchMode() { return searchMode; }
    public void setSearchMode(String searchMode) { this.searchMode = searchMode; }
}
