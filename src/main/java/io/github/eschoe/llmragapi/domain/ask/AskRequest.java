package io.github.eschoe.llmragapi.domain.ask;

import io.github.eschoe.llmragapi.domain.rag.RAGConfig;

public class AskRequest {
    private String query;
    private RAGConfig config;
    private String sessionId;

    public AskRequest() {}

    public AskRequest(String query, RAGConfig config, String sessionId) {
        this.query = query;
        this.config = config;
        this.sessionId = sessionId;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public RAGConfig getConfig() { return config; }
    public void setConfig(RAGConfig config) { this.config = config; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
