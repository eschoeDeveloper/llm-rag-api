package io.github.eschoe.llmragapi.llm.cache;

import io.github.eschoe.llmragapi.common.helper.SimpleDurationParser;
import io.github.eschoe.llmragapi.common.metrics.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmCacheServiceStampedeTest {

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
    private LlmCacheService cache;

    @BeforeEach
    void setUp() {
        ReactiveStringRedisTemplate redisW = mock(ReactiveStringRedisTemplate.class);
        ReactiveStringRedisTemplate redisR = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> valueOpsW = mock(ReactiveValueOperations.class);
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> valueOpsR = mock(ReactiveValueOperations.class);

        when(redisW.opsForValue()).thenReturn(valueOpsW);
        when(redisR.opsForValue()).thenReturn(valueOpsR);

        when(valueOpsW.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(0);
                    String val = inv.getArgument(1);
                    return Mono.just(store.putIfAbsent(key, val) == null);
                });

        when(valueOpsW.set(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(0);
                    String val = inv.getArgument(1);
                    store.put(key, val);
                    return Mono.just(true);
                });

        when(redisW.unlink(anyString()))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(0);
                    store.remove(key);
                    return Mono.just(1L);
                });

        when(valueOpsR.get(anyString()))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(0);
                    String val = store.get(key);
                    return val == null ? Mono.empty() : Mono.just(val);
                });
        when(valueOpsW.get(anyString()))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(0);
                    String val = store.get(key);
                    return val == null ? Mono.empty() : Mono.just(val);
                });

        MetricsService metrics = mock(MetricsService.class);
        when(metrics.incr(anyString())).thenReturn(Mono.empty());

        SimpleDurationParser parser = new SimpleDurationParser();

        cache = new LlmCacheService(redisW, redisR, parser, metrics);
        ReflectionTestUtils.setField(cache, "ctxTtlTimes", "15s");
        ReflectionTestUtils.setField(cache, "respTtlTimes", "6h");
    }

    @Test
    void second_concurrent_caller_with_same_hash_should_NOT_receive_empty() throws Exception {
        AtomicInteger invokerCalls = new AtomicInteger();
        Supplier<Mono<String>> invoker = () -> {
            invokerCalls.incrementAndGet();
            return Mono.delay(Duration.ofMillis(500)).thenReturn("answer-A");
        };

        Mono<String> firstMono = cache.getOrInvoke("gpt-4o", "hash-X", invoker).cache();
        Mono<String> secondMono = cache.getOrInvoke("gpt-4o", "hash-X", invoker).cache();

        CompletableFuture<String> firstFuture =
                firstMono.subscribeOn(Schedulers.parallel()).toFuture();

        Thread.sleep(50);

        Optional<String> secondResult = secondMono.blockOptional(Duration.ofSeconds(2));
        String firstResult = firstFuture.get(2, TimeUnit.SECONDS);

        assertThat(firstResult).isEqualTo("answer-A");
        assertThat(invokerCalls.get())
                .as("LLM invoker should be called exactly once across both concurrent callers")
                .isEqualTo(1);
        assertThat(secondResult)
                .as("Both concurrent callers should receive the same answer; "
                        + "second caller currently gets empty Mono — this is the bug")
                .contains("answer-A");
    }
}
