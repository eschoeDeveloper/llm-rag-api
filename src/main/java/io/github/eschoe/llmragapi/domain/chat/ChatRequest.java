package io.github.eschoe.llmragapi.domain.chat;

import io.github.eschoe.llmragapi.domain.rag.RAGConfig;
import io.github.eschoe.llmragapi.domain.search.SearchResult;

import java.util.List;

public class ChatRequest {

    private String query;
    private List<SearchResult> searchResults;
    private RAGConfig config;
    private String sessionId;  // 세션 ID 추가

    public ChatRequest() {}

    public ChatRequest(String query, List<SearchResult> searchResults, RAGConfig config) {
        this.query = query;
        this.searchResults = searchResults;
        this.config = config;
    }

    public ChatRequest(String query, List<SearchResult> searchResults, RAGConfig config, String sessionId) {
        this.query = query;
        this.searchResults = searchResults;
        this.config = config;
        this.sessionId = sessionId;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public List<SearchResult> getSearchResults() { return searchResults; }
    public void setSearchResults(List<SearchResult> searchResults) { this.searchResults = searchResults; }

    public RAGConfig getConfig() { return config; }
    public void setConfig(RAGConfig config) { this.config = config; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
