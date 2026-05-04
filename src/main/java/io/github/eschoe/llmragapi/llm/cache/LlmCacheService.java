package io.github.eschoe.llmragapi.llm.cache;

import io.github.eschoe.llmragapi.common.helper.SimpleDurationParser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

@Service
public class LlmCacheService {

    @Value("${app.llm.ctx-ttl-times:15s}")
    private String ctxTtlTimes;
    @Value("${app.llm.resp-ttl-times:6h}")
    private String respTtlTimes;

    // 쓰기(SET/DEL/락)는 반드시 마스터
    private final ReactiveStringRedisTemplate redisW;
    // 읽기(GET)는 레플리카 우선(없으면 마스터로 fallback)
    private final ReactiveStringRedisTemplate redisR;

    private final SimpleDurationParser parser;

    public LlmCacheService(@Qualifier("redisWriterTemplate") ReactiveStringRedisTemplate redisW,
                               @Qualifier("redisReaderTemplate") ReactiveStringRedisTemplate redisR,
                               SimpleDurationParser parser) {
        this.redisW = redisW;
        this.redisR = redisR;
        this.parser = parser;
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
     *
     * 흐름:
     *   1. 응답 캐시 조회 → hit 면 즉시 반환
     *   2. miss → 락 키에 SETNX 시도
     *      2-1. 락 획득 → 실제 LLM 호출 → 응답 저장 + 락 해제
     *      2-2. 락 실패 → 다른 요청이 처리 중 → 120ms 대기 후 캐시 재조회
     *
     * 락 TTL 30초 — 호출 중 서버 죽어도 락은 자동 해제.
     * 락 못 잡고 재조회도 miss 면 응답 없음 (현재 동작) — 이 경우 호출자가 다시 시도하거나
     * empty Mono 반환됨. cache stampede 방지 가치를 위해 감수하는 trade-off.
     *
     * 같은 입력으로 여러 동시 요청 들어와도 LLM 호출은 한 번만 발생 → API 비용 절감.
     */
    public Mono<String> getOrInvoke(String model, String inputHash, Supplier<Mono<String>> invoker) {

        String redisKey = "llm:resp:%s:%s".formatted(model, inputHash);
        String lockKey = "llm:lock:%s".formatted(inputHash);
        Duration ttlTimes = parser.parse(respTtlTimes);

        // 1) 응답 캐시 먼저 시도
        return redisR.opsForValue().get(redisKey)
                .switchIfEmpty(
                        // 2) 캐시 미스 → 락 시도
                        redisW.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(30))
                                .flatMap(acq -> Boolean.TRUE.equals(acq)
                                        // 2-1) 락 획득 → 실제 호출 → 캐시 저장 → 락 해제
                                        ? invoker.get()
                                        .flatMap(resp -> redisW.opsForValue().set(redisKey, resp, ttlTimes)
                                                .then(redisW.unlink(lockKey))
                                                .thenReturn(resp))
                                        .onErrorResume(e -> redisW.unlink(lockKey).then(Mono.error(e)))
                                        // 2-2) 락을 못 잡음(다른 요청이 처리 중) → 잠깐 대기 후 캐시 재조회
                                        : Mono.delay(Duration.ofMillis(120)).then(redisR.opsForValue().get(redisKey))
                                )
                );

    }

}
