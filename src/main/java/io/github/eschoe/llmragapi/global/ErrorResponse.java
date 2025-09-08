package io.github.eschoe.llmragapi.global;

import java.time.Instant;

public class ErrorResponse {
    private String message;
    private Instant timestamp;

    public ErrorResponse() {}

    public ErrorResponse(String message, Instant timestamp) {
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
