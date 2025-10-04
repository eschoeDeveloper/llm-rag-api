package io.github.eschoe.llmragapi.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final ReactiveRedisConnectionFactory redisConnectionFactory;

    public RedisHealthIndicator(@Qualifier("redisWriterFactory") ReactiveRedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        return checkRedisConnection()
                .map(connected -> connected ? 
                    Health.up()
                        .withDetail("redis", "Connected")
                        .build() :
                    Health.down()
                        .withDetail("redis", "Disconnected")
                        .build())
                .onErrorReturn(Health.down()
                    .withDetail("error", "Redis connection failed")
                    .build())
                .block();
    }

    private Mono<Boolean> checkRedisConnection() {
        return redisConnectionFactory.getReactiveConnection()
                .ping()
                .map(pong -> "PONG".equals(pong))
                .onErrorReturn(false)
                .doFinally(signalType -> redisConnectionFactory.getReactiveConnection().close());
    }
}
