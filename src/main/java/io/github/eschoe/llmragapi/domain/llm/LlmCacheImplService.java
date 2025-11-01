package io.github.eschoe.llmragapi.domain.llm;

import io.github.eschoe.llmragapi.util.SimpleDurationParser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

@Service
public class LlmCacheImplService implements LlmCacheService {

    @Value("${app.llm.ctx-ttl-times:15s}")
    private String ctxTtlTimes;
    @Value("${app.llm.resp-ttl-times:6h}")
    private String respTtlTimes;

    // 쓰기(SET/DEL/락)는 반드시 마스터
    private final ReactiveStringRedisTemplate redisW;
    // 읽기(GET)는 레플리카 우선(없으면 마스터로 fallback)
    private final ReactiveStringRedisTemplate redisR;

    private final SimpleDurationParser parser;

    public LlmCacheImplService(@Qualifier("redisWriterTemplate") ReactiveStringRedisTemplate redisW,
                               @Qualifier("redisReaderTemplate") ReactiveStringRedisTemplate redisR,
                               SimpleDurationParser parser) {
        this.redisW = redisW;
        this.redisR = redisR;
        this.parser = parser;
    }

    @Override
    public Mono<String> getOrBuildPrompt(String userId, String ctxHash, Supplier<Mono<String>> builder) {

        String redisKey = "llm:ctx:%s:%s".formatted(userId, ctxHash);
        Duration ttlTimes = parser.parse(ctxTtlTimes);

        return redisR.opsForValue().get(redisKey)
                .switchIfEmpty(
                        builder.get()
                                .flatMap(s -> redisW.opsForValue().set(redisKey, s, ttlTimes).thenReturn(s))
                );
    }

    @Override
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
