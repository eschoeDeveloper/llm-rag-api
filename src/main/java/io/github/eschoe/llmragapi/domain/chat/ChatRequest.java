package io.github.eschoe.llmragapi.domain.chat;

import io.github.eschoe.llmragapi.domain.rag.RAGConfig;
import io.github.eschoe.llmragapi.domain.search.SearchResult;

import java.util.List;

public class ChatRequest {
    private String query;
    private List<SearchResult> searchResults;
    private RAGConfig config;

    public ChatRequest() {}

    public ChatRequest(String query, List<SearchResult> searchResults, RAGConfig config) {
        this.query = query;
        this.searchResults = searchResults;
        this.config = config;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public List<SearchResult> getSearchResults() { return searchResults; }
    public void setSearchResults(List<SearchResult> searchResults) { this.searchResults = searchResults; }

    public RAGConfig getConfig() { return config; }
    public void setConfig(RAGConfig config) { this.config = config; }
}
