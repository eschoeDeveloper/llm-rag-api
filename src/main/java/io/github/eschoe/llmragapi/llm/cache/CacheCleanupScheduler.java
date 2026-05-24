package io.github.eschoe.llmragapi.llm.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 응답 캐시 자동 청소.
 * 트리거:
 *  - 매일 새벽 4시 (cron) — 운영 트래픽 적은 시간
 *  - 활성/비활성 토글: app.llm.cache.cleanup-enabled
 */
@Component
public class CacheCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CacheCleanupScheduler.class);

    private final LlmCacheService cache;

    @Value("${app.llm.cache.cleanup-enabled:true}")
    private boolean enabled;

    public CacheCleanupScheduler(LlmCacheService cache) {
        this.cache = cache;
    }

    /**
     * 매일 새벽 4시(서버 시간) 실행.
     * cron 표현: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "${app.llm.cache.cleanup-cron:0 0 4 * * *}")
    public void scheduledCleanup() {
        if (!enabled) {
            log.debug("[cache-cleanup] disabled, skipping");
            return;
        }
        log.info("[cache-cleanup] starting scheduled cleanup");
        try {
            // @Scheduled 는 자체 ThreadPoolTaskScheduler 에서 실행 — Reactor 이벤트 루프 아님 → block() 안전.
            // subscribe() 만 던지면 fire-and-forget 이라 결과/에러 추적 불가 + 디스포저블 누수.
            // Redis 응답 지연 시 무한 대기 → dyno 재시작과 겹치면 graceful shutdown 지연. 30s timeout 으로 방어.
            Long deleted = cache.invalidateAnswers().block(Duration.ofSeconds(30));
            log.info("[cache-cleanup] removed {} answer cache keys", deleted == null ? 0 : deleted);
        } catch (Exception e) {
            log.warn("[cache-cleanup] failed: {}", e.getMessage());
        }
    }
}
