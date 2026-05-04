package io.github.eschoe.llmragapi.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eschoe.llmragapi.common.helper.SimpleDurationParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 사용자별 검색 이력 Redis list 저장.
 *
 * Redis 키:
 *   search:hist:{sessionId}  → JSON 항목 list (LPUSH+LTRIM 으로 max 유지, TTL 갱신)
 */
@Service
public class SearchHistoryService {

    private static final Logger log = LoggerFactory.getLogger(SearchHistoryService.class);

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SimpleDurationParser durationParser;

    @Value("${app.llm.search-history-ttl:24h}")
    private String searchHistoryTtl;

    @Value("${app.llm.search-history-max:100}")
    private long maxSearchHistory;

    public SearchHistoryService(ReactiveStringRedisTemplate redis,
                                ObjectMapper objectMapper,
                                SimpleDurationParser durationParser) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.durationParser = durationParser;
    }

    public Mono<Void> saveSearchHistory(String sessionId, String query, int resultCount) {
        String key = "search:hist:" + sessionId;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("resultCount", resultCount);
        payload.put("timestamp", Instant.now().toString());

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize search history entry: {}", e.getMessage());
            return Mono.empty();
        }

        Duration ttl = parseTtl(searchHistoryTtl);
        return redis.opsForList().leftPush(key, json)
                .then(redis.opsForList().trim(key, 0, maxSearchHistory - 1))
                .then(redis.expire(key, ttl))
                .then();
    }

    public Flux<SearchHistoryEntry> getSearchHistory(String sessionId, int limit) {
        String key = "search:hist:" + sessionId;
        return redis.opsForList().range(key, 0, limit - 1)
                .map(this::parseSearchEntry)
                .onErrorResume(t -> {
                    log.warn("Redis read error in getSearchHistory: {}", t.getMessage());
                    return Flux.empty();
                });
    }

    public Mono<Void> clearSearchHistory(String sessionId) {
        return redis.delete("search:hist:" + sessionId).then();
    }

    public Mono<Long> getSearchHistoryCount(String sessionId) {
        return redis.opsForList().size("search:hist:" + sessionId);
    }

    @SuppressWarnings("unchecked")
    private SearchHistoryEntry parseSearchEntry(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            String query = String.valueOf(map.getOrDefault("query", ""));
            int resultCount = ((Number) map.getOrDefault("resultCount", 0)).intValue();
            Object ts = map.get("timestamp");
            Instant timestamp = ts != null ? Instant.parse(ts.toString()) : Instant.now();
            return new SearchHistoryEntry(query, resultCount, timestamp);
        } catch (Exception e) {
            log.warn("Failed to parse search history entry: {}", e.getMessage());
            return new SearchHistoryEntry("파싱 오류", 0, Instant.now());
        }
    }

    private Duration parseTtl(String value) {
        try {
            return durationParser.parse(value);
        } catch (Exception e) {
            return Duration.ofHours(24);
        }
    }

    public static class SearchHistoryEntry {
        private String query;
        private int resultCount;
        private Instant timestamp;

        public SearchHistoryEntry() {}

        public SearchHistoryEntry(String query, int resultCount, Instant timestamp) {
            this.query = query;
            this.resultCount = resultCount;
            this.timestamp = timestamp;
        }

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }

        public int getResultCount() { return resultCount; }
        public void setResultCount(int resultCount) { this.resultCount = resultCount; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    }
}
