package io.github.eschoe.llmragapi.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final ReactiveRedisConnectionFactory redisWriterFactory;
    private final ReactiveRedisConnectionFactory redisReaderFactory;

    public RedisHealthIndicator(
            @Qualifier("redisWriterFactory") ReactiveRedisConnectionFactory redisWriterFactory,
            @Qualifier("redisReaderFactory") ReactiveRedisConnectionFactory redisReaderFactory) {
        this.redisWriterFactory = redisWriterFactory;
        this.redisReaderFactory = redisReaderFactory;
    }

    @Override
    public Health health() {
        return checkRedisConnections()
                .map(connected -> connected ? 
                    Health.up()
                        .withDetail("writer", "Connected")
                        .withDetail("reader", "Connected")
                        .build() :
                    Health.down()
                        .withDetail("writer", "Disconnected")
                        .withDetail("reader", "Disconnected")
                        .build())
                .onErrorReturn(Health.down()
                    .withDetail("error", "Redis connection failed")
                    .build())
                .block();
    }

    private Mono<Boolean> checkRedisConnections() {
        return Mono.zip(
            checkWriterConnection(),
            checkReaderConnection()
        ).map(tuple -> tuple.getT1() && tuple.getT2());
    }

    private Mono<Boolean> checkWriterConnection() {
        return redisWriterFactory.getReactiveConnection()
                .ping()
                .map(pong -> "PONG".equals(pong))
                .onErrorReturn(false)
                .doFinally(signalType -> redisWriterFactory.getReactiveConnection().close());
    }

    private Mono<Boolean> checkReaderConnection() {
        return redisReaderFactory.getReactiveConnection()
                .ping()
                .map(pong -> "PONG".equals(pong))
                .onErrorReturn(false)
                .doFinally(signalType -> redisReaderFactory.getReactiveConnection().close());
    }
}
