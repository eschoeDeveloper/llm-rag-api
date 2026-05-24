package io.github.eschoe.llmragapi.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitingServiceTest {

    private ReactiveValueOperations<String, String> ops;
    private RateLimitingService svc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        ops = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        svc = new RateLimitingService(redis);
        ReflectionTestUtils.setField(svc, "windowSeconds", 1);
        ReflectionTestUtils.setField(svc, "maxRequests", 3);
    }

    @Test
    void redisHealthy_withinLimit_allowed() {
        when(ops.get(anyString())).thenReturn(Mono.just("1")); // count=1 < 3
        when(ops.increment(anyString())).thenReturn(Mono.just(2L));

        assertThat(svc.isAllowed("s1").block()).isTrue();
    }

    @Test
    void redisHealthy_atLimit_blocked() {
        when(ops.get(anyString())).thenReturn(Mono.just("3")); // count=3 >= max(3)

        assertThat(svc.isAllowed("s1").block()).isFalse();
    }

    @Test
    void redisDown_fallsBackToLocalLimit_blocksOverLimit() {
        // Redis 장애 — 완전 fail-open 이면 4번째도 true 가 되어 이 테스트가 실패해야 함.
        when(ops.get(anyString())).thenReturn(Mono.error(new RuntimeException("redis down")));

        assertThat(svc.isAllowed("s2").block()).isTrue();  // 1
        assertThat(svc.isAllowed("s2").block()).isTrue();  // 2
        assertThat(svc.isAllowed("s2").block()).isTrue();  // 3
        assertThat(svc.isAllowed("s2").block())
                .as("Redis 장애 시에도 로컬 한도 초과는 차단되어야 함 (완전 fail-open 금지)")
                .isFalse();                                // 4 — 차단
    }

    @Test
    void redisDown_localWindowResetsAfterExpiry() throws InterruptedException {
        when(ops.get(anyString())).thenReturn(Mono.error(new RuntimeException("redis down")));

        assertThat(svc.isAllowed("s3").block()).isTrue();
        assertThat(svc.isAllowed("s3").block()).isTrue();
        assertThat(svc.isAllowed("s3").block()).isTrue();
        assertThat(svc.isAllowed("s3").block()).isFalse(); // 한도 소진

        Thread.sleep(1100); // window(1s) 경과

        assertThat(svc.isAllowed("s3").block())
                .as("window 경과 후 로컬 카운터 reset 되어 다시 허용")
                .isTrue();
    }

    @Test
    void redisDown_differentSessionsIndependent() {
        when(ops.get(anyString())).thenReturn(Mono.error(new RuntimeException("redis down")));

        // s4 소진
        svc.isAllowed("s4").block();
        svc.isAllowed("s4").block();
        svc.isAllowed("s4").block();
        assertThat(svc.isAllowed("s4").block()).isFalse();

        // s5 는 독립적으로 허용
        assertThat(svc.isAllowed("s5").block()).isTrue();
    }
}
