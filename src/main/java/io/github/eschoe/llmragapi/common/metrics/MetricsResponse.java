package io.github.eschoe.llmragapi.common.metrics;

import java.util.Map;

/**
 * /api/admin/metrics 응답.
 *
 * counters 는 동적 카운터 이름이라 Map<String, Long> 유지가 자연스러움.
 * derived 는 고정 필드 — record 로 type-safe.
 */
public record MetricsResponse(
        Map<String, Long> counters,
        Derived derived
) {
    public record Derived(
            double hit_rate,
            long total_requests
    ) {}

    public static MetricsResponse from(Map<String, Long> counters) {
        long hit = counters.getOrDefault("cache_hit", 0L);
        long miss = counters.getOrDefault("cache_miss", 0L);
        double hitRate = (hit + miss) > 0 ? (double) hit / (hit + miss) : 0.0;
        return new MetricsResponse(counters, new Derived(hitRate, hit + miss));
    }
}
