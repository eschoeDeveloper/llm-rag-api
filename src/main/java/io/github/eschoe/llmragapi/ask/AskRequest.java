package io.github.eschoe.llmragapi.ask;

import io.github.eschoe.llmragapi.rag.config.RAGConfig;

public class AskRequest {
    private String query;
    private RAGConfig config;
    private String sessionId;
    private String customPrompt;

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

    public String getCustomPrompt() { return customPrompt; }
    public void setCustomPrompt(String customPrompt) { this.customPrompt = customPrompt; }
}
