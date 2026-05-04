package io.github.eschoe.llmragapi.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class RateLimitingService {

    private final ReactiveStringRedisTemplate redis;
    
    @Value("${app.rate-limit.window-sec:60}")
    private int windowSeconds;
    
    @Value("${app.rate-limit.limit:10}")
    private int maxRequests;

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
                .onErrorReturn(true); // Redis 장애 시 fail-open (요청 허용)
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
