package io.github.eschoe.llmragapi.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private final ReactiveStringRedisTemplate redis;

    @Value("${app.rate-limit.window-sec:60}")
    private int windowSeconds;

    @Value("${app.rate-limit.limit:10}")
    private int maxRequests;

    // Redis 장애 시 fallback 용 인스턴스 로컬 fixed-window 카운터.
    // 완전 fail-open(모든 요청 허용) 대신, Redis 가 죽어도 인스턴스 단위 한도는 강제 →
    // 익명 endpoint 의 LLM 비용 폭주를 억제. 다중 dyno 환경에선 전체 한도가 인스턴스
    // 수만큼 느슨해지지만 무방비보다 우수. 장애는 일시적이므로 LOCAL_MAX_ENTRIES 도달 시
    // 만료 엔트리를 sweep 해 메모리를 bound.
    private record LocalWindow(int count, long windowStartMs) {}
    private final ConcurrentHashMap<String, LocalWindow> localCounters = new ConcurrentHashMap<>();
    private static final int LOCAL_MAX_ENTRIES = 10_000;

    public RateLimitingService(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Boolean> isAllowed(String sessionId) {
        String key = "rate_limit:" + sessionId;

        return redis.opsForValue().get(key)
                .defaultIfEmpty("0")
                .map(this::parseCount)
                .flatMap(count -> {
                    if (count == 0) {
                        return redis.opsForValue().set(key, "1", Duration.ofSeconds(windowSeconds))
                                .thenReturn(true);
                    } else if (count >= maxRequests) {
                        return Mono.just(false);
                    } else {
                        return redis.opsForValue().increment(key).thenReturn(true);
                    }
                })
                // Redis 장애 시 완전 fail-open 대신 인스턴스 로컬 한도로 degrade.
                .onErrorResume(e -> Mono.just(isAllowedLocal(sessionId)));
    }

    /** Redis 장애 fallback — 인스턴스 로컬 fixed-window 카운터. 한도 내면 true, 초과면 false. */
    private boolean isAllowedLocal(String sessionId) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;

        if (localCounters.size() > LOCAL_MAX_ENTRIES) {
            localCounters.entrySet().removeIf(e -> now - e.getValue().windowStartMs() >= windowMs);
        }

        LocalWindow w = localCounters.compute(sessionId, (k, prev) ->
                (prev == null || now - prev.windowStartMs() >= windowMs)
                        ? new LocalWindow(1, now)
                        : new LocalWindow(prev.count() + 1, prev.windowStartMs()));

        return w.count() <= maxRequests;
    }

    public Mono<Long> getRemainingRequests(String sessionId) {
        String key = "rate_limit:" + sessionId;

        return redis.opsForValue().get(key)
                .map(this::parseCount)
                .map(count -> (long) Math.max(0, maxRequests - count))
                .defaultIfEmpty((long) maxRequests)
                .onErrorReturn((long) maxRequests);
    }

    public Mono<Long> getResetTime(String sessionId) {
        String key = "rate_limit:" + sessionId;
        
        return redis.getExpire(key)
                .map(ttl -> ttl.toSeconds() > 0 ? System.currentTimeMillis() + (ttl.toSeconds() * 1000) : 0L)
                .onErrorReturn(0L);
    }

    /** 손상된 Redis 값 (NumberFormatException) 시 0 fallback. 카운터가 리셋되는 것은 fail-open 의도와 일치. */
    private int parseCount(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
