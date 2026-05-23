package io.github.eschoe.llmragapi.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 스레드 CRUD — 단일 책임.
 *
 * 책임 (이전엔 RAG 호출·history 복원까지 박혀있었음):
 *  - 생성 / 조회 / 목록 / 메시지 추가 / 제목 변경 / 보관 / 삭제
 *  - 메시지 max FIFO trim (Redis payload 비대 차단)
 *
 * Redis 영속화는 {@link ThreadRepository} 에 위임.
 * RAG/Ask 호출은 {@link ThreadChatOrchestrator} 에 위임.
 * history 복원은 {@link ThreadHistoryRestorer} 에 위임.
 */
@Service
public class ConversationThreadService {

    private static final Logger log = LoggerFactory.getLogger(ConversationThreadService.class);

    /**
     * 스레드당 메시지 최대 보존 개수.
     * 무한 누적 시 Redis payload 비대 + 매 chat 응답 직렬화 비용 폭증.
     * 토큰 예산 단계에서 어차피 1/4 만 사용하므로 100 이상 보존은 가성비 0.
     */
    private static final int MAX_MESSAGES_PER_THREAD = 100;

    private final ThreadRepository repository;

    public ConversationThreadService(ThreadRepository repository) {
        this.repository = repository;
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

    /** 메시지 추가 (메타 없음 — USER 메시지 / 시스템 메시지 등). */
    public Mono<ConversationThread> addMessage(String threadId,
                                               String content,
                                               ConversationThread.Message.MessageRole role) {
        return addMessage(threadId, content, role, null);
    }

    /**
     * 메시지 추가 + metadata 첨부 (ASSISTANT 의 citations 보존 등).
     * trim 으로 100개 초과분 FIFO 제거.
     */
    public Mono<ConversationThread> addMessage(String threadId,
                                               String content,
                                               ConversationThread.Message.MessageRole role,
                                               Map<String, Object> metadata) {
        return mutateThread(threadId, thread -> {
            ConversationThread.Message msg = new ConversationThread.Message(content, role);
            msg.setId(UUID.randomUUID().toString());
            if (metadata != null) msg.setMetadata(metadata);
            if (thread.getMessages() == null) thread.setMessages(new ArrayList<>());
            thread.getMessages().add(msg);
            trimMessages(thread);
        });
    }

    public Mono<ConversationThread> updateThreadTitle(String threadId, String newTitle) {
        return mutateThread(threadId, t -> t.setTitle(newTitle));
    }

    public Mono<Void> archiveThread(String threadId) {
        return mutateThread(threadId, t -> t.setStatus(ConversationThread.ThreadStatus.ARCHIVED)).then();
    }

    public Mono<Void> deleteThread(String threadId) {
        return mutateThread(threadId, t -> t.setStatus(ConversationThread.ThreadStatus.DELETED)).then();
    }

    // ---------- 내부 ----------

    /** 메시지 수가 한도 초과 시 가장 오래된 것부터 제거 (FIFO). */
    private void trimMessages(ConversationThread thread) {
        List<ConversationThread.Message> msgs = thread.getMessages();
        if (msgs == null) return;
        int excess = msgs.size() - MAX_MESSAGES_PER_THREAD;
        if (excess > 0) {
            msgs.subList(0, excess).clear();
        }
    }

    /** find → mutate → save 공통 패턴. updatedAt 자동 갱신. */
    private Mono<ConversationThread> mutateThread(String threadId,
                                                  Consumer<ConversationThread> mutator) {
        return repository.findById(threadId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Thread not found: " + threadId)))
                .flatMap(thread -> {
                    mutator.accept(thread);
                    thread.setUpdatedAt(LocalDateTime.now());
                    return repository.save(thread);
                });
    }
}
