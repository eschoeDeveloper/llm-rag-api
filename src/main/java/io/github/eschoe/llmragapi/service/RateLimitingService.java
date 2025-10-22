package io.github.eschoe.llmragapi.service;

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
                .flatMap(currentCount -> {
                    if (currentCount == null) {
                        // 첫 번째 요청
                        return redis.opsForValue().set(key, "1", Duration.ofSeconds(windowSeconds))
                                .thenReturn(true);
                    } else {
                        int count = Integer.parseInt(currentCount);
                        if (count >= maxRequests) {
                            return Mono.just(false);
                        } else {
                            return redis.opsForValue().increment(key)
                                    .thenReturn(true);
                        }
                    }
                })
                .onErrorReturn(true); // Redis 오류 시 허용
    }

    public Mono<Long> getRemainingRequests(String sessionId) {
        String key = "rate_limit:" + sessionId;
        
        return redis.opsForValue().get(key)
                .map(currentCount -> {
                    int count = currentCount != null ? Integer.parseInt(currentCount) : 0;
                    return (long) Math.max(0, maxRequests - count);
                })
                .onErrorReturn((long) maxRequests);
    }

    public Mono<Long> getResetTime(String sessionId) {
        String key = "rate_limit:" + sessionId;
        
        return redis.getExpire(key)
                .map(ttl -> ttl.toSeconds() > 0 ? System.currentTimeMillis() + (ttl.toSeconds() * 1000) : 0L)
                .onErrorReturn(0L);
    }
}
