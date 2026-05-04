package io.github.eschoe.llmragapi.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eschoe.llmragapi.ask.AskRequest;
import io.github.eschoe.llmragapi.ask.AskResponse;
import io.github.eschoe.llmragapi.ask.AskService;
import io.github.eschoe.llmragapi.chat.ChatRequest;
import io.github.eschoe.llmragapi.chat.ChatResponse;
import io.github.eschoe.llmragapi.chat.ChatService;
import io.github.eschoe.llmragapi.chat.history.ChatHistoryStore;
import io.github.eschoe.llmragapi.rag.config.RAGConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 스레드 비즈니스 로직.
 *
 * 책임:
 *  - 스레드 CRUD (생성, 메시지 추가, 제목 변경, 보관, 삭제)
 *  - 채팅 히스토리에서 스레드 복원
 *
 * Redis 저장·조회는 {@link ThreadRepository} 에 위임.
 */
@Service
public class ConversationThreadService {

    private static final Logger log = LoggerFactory.getLogger(ConversationThreadService.class);

    private final ThreadRepository repository;
    private final ChatHistoryStore chatHistoryStore;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final AskService askService;

    public ConversationThreadService(ThreadRepository repository,
                                     ChatHistoryStore chatHistoryStore,
                                     ObjectMapper objectMapper,
                                     ChatService chatService,
                                     AskService askService) {
        this.repository = repository;
        this.chatHistoryStore = chatHistoryStore;
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.askService = askService;
    }

    public Mono<ConversationThread> createThread(String sessionId, String title) {
        String threadId = UUID.randomUUID().toString();
        ConversationThread thread = new ConversationThread(threadId, title, sessionId);
        return repository.save(thread);
    }

    public Mono<ConversationThread> getThread(String threadId) {
        return repository.findById(threadId);
    }

    public Flux<ConversationThread> getUserThreads(String sessionId) {
        return repository.findBySession(sessionId)
                .sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));
    }

    public Mono<ConversationThread> addMessage(String threadId,
                                               String content,
                                               ConversationThread.Message.MessageRole role) {
        return mutateThread(threadId, thread -> {
            ConversationThread.Message msg = new ConversationThread.Message(content, role);
            msg.setId(UUID.randomUUID().toString());
            if (thread.getMessages() == null) thread.setMessages(new ArrayList<>());
            thread.getMessages().add(msg);
        });
    }

    /**
     * 스레드 내에서 메시지 보내고 LLM 응답 받기 — 한 번의 트랜잭션처럼.
     *
     * 흐름:
     *   1. 스레드 존재 확인
     *   2. USER 메시지 추가
     *   3. mode 에 따라 ChatService.chatEnhanced (RAG) 또는 AskService.askEnhanced (RAG 없음) 호출
     *      - sessionId 는 스레드의 sessionId 사용 (요청 sessionId 와 다를 수 있는 헤더 차이 흡수)
     *   4. 응답 content + metadata 를 ASSISTANT 메시지에 첨부 (citations 보존)
     *   5. 갱신된 스레드 반환
     *
     * 실패 처리: LLM 호출 실패해도 USER 메시지는 이미 저장됨 — ASSISTANT 메시지만 에러 텍스트로 추가.
     * 이렇게 하면 사용자 입력은 유실되지 않고, 화면에서 "응답 실패" 가 자연스럽게 표시됨.
     */
    public Mono<ConversationThread> threadChat(String threadId,
                                               String query,
                                               String mode,
                                               RAGConfig config) {
        if (query == null || query.isBlank()) {
            return Mono.error(new IllegalArgumentException("query is required"));
        }
        boolean useRag = !"ask".equalsIgnoreCase(mode);

        return repository.findById(threadId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Thread not found: " + threadId)))
                .flatMap(thread -> {
                    String sessionId = thread.getSessionId();

                    ConversationThread.Message userMsg = new ConversationThread.Message(query, ConversationThread.Message.MessageRole.USER);
                    userMsg.setId(UUID.randomUUID().toString());
                    if (thread.getMessages() == null) thread.setMessages(new ArrayList<>());
                    thread.getMessages().add(userMsg);
                    thread.setUpdatedAt(LocalDateTime.now());

                    return repository.save(thread)
                            .flatMap(saved -> invokeLlm(query, mode, config, sessionId, useRag)
                                    .onErrorResume(e -> {
                                        log.warn("[Thread] LLM 호출 실패: {}", e.getMessage());
                                        Map<String, Object> errMeta = new HashMap<>();
                                        errMeta.put("error", e.getMessage());
                                        return Mono.just(new LlmOutcome("죄송합니다. 응답을 생성하는 중 오류가 발생했습니다.", errMeta));
                                    })
                                    .flatMap(outcome -> appendAssistant(saved, outcome)));
                });
    }

    private Mono<LlmOutcome> invokeLlm(String query, String mode, RAGConfig config, String sessionId, boolean useRag) {
        if (useRag) {
            ChatRequest req = new ChatRequest(query, null, config, sessionId);
            return chatService.chatEnhanced(req)
                    .map(r -> new LlmOutcome(r.getContent(), r.getMetadata()));
        }
        AskRequest req = new AskRequest(query, config, sessionId);
        return askService.askEnhanced(req)
                .map(r -> new LlmOutcome(r.getContent(), r.getMetadata()));
    }

    private Mono<ConversationThread> appendAssistant(ConversationThread thread, LlmOutcome outcome) {
        ConversationThread.Message assistantMsg = new ConversationThread.Message(
                outcome.content(),
                ConversationThread.Message.MessageRole.ASSISTANT);
        assistantMsg.setId(UUID.randomUUID().toString());
        if (outcome.metadata() != null) {
            assistantMsg.setMetadata(outcome.metadata());
        }
        thread.getMessages().add(assistantMsg);
        thread.setUpdatedAt(LocalDateTime.now());
        return repository.save(thread);
    }

    private record LlmOutcome(String content, Map<String, Object> metadata) {}

    public Mono<ConversationThread> updateThreadTitle(String threadId, String newTitle) {
        return mutateThread(threadId, t -> t.setTitle(newTitle));
    }

    public Mono<Void> archiveThread(String threadId) {
        return mutateThread(threadId, t -> t.setStatus(ConversationThread.ThreadStatus.ARCHIVED)).then();
    }

    public Mono<Void> deleteThread(String threadId) {
        return mutateThread(threadId, t -> t.setStatus(ConversationThread.ThreadStatus.DELETED)).then();
    }

    /**
     * 채팅 히스토리에서 메시지 가져와 새 스레드로 복원.
     * 기존 사용처: 스레드 미생성 상태에서 대화 이어가기.
     */
    public Mono<ConversationThread> loadThreadFromHistory(String threadId, String sessionId) {
        return chatHistoryStore.recent(sessionId, 50)
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

    private Mono<ConversationThread> mutateThread(String threadId,
                                                  java.util.function.Consumer<ConversationThread> mutator) {
        return repository.findById(threadId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Thread not found: " + threadId)))
                .flatMap(thread -> {
                    mutator.accept(thread);
                    thread.setUpdatedAt(LocalDateTime.now());
                    return repository.save(thread);
                });
    }

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
