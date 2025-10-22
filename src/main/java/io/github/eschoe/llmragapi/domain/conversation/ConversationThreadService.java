package io.github.eschoe.llmragapi.domain.conversation;

import io.github.eschoe.llmragapi.domain.history.ChatHistoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConversationThreadService {

    private final ReactiveStringRedisTemplate redis;
    private final ChatHistoryStore chatHistoryStore;

    @Value("${app.llm.thread-ttl:7d}")
    private String threadTtl;

    public ConversationThreadService(ReactiveStringRedisTemplate redis, ChatHistoryStore chatHistoryStore) {
        this.redis = redis;
        this.chatHistoryStore = chatHistoryStore;
    }

    public Mono<ConversationThread> createThread(String sessionId, String title) {
        String threadId = UUID.randomUUID().toString();
        ConversationThread thread = new ConversationThread(threadId, title, sessionId);
        
        return saveThread(thread)
                .thenReturn(thread);
    }

    public Mono<ConversationThread> getThread(String threadId) {
        String key = "thread:" + threadId;
        
        return redis.opsForValue().get(key)
                .map(this::parseThread)
                .onErrorReturn(null);
    }

    public Flux<ConversationThread> getUserThreads(String sessionId) {
        String key = "user:threads:" + sessionId;
        
        return redis.opsForSet().members(key)
                .flatMap(threadId -> getThread(threadId))
                .filter(thread -> thread != null && thread.getStatus() != ConversationThread.ThreadStatus.DELETED)
                .sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));
    }

    public Mono<ConversationThread> addMessage(String threadId, String content, ConversationThread.Message.MessageRole role) {
        return getThread(threadId)
                .flatMap(thread -> {
                    if (thread == null) {
                        return Mono.error(new IllegalArgumentException("Thread not found"));
                    }
                    
                    ConversationThread.Message message = new ConversationThread.Message(content, role);
                    message.setId(UUID.randomUUID().toString());
                    thread.getMessages().add(message);
                    thread.setUpdatedAt(LocalDateTime.now());
                    
                    return saveThread(thread);
                });
    }

    public Mono<ConversationThread> updateThreadTitle(String threadId, String newTitle) {
        return getThread(threadId)
                .flatMap(thread -> {
                    if (thread == null) {
                        return Mono.error(new IllegalArgumentException("Thread not found"));
                    }
                    
                    thread.setTitle(newTitle);
                    thread.setUpdatedAt(LocalDateTime.now());
                    
                    return saveThread(thread);
                });
    }

    public Mono<Void> archiveThread(String threadId) {
        return getThread(threadId)
                .flatMap(thread -> {
                    if (thread == null) {
                        return Mono.error(new IllegalArgumentException("Thread not found"));
                    }
                    
                    thread.setStatus(ConversationThread.ThreadStatus.ARCHIVED);
                    thread.setUpdatedAt(LocalDateTime.now());
                    
                    return saveThread(thread);
                })
                .then();
    }

    public Mono<Void> deleteThread(String threadId) {
        return getThread(threadId)
                .flatMap(thread -> {
                    if (thread == null) {
                        return Mono.error(new IllegalArgumentException("Thread not found"));
                    }
                    
                    thread.setStatus(ConversationThread.ThreadStatus.DELETED);
                    thread.setUpdatedAt(LocalDateTime.now());
                    
                    return saveThread(thread);
                })
                .then();
    }

    public Mono<ConversationThread> loadThreadFromHistory(String threadId, String sessionId) {
        return chatHistoryStore.recent(sessionId, 50)
                .collectList()
                .flatMap(historyMessages -> {
                    ConversationThread thread = new ConversationThread(threadId, "Loaded from History", sessionId);
                    
                    List<ConversationThread.Message> messages = historyMessages.stream()
                            .map(this::parseHistoryMessage)
                            .collect(Collectors.toList());
                    
                    thread.setMessages(messages);
                    thread.setUpdatedAt(LocalDateTime.now());
                    
                    return saveThread(thread);
                });
    }

    private Mono<ConversationThread> saveThread(ConversationThread thread) {
        String key = "thread:" + thread.getId();
        String threadJson = serializeThread(thread);
        
        return redis.opsForValue().set(key, threadJson, Duration.parse("PT" + threadTtl.replace("d", "D")))
                .then(redis.opsForSet().add("user:threads:" + thread.getSessionId(), thread.getId()))
                .thenReturn(thread);
    }

    private ConversationThread parseThread(String threadJson) {
        try {
            // 간단한 JSON 파싱 (실제로는 Jackson 사용 권장)
            String id = extractValue(threadJson, "id");
            String title = extractValue(threadJson, "title");
            String sessionId = extractValue(threadJson, "sessionId");
            String status = extractValue(threadJson, "status");
            String createdAt = extractValue(threadJson, "createdAt");
            String updatedAt = extractValue(threadJson, "updatedAt");
            
            ConversationThread thread = new ConversationThread(id, title, sessionId);
            thread.setStatus(ConversationThread.ThreadStatus.valueOf(status));
            thread.setCreatedAt(LocalDateTime.parse(createdAt));
            thread.setUpdatedAt(LocalDateTime.parse(updatedAt));
            
            return thread;
        } catch (Exception e) {
            return null;
        }
    }

    private ConversationThread.Message parseHistoryMessage(String historyJson) {
        try {
            String role = extractValue(historyJson, "role");
            String content = extractValue(historyJson, "content");
            String timestamp = extractValue(historyJson, "timestamp");
            
            ConversationThread.Message message = new ConversationThread.Message();
            message.setId(UUID.randomUUID().toString());
            message.setContent(content);
            message.setRole(ConversationThread.Message.MessageRole.valueOf(role.toUpperCase()));
            message.setTimestamp(LocalDateTime.parse(timestamp));
            
            return message;
        } catch (Exception e) {
            return new ConversationThread.Message("파싱 오류", ConversationThread.Message.MessageRole.SYSTEM);
        }
    }

    private String serializeThread(ConversationThread thread) {
        return String.format(
            "{\"id\":\"%s\",\"title\":\"%s\",\"sessionId\":\"%s\",\"status\":\"%s\",\"createdAt\":\"%s\",\"updatedAt\":\"%s\"}",
            thread.getId(),
            thread.getTitle().replace("\"", "\\\""),
            thread.getSessionId(),
            thread.getStatus().name(),
            thread.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            thread.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    private String extractValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return "";
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end);
    }
}
