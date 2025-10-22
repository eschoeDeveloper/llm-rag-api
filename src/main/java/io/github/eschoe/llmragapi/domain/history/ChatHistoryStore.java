package io.github.eschoe.llmragapi.domain.history;

import io.github.eschoe.llmragapi.util.SimpleDurationParser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class ChatHistoryStore {

    @Value("${app.llm.hist-max}") private long historyMax;
    @Value("${app.llm.hist-ttl-times}") private String historyTtlTimes;

    private final ReactiveStringRedisTemplate redis;
    private final SimpleDurationParser parser;

    public ChatHistoryStore(@Qualifier("redisWriterTemplate") ReactiveStringRedisTemplate redis, SimpleDurationParser parser) {
        this.redis = redis;
        this.parser = parser;
    }

    public Mono<Void> append(String sessionId, String messageJson) {

        String redisKey = "chat:hist:%s".formatted(sessionId);
        Duration ttlTimes = parser.parse(historyTtlTimes);

        return redis.opsForList().leftPush(redisKey, messageJson)
                .then(redis.opsForList().trim(redisKey, 0, historyMax - 1))
                .then(redis.expire(redisKey, ttlTimes))
                .then()
                .onErrorResume(throwable -> {
                    // Redis 연결 오류 시 로그만 출력하고 계속 진행
                    System.err.println("Redis connection error in append(): " + throwable.getMessage());
                    return Mono.empty();
                });

    }

    public Flux<String> recent(String sessionId, long rateLimit) {

        String redisKey = "chat:hist:%s".formatted(sessionId);

        // 최신 메시지들을 가져오기 위해 음수 인덱스 사용
        return redis.opsForList().range(redisKey, -rateLimit, -1)
                .onErrorResume(throwable -> {
                    // Redis 연결 오류 시 빈 Flux 반환 (첫 번째 요청에서 히스토리 없음)
                    System.err.println("Redis connection error in recent(): " + throwable.getMessage());
                    return Flux.empty();
                });

    }

    /**
     * 특정 세션의 대화 히스토리를 모두 삭제합니다.
     * 
     * @param sessionId 세션 ID
     * @return 삭제 완료 Mono
     */
    public Mono<Void> clearHistory(String sessionId) {
        String redisKey = "chat:hist:%s".formatted(sessionId);
        return redis.delete(redisKey).then();
    }

    /**
     * 세션의 대화 히스토리 개수를 반환합니다.
     * 
     * @param sessionId 세션 ID
     * @return 히스토리 개수
     */
    public Mono<Long> getHistoryCount(String sessionId) {
        String redisKey = "chat:hist:%s".formatted(sessionId);
        return redis.opsForList().size(redisKey);
    }

    /**
     * 대화 히스토리를 읽기 쉬운 형태로 포맷팅합니다.
     * 
     * @param sessionId 세션 ID
     * @param limit 최대 개수
     * @return 포맷팅된 대화 히스토리
     */
    public Mono<String> getFormattedHistory(String sessionId, long limit) {
        return recent(sessionId, limit)
                .collectList()
                .map(messages -> {
                    if (messages.isEmpty()) {
                        return "대화 히스토리가 없습니다.";
                    }
                    
                    StringBuilder formatted = new StringBuilder();
                    for (String message : messages) {
                        try {
                            // JSON 파싱 시도 (간단한 형태)
                            if (message.contains("\"role\":\"user\"")) {
                                String content = extractContent(message, "content");
                                formatted.append("사용자: ").append(content).append("\n");
                            } else if (message.contains("\"role\":\"assistant\"")) {
                                String content = extractContent(message, "content");
                                formatted.append("AI: ").append(content).append("\n");
                            }
                        } catch (Exception e) {
                            // JSON 파싱 실패 시 원본 메시지 사용
                            formatted.append(message).append("\n");
                        }
                    }
                    return formatted.toString();
                });
    }

    private String extractContent(String json, String key) {
        int start = json.indexOf("\"" + key + "\":\"") + key.length() + 4;
        int end = json.lastIndexOf("\"");
        if (start > key.length() + 3 && end > start) {
            return json.substring(start, end).replace("\\\"", "\"");
        }
        return json;
    }

}
