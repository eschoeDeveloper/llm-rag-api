package io.github.eschoe.llmragapi.domain.chat;

import java.util.Map;

public class ChatResponse {
    private String content;
    private String model;
    private int tokens;
    private Map<String, Object> metadata;

    public ChatResponse() {}

    public ChatResponse(String content, String model, int tokens, Map<String, Object> metadata) {
        this.content = content;
        this.model = model;
        this.tokens = tokens;
        this.metadata = metadata;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getTokens() { return tokens; }
    public void setTokens(int tokens) { this.tokens = tokens; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}