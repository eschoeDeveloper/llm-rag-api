package io.github.eschoe.llmragapi.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class SearchHistoryService {

    private final ReactiveStringRedisTemplate redis;

    @Value("${app.llm.search-history-ttl:24h}")
    private String searchHistoryTtl;

    @Value("${app.llm.search-history-max:100}")
    private long maxSearchHistory;

    public SearchHistoryService(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Void> saveSearchHistory(String sessionId, String query, int resultCount) {
        String key = "search:hist:" + sessionId;
        String searchEntry = String.format("{\"query\":\"%s\",\"resultCount\":%d,\"timestamp\":\"%s\"}", 
                query.replace("\"", "\\\""), resultCount, Instant.now());

        return redis.opsForList().leftPush(key, searchEntry)
                .then(redis.opsForList().trim(key, 0, maxSearchHistory - 1))
                .then(redis.expire(key, Duration.parse("PT" + searchHistoryTtl.replace("h", "H").replace("m", "M").replace("s", "S"))))
                .then();
    }

    public Flux<SearchHistoryEntry> getSearchHistory(String sessionId, int limit) {
        String key = "search:hist:" + sessionId;
        
        return redis.opsForList().range(key, 0, limit - 1)
                .map(this::parseSearchEntry)
                .onErrorResume(throwable -> {
                    System.err.println("Redis connection error in getSearchHistory(): " + throwable.getMessage());
                    return Flux.empty();
                });
    }

    public Mono<Void> clearSearchHistory(String sessionId) {
        String key = "search:hist:" + sessionId;
        return redis.delete(key).then();
    }

    public Mono<Long> getSearchHistoryCount(String sessionId) {
        String key = "search:hist:" + sessionId;
        return redis.opsForList().size(key);
    }

    private SearchHistoryEntry parseSearchEntry(String entry) {
        try {
            // 간단한 JSON 파싱 (실제로는 Jackson 사용 권장)
            String query = extractValue(entry, "query");
            int resultCount = Integer.parseInt(extractValue(entry, "resultCount"));
            String timestamp = extractValue(entry, "timestamp");
            
            return new SearchHistoryEntry(query, resultCount, Instant.parse(timestamp));
        } catch (Exception e) {
            return new SearchHistoryEntry("파싱 오류", 0, Instant.now());
        }
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

        // Getters and Setters
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }

        public int getResultCount() { return resultCount; }
        public void setResultCount(int resultCount) { this.resultCount = resultCount; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    }
}
