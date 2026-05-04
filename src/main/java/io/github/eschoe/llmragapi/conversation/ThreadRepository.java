package io.github.eschoe.llmragapi.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 스레드의 Redis 저장·조회·삭제 책임만 담는다.
 *
 * Redis 키 스킴:
 *   thread:{threadId}        → 스레드 직렬화 JSON (TTL = app.llm.thread-ttl)
 *   user:threads:{sessionId} → 사용자별 threadId set
 */
@Repository
public class ThreadRepository {

    private static final Logger log = LoggerFactory.getLogger(ThreadRepository.class);

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${app.llm.thread-ttl:7d}")
    private String threadTtl;

    public ThreadRepository(ReactiveStringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Mono<ConversationThread> save(ConversationThread thread) {
        String key = threadKey(thread.getId());
        Duration ttl = parseTtl(threadTtl);
        try {
            String json = objectMapper.writeValueAsString(thread);
            return redis.opsForValue().set(key, json, ttl)
                    .then(redis.opsForSet().add(userThreadsKey(thread.getSessionId()), thread.getId()))
                    .thenReturn(thread)
                    .doOnError(e -> log.error("Redis save failed for thread {}: {}", thread.getId(), e.getMessage()));
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("Failed to serialize thread " + thread.getId(), e));
        }
    }

    public Mono<ConversationThread> findById(String threadId) {
        return redis.opsForValue().get(threadKey(threadId))
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, ConversationThread.class));
                    } catch (Exception e) {
                        log.warn("Failed to parse thread {}: {}", threadId, e.getMessage());
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Redis read error for thread {}: {}", threadId, e.getMessage());
                    return Mono.empty();
                });
    }

    public Flux<ConversationThread> findBySession(String sessionId) {
        return redis.opsForSet().members(userThreadsKey(sessionId))
                .flatMap(this::findById)
                .filter(t -> t != null && t.getStatus() != ConversationThread.ThreadStatus.DELETED);
    }

    private String threadKey(String threadId) {
        return "thread:" + threadId;
    }

    private String userThreadsKey(String sessionId) {
        return "user:threads:" + sessionId;
    }

    private Duration parseTtl(String value) {
        try {
            if (value.endsWith("d")) return Duration.ofDays(Integer.parseInt(value.replace("d", "")));
            if (value.endsWith("h")) return Duration.ofHours(Integer.parseInt(value.replace("h", "")));
            if (value.endsWith("m")) return Duration.ofMinutes(Integer.parseInt(value.replace("m", "")));
            if (value.endsWith("s")) return Duration.ofSeconds(Integer.parseInt(value.replace("s", "")));
            return Duration.parse(value);
        } catch (Exception e) {
            log.warn("TTL parse failed for '{}', defaulting to 7d", value);
            return Duration.ofDays(7);
        }
    }
}
