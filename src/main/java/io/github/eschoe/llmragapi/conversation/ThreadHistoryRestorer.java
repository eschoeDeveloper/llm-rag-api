package io.github.eschoe.llmragapi.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eschoe.llmragapi.chat.history.ChatHistoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 채팅 히스토리(Redis) 에서 대화 메시지 가져와 새 ConversationThread 로 복원.
 *
 * 사용처: 사용자가 sessionId 만으로 익명 채팅하다가 스레드로 영속하고 싶을 때.
 *
 * ConversationThreadService 에서 분리한 이유: history 파싱 + 메시지 변환 책임이
 * CRUD 와 별개. ChatHistoryStore JSON 포맷이 바뀌면 여기만 수정.
 */
@Service
public class ThreadHistoryRestorer {

    private static final Logger log = LoggerFactory.getLogger(ThreadHistoryRestorer.class);
    private static final int MAX_HISTORY_TO_LOAD = 50;

    private final ChatHistoryStore chatHistoryStore;
    private final ObjectMapper objectMapper;
    private final ThreadRepository repository;

    public ThreadHistoryRestorer(ChatHistoryStore chatHistoryStore,
                                 ObjectMapper objectMapper,
                                 ThreadRepository repository) {
        this.chatHistoryStore = chatHistoryStore;
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    /**
     * sessionId 의 최근 N 개 메시지를 가져와 threadId 로 새 스레드 생성.
     * 파싱 실패한 메시지는 silently skip.
     */
    public Mono<ConversationThread> loadThreadFromHistory(String threadId, String sessionId) {
        return chatHistoryStore.recent(sessionId, MAX_HISTORY_TO_LOAD)
                .collectList()
                .flatMap(historyJsonList -> {
                    ConversationThread thread = new ConversationThread(threadId, "Loaded from History", sessionId);
                    List<ConversationThread.Message> messages = new ArrayList<>();
                    for (String json : historyJsonList) {
                        ConversationThread.Message m = parseHistoryMessage(json);
                        if (m != null) messages.add(m);
                    }
                    thread.setMessages(messages);
                    thread.setUpdatedAt(LocalDateTime.now());
                    return repository.save(thread);
                });
    }

    /** PromptSerializer.toMessageJson 으로 직렬화된 메시지를 다시 ConversationThread.Message 로 변환. */
    private ConversationThread.Message parseHistoryMessage(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            String role = String.valueOf(map.getOrDefault("role", "user"));
            String content = String.valueOf(map.getOrDefault("content", ""));
            String timestamp = String.valueOf(map.getOrDefault("timestamp", LocalDateTime.now().toString()));

            ConversationThread.Message m = new ConversationThread.Message();
            m.setId(UUID.randomUUID().toString());
            m.setContent(content);
            try {
                m.setRole(ConversationThread.Message.MessageRole.valueOf(role.toUpperCase()));
            } catch (IllegalArgumentException e) {
                m.setRole(ConversationThread.Message.MessageRole.SYSTEM);
            }
            try {
                m.setTimestamp(LocalDateTime.parse(timestamp));
            } catch (Exception e) {
                m.setTimestamp(LocalDateTime.now());
            }
            return m;
        } catch (JsonProcessingException e) {
            log.warn("history message parse failed: {}", e.getMessage());
            return null;
        }
    }
}
