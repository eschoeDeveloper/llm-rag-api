package io.github.eschoe.llmragapi.admin;

import io.github.eschoe.llmragapi.common.helper.SessionUtil;
import io.github.eschoe.llmragapi.common.metrics.MetricsResponse;
import io.github.eschoe.llmragapi.common.metrics.MetricsService;
import io.github.eschoe.llmragapi.document.parsing.VisionUsageTracker;
import io.github.eschoe.llmragapi.llm.cache.LlmCacheService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 운영/모니터링 관련 핸들러.
 *  - GET  /api/admin/vision-usage    : 오늘자 vision 사용량/비용/한도
 *  - POST /api/admin/cache/invalidate: 응답 캐시 즉시 비우기
 */
@Component
public class AdminHandler {

    private final VisionUsageTracker usageTracker;
    private final LlmCacheService llmCache;
    private final SessionUtil sessionUtil;
    private final MetricsService metrics;

    public AdminHandler(VisionUsageTracker usageTracker,
                        LlmCacheService llmCache,
                        SessionUtil sessionUtil,
                        MetricsService metrics) {
        this.usageTracker = usageTracker;
        this.llmCache = llmCache;
        this.sessionUtil = sessionUtil;
        this.metrics = metrics;
    }

    /**
     * 운영 메트릭 스냅샷 — cache_hit/miss, llm_calls, retrieval_empty 등.
     * 카운터 + 파생 지표(hit_rate) 함께 반환.
     */
    public Mono<ServerResponse> getMetrics(ServerRequest req) {
        String sessionId = sessionUtil.extractSessionId(req);
        return metrics.snapshot()
                .map(MetricsResponse::from)
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Session-ID", sessionId)
                        .bodyValue(result));
    }

    public Mono<ServerResponse> getVisionUsage(ServerRequest req) {
        String sessionId = sessionUtil.extractSessionId(req);
        return usageTracker.getUsage(sessionId)
                .flatMap(snapshot -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Session-ID", sessionId)
                        .bodyValue(snapshot));
    }

    public Mono<ServerResponse> invalidateCache(ServerRequest req) {
        String sessionId = sessionUtil.extractSessionId(req);
        return llmCache.invalidateAnswers()
                .flatMap(deleted -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Session-ID", sessionId)
                        .bodyValue(new InvalidateResult(deleted, "응답 캐시 비움 (prompt 캐시는 유지)")));
    }

    /** 캐시 무효화 응답 — 외부 노출용 record. */
    public record InvalidateResult(long deletedKeys, String message) {}
}
