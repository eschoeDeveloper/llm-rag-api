package io.github.eschoe.llmragapi.util;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis 연결 테스트 유틸리티
 */
@Component
public class RedisConnectionTest {

    private final ReactiveStringRedisTemplate redisWriter;
    private final ReactiveStringRedisTemplate redisReader;

    public RedisConnectionTest(@Qualifier("redisWriterTemplate") ReactiveStringRedisTemplate redisWriter,
                              @Qualifier("redisReaderTemplate") ReactiveStringRedisTemplate redisReader) {
        this.redisWriter = redisWriter;
        this.redisReader = redisReader;
    }

    /**
     * Redis Writer 연결 테스트
     */
    public Mono<String> testWriterConnection() {
        String testKey = "test:writer:" + System.currentTimeMillis();
        String testValue = "writer-test-value";
        
        return redisWriter.opsForValue().set(testKey, testValue, Duration.ofSeconds(10))
                .then(redisWriter.opsForValue().get(testKey))
                .map(value -> "Writer 연결 성공: " + value)
                .doOnSuccess(result -> System.out.println("✅ " + result))
                .doOnError(error -> System.err.println("❌ Writer 연결 실패: " + error.getMessage()))
                .onErrorReturn("Writer 연결 실패");
    }

    /**
     * Redis Reader 연결 테스트
     */
    public Mono<String> testReaderConnection() {
        String testKey = "test:reader:" + System.currentTimeMillis();
        
        return redisReader.opsForValue().get(testKey)
                .map(value -> "Reader 연결 성공: " + (value != null ? value : "null"))
                .doOnSuccess(result -> System.out.println("✅ " + result))
                .doOnError(error -> System.err.println("❌ Reader 연결 실패: " + error.getMessage()))
                .onErrorReturn("Reader 연결 실패");
    }

    /**
     * 전체 Redis 연결 테스트
     */
    public Mono<String> testAllConnections() {
        return testWriterConnection()
                .flatMap(writerResult -> 
                    testReaderConnection()
                            .map(readerResult -> 
                                String.format("Writer: %s | Reader: %s", writerResult, readerResult)
                            )
                );
    }
}

