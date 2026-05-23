package io.github.eschoe.llmragapi.conversation;

import io.github.eschoe.llmragapi.ask.AskRequest;
import io.github.eschoe.llmragapi.ask.AskService;
import io.github.eschoe.llmragapi.chat.ChatRequest;
import io.github.eschoe.llmragapi.chat.ChatService;
import io.github.eschoe.llmragapi.rag.config.RAGConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 스레드 단위 LLM 호출 오케스트레이션.
 *
 * 책임:
 *  - mode("chat"|"ask")에 따라 ChatService.chatEnhanced 또는 AskService.askEnhanced 호출
 *  - 호출 전후로 USER / ASSISTANT 메시지를 스레드에 영속 (CRUD 는 ConversationThreadService 에 위임)
 *  - LLM 실패 시에도 USER 메시지는 보존 + ASSISTANT 는 에러 텍스트로 추가 (입력 유실 방지)
 *
 * ConversationThreadService 에서 분리한 이유: 스레드 CRUD 와 RAG/Ask 호출은 다른 변경 축.
 * 검색·LLM 정책 변경은 여기, 저장 스킴 변경은 CRUD 클래스 — 변경 영향 격리.
 */
@Service
public class ThreadChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ThreadChatOrchestrator.class);

    private final ConversationThreadService threadService;
    private final ChatService chatService;
    private final AskService askService;

    public ThreadChatOrchestrator(ConversationThreadService threadService,
                                  ChatService chatService,
                                  AskService askService) {
        this.threadService = threadService;
        this.chatService = chatService;
        this.askService = askService;
    }

    /**
     * 스레드 안에서 사용자 query 보내고 LLM 응답 받기.
     *
     * 흐름:
     *   1. 스레드 존재 확인 (addMessage 가 mutateThread 로 처리)
     *   2. USER 메시지 추가 (저장 1회)
     *   3. mode 따라 RAG (chat) 또는 LLM 단독 (ask) 호출
     *   4. ASSISTANT 메시지 + metadata(citations) 추가 (저장 2회)
     *   5. 갱신된 ConversationThread 전체 반환
     *
     * sessionId 는 스레드 본인 sessionId 사용 — 헤더 변동 흡수.
     */
    public Mono<ConversationThread> threadChat(String threadId,
                                               String query,
                                               String mode,
                                               RAGConfig config,
                                               String customPrompt) {
        if (query == null || query.isBlank()) {
            return Mono.error(new IllegalArgumentException("query is required"));
        }
        boolean useRag = !"ask".equalsIgnoreCase(mode);

        return threadService.getThread(threadId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Thread not found: " + threadId)))
                .flatMap(thread -> {
                    String sessionId = thread.getSessionId();
                    return threadService.addMessage(threadId, query, ConversationThread.Message.MessageRole.USER, null)
                            .flatMap(saved -> invokeLlm(query, config, sessionId, useRag, customPrompt)
                                    .onErrorResume(e -> {
                                        log.warn("[Thread] LLM 호출 실패: {}", e.getMessage());
                                        Map<String, Object> errMeta = new HashMap<>();
                                        errMeta.put("error", e.getMessage());
                                        return Mono.just(new LlmOutcome(
                                                "죄송합니다. 응답을 생성하는 중 오류가 발생했습니다.", errMeta));
                                    })
                                    .flatMap(outcome -> threadService.addMessage(
                                            threadId, outcome.content(),
                                            ConversationThread.Message.MessageRole.ASSISTANT,
                                            outcome.metadata())));
                });
    }

    private Mono<LlmOutcome> invokeLlm(String query, RAGConfig config, String sessionId,
                                       boolean useRag, String customPrompt) {
        if (useRag) {
            ChatRequest req = new ChatRequest(query, null, config, sessionId);
            req.setCustomPrompt(customPrompt);
            return chatService.chatEnhanced(req)
                    .map(r -> new LlmOutcome(r.getContent(), r.getMetadata()));
        }
        AskRequest req = new AskRequest(query, config, sessionId);
        req.setCustomPrompt(customPrompt);
        return askService.askEnhanced(req)
                .map(r -> new LlmOutcome(r.getContent(), r.getMetadata()));
    }

    private record LlmOutcome(String content, Map<String, Object> metadata) {}
}
