package io.github.eschoe.llmragapi.common.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 운영 메트릭 — Redis 카운터 기반.
 *
 * 키 스킴:
 *   metrics:counter:{name}     → 누적 카운터 (INCR)
 *
 * 카운터 종류:
 *   - cache_hit / cache_miss   → LLM 응답 캐시 적중률
 *   - llm_calls                → 외부 LLM 호출 횟수 (가격·사용량)
 *   - retrieval_empty          → 검색 결과 비어 있는 RAG 호출 (품질 신호)
 *   - rerank_calls             → Cohere reranker 호출 (비용)
 *   - vision_pages             → Vision 추출 페이지 (비용)
 *
 * 모든 INCR 은 fail-open — Redis 장애 시에도 RAG 흐름 차단 안 함.
 */
@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);
    private static final String PREFIX = "metrics:counter:";

    private final ReactiveStringRedisTemplate redis;

    public MetricsService(@Qualifier("redisWriterTemplate") ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Long> incr(String name) {
        return redis.opsForValue().increment(PREFIX + name)
                .onErrorResume(t -> {
                    log.debug("metrics incr failed (fail-open): {} → {}", name, t.getMessage());
                    return Mono.just(0L);
                });
    }

    public Mono<Long> incr(String name, long by) {
        return redis.opsForValue().increment(PREFIX + name, by)
                .onErrorResume(t -> {
                    log.debug("metrics incr failed (fail-open): {} → {}", name, t.getMessage());
                    return Mono.just(0L);
                });
    }

    /** 모든 카운터 일괄 조회. 누락 키는 0. */
    public Mono<Map<String, Long>> snapshot() {
        List<String> names = List.of(
                "cache_hit", "cache_miss",
                "llm_calls", "retrieval_empty",
                "rerank_calls", "vision_pages"
        );
        return Flux.fromIterable(names)
                .flatMap(n -> redis.opsForValue().get(PREFIX + n)
                        .defaultIfEmpty("0")
                        .map(v -> Map.entry(n, parseLong(v))))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(m -> {
                    Map<String, Long> filled = new HashMap<>();
                    names.forEach(n -> filled.put(n, m.getOrDefault(n, 0L)));
                    return filled;
                })
                .onErrorReturn(zeros(names));
    }

    private long parseLong(String v) {
        try { return Long.parseLong(v); } catch (Exception e) { return 0L; }
    }

    private Map<String, Long> zeros(List<String> names) {
        Map<String, Long> m = new HashMap<>();
        names.forEach(n -> m.put(n, 0L));
        return m;
    }
}
