package io.github.eschoe.llmragapi.global;

import java.time.Instant;
import java.util.Map;

public class DetailedErrorResponse {
    private String error;
    private String message;
    private String details;
    private String timestamp;
    private String sessionId;
    private Map<String, Object> metadata;

    public DetailedErrorResponse() {}

    public DetailedErrorResponse(String error, String message, String details, String sessionId) {
        this.error = error;
        this.message = message;
        this.details = details;
        this.sessionId = sessionId;
        this.timestamp = Instant.now().toString();
    }

    public DetailedErrorResponse(String error, String message, String details, String sessionId, Map<String, Object> metadata) {
        this.error = error;
        this.message = message;
        this.details = details;
        this.sessionId = sessionId;
        this.timestamp = Instant.now().toString();
        this.metadata = metadata;
    }

    // Getters and Setters
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
