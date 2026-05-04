package io.github.eschoe.llmragapi.document.parsing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * GPT-4o vision API 사용량 추적 + 한도 enforcement.
 *
 * Redis 키 스킴 (TTL 26시간):
 *   vision:pages:session:{sessionId}:{yyyy-MM-dd}   → 세션 일별 페이지 수
 *   vision:pages:global:{yyyy-MM-dd}                → 시스템 전체 일별 페이지 수
 *
 * 한도 초과 시 {@link VisionLimitExceededException} 던짐.
 */
@Component
public class VisionUsageTracker {

    private static final Logger log = LoggerFactory.getLogger(VisionUsageTracker.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ReactiveStringRedisTemplate redis;

    /** 페이지당 추정 비용 (USD). gpt-4o high detail 기준. */
    @Value("${app.parsing.pdf.vision-cost-per-page-usd:0.005}")
    private double costPerPage;

    /** 세션당 일일 페이지 한도. 0 이하면 비활성 (무제한). */
    @Value("${app.parsing.pdf.vision-daily-pages-per-session:100}")
    private int dailyLimitPerSession;

    /** 시스템 전체 일일 페이지 한도. 0 이하면 비활성. */
    @Value("${app.parsing.pdf.vision-daily-pages-global:500}")
    private int dailyLimitGlobal;

    public VisionUsageTracker(@Qualifier("redisWriterTemplate") ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 페이지 수만큼 사용 가능한지 확인 + 가능하면 카운터 증가.
     * 한도 초과 시 {@link VisionLimitExceededException} 던짐.
     */
    public Mono<Void> reserve(String sessionId, int pages) {
        if (pages <= 0) return Mono.empty();

        String day = LocalDate.now().format(DAY);
        String sessionKey = "vision:pages:session:" + sessionId + ":" + day;
        String globalKey = "vision:pages:global:" + day;

        // 세션 한도 체크
        Mono<Void> sessionCheck = (dailyLimitPerSession <= 0)
                ? Mono.empty()
                : checkAndIncrement(sessionKey, pages, dailyLimitPerSession,
                        "세션 일일 한도");

        // 전역 한도 체크
        Mono<Void> globalCheck = (dailyLimitGlobal <= 0)
                ? Mono.empty()
                : checkAndIncrement(globalKey, pages, dailyLimitGlobal,
                        "시스템 일일 한도");

        return sessionCheck.then(globalCheck);
    }

    /**
     * 원자적 증가-후-검증 패턴:
     *  1. INCR (Redis 단일 명령, 동시 안전)
     *  2. 결과가 한도 초과면 DECR 로 롤백 후 예외
     *  3. 통과면 TTL 갱신
     *
     * 이전엔 GET→check→INCR 구조라 동시 요청 시 두 트랜잭션이 같은 GET 결과로 통과해
     * 한도를 넘기는 TOCTOU race 가 있었음.
     */
    private Mono<Void> checkAndIncrement(String key, int pages, int limit, String label) {
        return redis.opsForValue().increment(key, pages)
                .flatMap(updated -> {
                    long current = updated == null ? pages : updated;
                    if (current > limit) {
                        // 롤백 — INCR 한 만큼 DECR. 그 후 예외.
                        long previous = current - pages;
                        double currentCost = previous * costPerPage;
                        double rejectedCost = pages * costPerPage;
                        return redis.opsForValue().increment(key, -pages)
                                .then(Mono.error(new VisionLimitExceededException(String.format(
                                        "%s 초과: 현재 %d페이지 (~$%.2f), 요청 %d페이지 (~$%.2f), 한도 %d페이지. 내일 다시 시도하거나 PPTX 사용 권장.",
                                        label, previous, currentCost, pages, rejectedCost, limit))));
                    }
                    return redis.expire(key, Duration.ofHours(26))
                            .doOnSuccess(ok -> log.info(
                                    "[vision-usage] {}: {}/{} pages (~${}) used today",
                                    label, current, limit,
                                    String.format("%.2f", current * costPerPage)))
                            .then();
                });
    }

    /**
     * 오늘자 사용량 조회. 대시보드/모니터링용.
     */
    public Mono<UsageSnapshot> getUsage(String sessionId) {
        String day = LocalDate.now().format(DAY);
        String sessionKey = "vision:pages:session:" + sessionId + ":" + day;
        String globalKey = "vision:pages:global:" + day;

        Mono<Integer> sessionPages = readCount(sessionKey);
        Mono<Integer> globalPages = readCount(globalKey);

        return Mono.zip(sessionPages, globalPages)
                .map(t -> new UsageSnapshot(
                        day,
                        t.getT1(), dailyLimitPerSession,
                        t.getT2(), dailyLimitGlobal,
                        costPerPage,
                        t.getT1() * costPerPage,
                        t.getT2() * costPerPage));
    }

    /** Redis 값 → int. 손상된 값(파싱 실패) 시 0 으로 fallback — 추적 멈추지 말고 다음 요청부터 재시작. */
    private Mono<Integer> readCount(String key) {
        return redis.opsForValue().get(key)
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        log.warn("[vision-usage] corrupted counter at {}: {}", key, s);
                        return 0;
                    }
                })
                .defaultIfEmpty(0);
    }

    /** 한도 초과 시 던지는 예외. 핸들러에서 명확한 에러 메시지로 변환. */
    public static class VisionLimitExceededException extends RuntimeException {
        public VisionLimitExceededException(String message) {
            super(message);
        }
    }

    public record UsageSnapshot(
            String date,
            int sessionPagesToday, int sessionDailyLimit,
            int globalPagesToday, int globalDailyLimit,
            double costPerPageUsd,
            double sessionCostTodayUsd,
            double globalCostTodayUsd
    ) {}
}
