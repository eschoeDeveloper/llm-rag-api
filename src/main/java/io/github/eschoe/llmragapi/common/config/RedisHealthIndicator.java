package io.github.eschoe.llmragapi.common.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Redis 연결 상태를 actuator /health 에 노출.
 *
 * 이전엔 {@code HealthIndicator}(동기)를 구현하면서 내부에서 .block() 을 호출해
 * Reactor netty 이벤트 루프에서 실행될 경우 데드락 위험이 있었음.
 * {@link ReactiveHealthIndicator} 로 바꿔 actuator 가 직접 Mono 를 처리하도록 한다.
 */
@Component
public class RedisHealthIndicator implements ReactiveHealthIndicator {

    private final ReactiveRedisConnectionFactory redisConnectionFactory;

    public RedisHealthIndicator(ReactiveRedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Mono<Health> health() {
        return redisConnectionFactory.getReactiveConnection().ping()
                .map(pong -> "PONG".equals(pong)
                        ? Health.up().withDetail("redis", "Connected").build()
                        : Health.down().withDetail("redis", "Unexpected ping response: " + pong).build())
                .onErrorResume(e -> Mono.just(Health.down()
                        .withDetail("error", e.getMessage() != null ? e.getMessage() : "Redis connection failed")
                        .build()));
    }
}
