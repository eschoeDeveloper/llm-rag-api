package io.github.eschoe.llmragapi.admin;

import io.github.eschoe.llmragapi.common.helper.SessionUtil;
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

    public AdminHandler(VisionUsageTracker usageTracker,
                        LlmCacheService llmCache,
                        SessionUtil sessionUtil) {
        this.usageTracker = usageTracker;
        this.llmCache = llmCache;
        this.sessionUtil = sessionUtil;
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
                        .bodyValue(Map.of(
                                "deletedKeys", deleted,
                                "message", "응답 캐시 비움 (prompt 캐시는 유지)"
                        )));
    }
}
