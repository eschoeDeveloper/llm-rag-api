package io.github.eschoe.llmragapi.llm.cache;

import io.github.eschoe.llmragapi.common.helper.SimpleDurationParser;
import io.github.eschoe.llmragapi.common.metrics.MetricsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.function.Supplier;

@Service
public class LlmCacheService {

    // 락 fail 측 폴링: 100ms 간격 × 최대 300회 = 최대 30s 대기.
    // 락 TTL(60s)보다 짧게 두고, 도달 시 본인이 호출(안전망)으로 fallback.
    private static final Duration STAMPEDE_POLL_INTERVAL = Duration.ofMillis(100);
    private static final int STAMPEDE_MAX_POLLS = 300;

    @Value("${app.llm.ctx-ttl-times:15s}")
    private String ctxTtlTimes;
    @Value("${app.llm.resp-ttl-times:6h}")
    private String respTtlTimes;

    // 쓰기(SET/DEL/락)는 반드시 마스터
    private final ReactiveStringRedisTemplate redisW;
    // 읽기(GET)는 레플리카 우선(없으면 마스터로 fallback)
    private final ReactiveStringRedisTemplate redisR;

    private final SimpleDurationParser parser;
    private final MetricsService metrics;

    public LlmCacheService(@Qualifier("redisWriterTemplate") ReactiveStringRedisTemplate redisW,
                               @Qualifier("redisReaderTemplate") ReactiveStringRedisTemplate redisR,
                               SimpleDurationParser parser,
                               MetricsService metrics) {
        this.redisW = redisW;
        this.redisR = redisR;
        this.parser = parser;
        this.metrics = metrics;
    }

    
    public Mono<String> getOrBuildPrompt(String userId, String ctxHash, Supplier<Mono<String>> builder) {

        String redisKey = "llm:ctx:%s:%s".formatted(userId, ctxHash);
        Duration ttlTimes = parser.parse(ctxTtlTimes);

        return redisR.opsForValue().get(redisKey)
                .switchIfEmpty(
                        builder.get()
                                .flatMap(s -> redisW.opsForValue().set(redisKey, s, ttlTimes).thenReturn(s))
                );
    }

    
    public Mono<Long> invalidateAnswers() {
        // SCAN은 운영에 부담스럽지만 LlmCacheService 가 KEYS 대신 SCAN을 권장해서 사용.
        // 응답 캐시만 비우고 prompt 캐시는 유지.
        return redisW.scan(org.springframework.data.redis.core.ScanOptions
                        .scanOptions().match("llm:resp:*").count(500).build())
                .collectList()
                .flatMap(keys -> keys.isEmpty() ? Mono.just(0L) : redisW.delete(keys.toArray(new String[0])));
    }

    /**
     * LLM 응답 cache + 분산락 패턴 (cache-aside with stampede protection).
     * 흐름:
     *   1. 응답 캐시 조회 → hit 면 즉시 반환
     *   2. miss → 락 키에 SETNX 시도
     *      2-1. 락 획득 → 실제 LLM 호출 → 응답 저장 + 락 해제
     *      2-2. 락 실패 → 응답 set 될 때까지 짧은 간격 폴링 → 도달 시 본인 호출(안전망)
     * 락 TTL 60초 — 호출 중 서버 죽어도 락은 자동 해제. 폴링 캡(30s)은 그보다 짧게.
     * 같은 입력으로 여러 동시 요청이 들어와도 LLM 호출은 정상 케이스에서 한 번만 발생.
     * 폴링 timeout(락 보유자 폭사/매우 느림) 케이스만 fallback으로 추가 호출 — 운영 모니터링용
     * metric: cache_stampede_fallback.
     */
    public Mono<String> getOrInvoke(String model, String inputHash, Supplier<Mono<String>> invoker) {

        String redisKey = "llm:resp:%s:%s".formatted(model, inputHash);
        String lockKey = "llm:lock:%s".formatted(inputHash);
        Duration ttlTimes = parser.parse(respTtlTimes);

        // 1) 응답 캐시 먼저 시도
        return redisR.opsForValue().get(redisKey)
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(hit -> metrics.incr("cache_hit").subscribe())
                .switchIfEmpty(
                        // 2) 캐시 미스 — metric 증가 + 락 시도
                        Mono.defer(() -> metrics.incr("cache_miss").then(
                                // 락 TTL 60s — LLM long-tail(gpt-4o 30~50초 가능) 커버.
                                redisW.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(60))
                                        .flatMap(acq -> Boolean.TRUE.equals(acq)
                                                // 2-1) 락 획득 → 실제 호출 (llm_calls 증가) → 캐시 저장 → 락 해제
                                                ? metrics.incr("llm_calls")
                                                        .then(invoker.get())
                                                        .flatMap(resp -> redisW.opsForValue().set(redisKey, resp, ttlTimes)
                                                                .then(redisW.unlink(lockKey))
                                                                .thenReturn(resp))
                                                        .onErrorResume(e -> redisW.unlink(lockKey).then(Mono.error(e)))
                                                // 2-2) 락 실패 — 응답 set 까지 짧은 간격 폴링.
                                                //      timeout 도달 시 본인 호출(안전망) — 락 보유자 폭사/매우 느림 보호.
                                                : Flux.interval(STAMPEDE_POLL_INTERVAL)
                                                        .take(STAMPEDE_MAX_POLLS)
                                                        .concatMap(tick -> redisR.opsForValue().get(redisKey))
                                                        .next()
                                                        .switchIfEmpty(Mono.defer(() ->
                                                                metrics.incr("cache_stampede_fallback")
                                                                        .then(invoker.get())
                                                                        .flatMap(resp -> redisW.opsForValue().set(redisKey, resp, ttlTimes).thenReturn(resp))
                                                        ))
                                        )
                        ))
                );

    }

}
