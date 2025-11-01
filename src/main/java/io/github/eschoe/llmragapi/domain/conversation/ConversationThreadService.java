package io.github.eschoe.llmragapi.domain.conversation;

import io.github.eschoe.llmragapi.domain.history.ChatHistoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConversationThreadService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationThreadService.class);
    
    private final ReactiveStringRedisTemplate redis;
    private final ChatHistoryStore chatHistoryStore;

    @Value("${app.llm.thread-ttl:7d}")
    private String threadTtl;

    public ConversationThreadService(ReactiveStringRedisTemplate redis, ChatHistoryStore chatHistoryStore) {
        this.redis = redis;
        this.chatHistoryStore = chatHistoryStore;
    }

    public Mono<ConversationThread> createThread(String sessionId, String title) {
        logger.info("Creating thread - sessionId: {}, title: {}", sessionId, title);
        
        String threadId = UUID.randomUUID().toString();
        ConversationThread thread = new ConversationThread(threadId, title, sessionId);
        
        logger.info("Thread created with ID: {}", threadId);
        
        return saveThread(thread)
                .doOnSuccess(result -> logger.info("Thread saved successfully"))
                .doOnError(error -> logger.error("Error saving thread: {}", error.getMessage()))
                .thenReturn(thread);
    }

    public Mono<ConversationThread> getThread(String threadId) {
        String key = "thread:" + threadId;
        
        return redis.opsForValue().get(key)
                .map(this::parseThread)
                .onErrorMap(throwable -> {
                    logger.error("Redis error getting thread {}: {}", threadId, throwable.getMessage());
                    return new RuntimeException("Redis connection failed", throwable);
                })
                .onErrorResume(throwable -> {
                    logger.warn("Thread not found or error occurred for thread {}: {}", threadId, throwable.getMessage());
                    return Mono.empty();
                });
    }

    public Flux<ConversationThread> getUserThreads(String sessionId) {
        String key = "user:threads:" + sessionId;
        logger.info("Getting user threads for session: {}", sessionId);
        logger.info("Redis key: {}", key);
        
        return redis.opsForSet().members(key)
                .doOnNext(threadId -> logger.info("Found thread ID: {}", threadId))
                .doOnComplete(() -> logger.info("Finished getting thread IDs from set"))
                .flatMap(threadId -> {
                    logger.info("Getting thread: {}", threadId);
                    return getThread(threadId)
                            .doOnNext(thread -> {
                                if (thread != null) {
                                    logger.info("Thread loaded - ID: {}, Messages: {}", thread.getId(), thread.getMessages() != null ? thread.getMessages().size() : "null");
                                } else {
                                    logger.warn("Thread is null for ID: {}", threadId);
                                }
                            })
                            .onErrorResume(throwable -> {
                                logger.error("Error loading thread {}: {}", threadId, throwable.getMessage());
                                return Mono.empty();
                            });
                })
                .filter(thread -> thread != null && thread.getStatus() != ConversationThread.ThreadStatus.DELETED)
                .sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .doOnComplete(() -> logger.info("Finished loading user threads"));
    }

    public Mono<ConversationThread> addMessage(String threadId, String content, ConversationThread.Message.MessageRole role) {
        logger.info("Adding message to thread: {}, content: {}, role: {}", threadId, content, role);
        return getThread(threadId)
                .flatMap(thread -> {
                    if (thread == null) {
                        logger.error("Thread not found: {}", threadId);
                        return Mono.error(new IllegalArgumentException("Thread not found"));
                    }
                    
                    ConversationThread.Message message = new ConversationThread.Message(content, role);
                    message.setId(UUID.randomUUID().toString());
                    
                    // messages 리스트가 null인 경우 초기화
                    if (thread.getMessages() == null) {
                        logger.info("Messages list is null, initializing");
                        thread.setMessages(new ArrayList<>());
                    }
                    
                    logger.info("Before adding message - current count: {}", thread.getMessages().size());
                    thread.getMessages().add(message);
                    logger.info("After adding message - new count: {}", thread.getMessages().size());
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
                .onErrorMap(throwable -> {
                    if (throwable instanceof IllegalArgumentException) {
                        return throwable; // Thread not found or corrupted - pass through
                    }
                    logger.error("Redis error deleting thread {}: {}", threadId, throwable.getMessage());
                    return new RuntimeException("Redis connection failed", throwable);
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
        
        logger.info("Saving thread - key: {}", key);
        logger.info("Thread JSON: {}", threadJson);
        logger.info("Thread TTL: {}", threadTtl);
        
        Duration ttlDuration;
        try {
            if (threadTtl.endsWith("d")) {
                int days = Integer.parseInt(threadTtl.replace("d", ""));
                ttlDuration = Duration.ofDays(days);
            } else {
                ttlDuration = Duration.parse("PT" + threadTtl.replace("d", "D"));
            }
        } catch (Exception e) {
            logger.error("TTL parsing error, using default 7 days: {}", e.getMessage());
            ttlDuration = Duration.ofDays(7);
        }
        
        return redis.opsForValue().set(key, threadJson, ttlDuration)
                .doOnSuccess(result -> logger.info("Thread saved to Redis"))
                .doOnError(error -> logger.error("Redis save error: {}", error.getMessage()))
                .then(redis.opsForSet().add("user:threads:" + thread.getSessionId(), thread.getId()))
                .doOnSuccess(result -> logger.info("Thread added to user set"))
                .doOnError(error -> logger.error("User set add error: {}", error.getMessage()))
                .thenReturn(thread);
    }

    private ConversationThread parseThread(String threadJson) {
        try {
            // 간단한 JSON 파싱 (실제로는 Jackson 사용 권장)
            String id = extractValue(threadJson, "id");
            String title = extractValue(threadJson, "title");
            String description = extractValue(threadJson, "description");
            String sessionId = extractValue(threadJson, "sessionId");
            String status = extractValue(threadJson, "status");
            String createdAt = extractValue(threadJson, "createdAt");
            String updatedAt = extractValue(threadJson, "updatedAt");
            
            ConversationThread thread = new ConversationThread(id, title, sessionId);
            thread.setDescription(description.isEmpty() ? null : description);
            thread.setStatus(ConversationThread.ThreadStatus.valueOf(status));
            thread.setCreatedAt(LocalDateTime.parse(createdAt));
            thread.setUpdatedAt(LocalDateTime.parse(updatedAt));
            
            // 메시지 배열 파싱
            String messagesJson = extractArrayValue(threadJson, "messages");
            logger.info("Messages JSON: {}", messagesJson);
            if (!messagesJson.isEmpty()) {
                List<ConversationThread.Message> messages = parseMessagesArray(messagesJson);
                logger.info("Parsed messages count: {}", messages.size());
                thread.setMessages(messages);
            } else {
                // 메시지가 없는 경우 빈 리스트로 초기화
                logger.info("No messages found, initializing empty list");
                thread.setMessages(new ArrayList<>());
            }
            
            return thread;
        } catch (Exception e) {
            logger.error("Error parsing thread: {}", e.getMessage());
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
        // 메시지 배열을 JSON 문자열로 변환
        String messagesJson = "[]";
        if (thread.getMessages() != null && !thread.getMessages().isEmpty()) {
            messagesJson = thread.getMessages().stream()
                .map(msg -> String.format(
                    "{\"id\":\"%s\",\"content\":\"%s\",\"role\":\"%s\",\"timestamp\":\"%s\"}",
                    msg.getId(),
                    msg.getContent().replace("\"", "\\\""),
                    msg.getRole().name(),
                    msg.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                ))
                .collect(Collectors.joining(",", "[", "]"));

        }
        
        return String.format(
            "{\"id\":\"%s\",\"title\":\"%s\",\"description\":\"%s\",\"sessionId\":\"%s\",\"status\":\"%s\",\"createdAt\":\"%s\",\"updatedAt\":\"%s\",\"messages\":%s}",
            thread.getId(),
            thread.getTitle().replace("\"", "\\\""),
            thread.getDescription() != null ? thread.getDescription().replace("\"", "\\\"") : "",
            thread.getSessionId(),
            thread.getStatus().name(),
            thread.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            thread.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            messagesJson
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
    
    private String extractArrayValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return "";
        start += pattern.length();
        
        // 배열의 시작과 끝 찾기
        int bracketCount = 0;
        int arrayStart = start;
        int arrayEnd = start;
        
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '[') {
                if (bracketCount == 0) arrayStart = i;
                bracketCount++;
            } else if (json.charAt(i) == ']') {
                bracketCount--;
                if (bracketCount == 0) {
                    arrayEnd = i + 1;
                    break;
                }
            }
        }
        
        return json.substring(arrayStart, arrayEnd);
    }
    
    private List<ConversationThread.Message> parseMessagesArray(String messagesJson) {
        List<ConversationThread.Message> messages = new ArrayList<>();
        
        if (messagesJson.equals("[]")) {
            return messages;
        }
        
        // 간단한 배열 파싱 (실제로는 Jackson 사용 권장)
        String content = messagesJson.substring(1, messagesJson.length() - 1); // [ ] 제거
        
        // 각 메시지 객체 파싱
        int start = 0;
        while (start < content.length()) {
            int objStart = content.indexOf("{", start);
            if (objStart == -1) break;
            
            int objEnd = findObjectEnd(content, objStart);
            if (objEnd == -1) break;
            
            String messageJson = content.substring(objStart, objEnd + 1);
            ConversationThread.Message message = parseMessage(messageJson);
            if (message != null) {
                messages.add(message);
            }
            
            start = objEnd + 1;
        }
        
        return messages;
    }
    
    private int findObjectEnd(String content, int start) {
        int braceCount = 0;
        for (int i = start; i < content.length(); i++) {
            if (content.charAt(i) == '{') {
                braceCount++;
            } else if (content.charAt(i) == '}') {
                braceCount--;
                if (braceCount == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    private ConversationThread.Message parseMessage(String messageJson) {
        try {
            String id = extractValue(messageJson, "id");
            String content = extractValue(messageJson, "content");
            String role = extractValue(messageJson, "role");
            String timestamp = extractValue(messageJson, "timestamp");
            
            ConversationThread.Message message = new ConversationThread.Message();
            message.setId(id);
            message.setContent(content);
            message.setRole(ConversationThread.Message.MessageRole.valueOf(role));
            message.setTimestamp(LocalDateTime.parse(timestamp));
            
            return message;
        } catch (Exception e) {
            logger.error("Error parsing message: {}", e.getMessage());
            return null;
        }
    }
}
