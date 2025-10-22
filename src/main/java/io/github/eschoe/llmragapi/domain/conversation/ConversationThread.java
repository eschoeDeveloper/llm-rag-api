package io.github.eschoe.llmragapi.domain.conversation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ConversationThread {
    private String id;
    private String title;
    private String description;
    private String sessionId;
    private List<Message> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private ThreadStatus status;
    private Map<String, Object> metadata;

    public ConversationThread() {}

    public ConversationThread(String id, String title, String sessionId) {
        this.id = id;
        this.title = title;
        this.sessionId = sessionId;
        this.status = ThreadStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public ThreadStatus getStatus() { return status; }
    public void setStatus(ThreadStatus status) { this.status = status; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public enum ThreadStatus {
        ACTIVE,
        ARCHIVED,
        DELETED
    }

    public static class Message {
        private String id;
        private String content;
        private MessageRole role;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;

        public Message() {}

        public Message(String content, MessageRole role) {
            this.content = content;
            this.role = role;
            this.timestamp = LocalDateTime.now();
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public MessageRole getRole() { return role; }
        public void setRole(MessageRole role) { this.role = role; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

        public enum MessageRole {
            USER,
            ASSISTANT,
            SYSTEM
        }
    }
}
