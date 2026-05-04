package io.github.eschoe.llmragapi.chat.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eschoe.llmragapi.common.helper.SimpleDurationParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * 사용자 세션별 대화 이력 저장.
 *
 * Redis 키:
 *   chat:hist:{sessionId}  → JSON 메시지 list (LPUSH 로 최신이 head, LTRIM 으로 max 유지)
 *
 * 메시지 형식: {"role": "user|assistant", "content": "...", "timestamp": "ISO-8601"}
 * (PromptSerializer.toMessageJson 으로 직렬화)
 */
@Service
public class ChatHistoryStore {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryStore.class);

    @Value("${app.llm.hist-max:50}") private long historyMax;
    @Value("${app.llm.hist-ttl-times:48h}") private String historyTtlTimes;

    private final ReactiveStringRedisTemplate redis;
    private final SimpleDurationParser parser;
    private final ObjectMapper objectMapper;

    public ChatHistoryStore(@Qualifier("redisWriterTemplate") ReactiveStringRedisTemplate redis,
                            SimpleDurationParser parser,
                            ObjectMapper objectMapper) {
        this.redis = redis;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    public Mono<Void> append(String sessionId, String messageJson) {
        String redisKey = "chat:hist:%s".formatted(sessionId);
        Duration ttl = parser.parse(historyTtlTimes);

        return redis.opsForList().leftPush(redisKey, messageJson)
                .then(redis.opsForList().trim(redisKey, 0, historyMax - 1))
                .then(redis.expire(redisKey, ttl))
                .then()
                .onErrorResume(t -> {
                    log.warn("Redis append failed (continuing): {}", t.getMessage());
                    return Mono.empty();
                });
    }

    public Flux<String> recent(String sessionId, long limit) {
        String redisKey = "chat:hist:%s".formatted(sessionId);
        // LPUSH 라 head 가 최신. 0..limit-1 → "최근 N개" (head 쪽부터).
        // 출력 순서는 오래된 → 최신 이라야 LLM 컨텍스트 가독성 좋음 → flatMapIterable 시점에서 reverse.
        // collectList 후 reverse 하면 임시 리스트 1번만 만들고 끝 (작은 N=50 이라 비용 무시 가능).
        return redis.opsForList().range(redisKey, 0, limit - 1)
                .collectList()
                .flatMapIterable(list -> {
                    java.util.Collections.reverse(list);
                    return list;
                })
                .onErrorResume(t -> {
                    log.warn("Redis read failed (returning empty): {}", t.getMessage());
                    return Flux.empty();
                });
    }

    public Mono<Void> clearHistory(String sessionId) {
        return redis.delete("chat:hist:%s".formatted(sessionId)).then();
    }

    public Mono<Long> getHistoryCount(String sessionId) {
        return redis.opsForList().size("chat:hist:%s".formatted(sessionId));
    }

    /**
     * 사람이 읽기 쉬운 형식으로 포맷:
     *   사용자: 안녕
     *   AI: 안녕하세요
     *
     * Jackson 으로 role/content 추출. 파싱 실패 시 raw 메시지 그대로 표시.
     */
    public Mono<String> getFormattedHistory(String sessionId, long limit) {
        return recent(sessionId, limit)
                .collectList()
                .map(this::format);
    }

    private String format(java.util.List<String> messages) {
        if (messages.isEmpty()) return "대화 히스토리가 없습니다.";

        StringBuilder out = new StringBuilder();
        for (String json : messages) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(json, Map.class);
                String role = String.valueOf(map.getOrDefault("role", ""));
                String content = String.valueOf(map.getOrDefault("content", ""));
                String prefix = switch (role) {
                    case "user" -> "사용자: ";
                    case "assistant" -> "AI: ";
                    default -> role + ": ";
                };
                out.append(prefix).append(content).append('\n');
            } catch (Exception e) {
                out.append(json).append('\n');
            }
        }
        return out.toString();
    }
}
