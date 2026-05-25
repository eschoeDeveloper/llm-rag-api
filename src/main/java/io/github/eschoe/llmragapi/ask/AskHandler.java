package io.github.eschoe.llmragapi.ask;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eschoe.llmragapi.common.exception.DetailedErrorResponse;
import io.github.eschoe.llmragapi.common.exception.ErrorResponse;
import io.github.eschoe.llmragapi.common.helper.SessionUtil;
import io.github.eschoe.llmragapi.ratelimit.RateLimitingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class AskHandler {

    private static final Logger logger = LoggerFactory.getLogger(AskHandler.class);

    private final AskService askService;
    private final ObjectMapper objectMapper;
    private final SessionUtil sessionUtil;
    private final RateLimitingService rateLimitingService;

    public AskHandler(AskService askService,
                      ObjectMapper objectMapper,
                      SessionUtil sessionUtil,
                      RateLimitingService rateLimitingService) {
        this.askService = askService;
        this.objectMapper = objectMapper;
        this.sessionUtil = sessionUtil;
        this.rateLimitingService = rateLimitingService;
    }

    public Mono<ServerResponse> ask(ServerRequest req) {
        return req.bodyToMono(String.class)
                .flatMap(body -> {
                    // sessionId 는 헤더/쿠키에서 추출 — body 의 sessionId 는 신뢰하지 않음(spoofing 차단).
                    // ChatHandler 와 동일 정책. (이전엔 body sessionId 를 그대로 써서 남의 세션 history
                    // 접근/오염 가능 + 헤더만 보내는 클라이언트는 default-session 으로 묶였음.)
                    final String sessionId = sessionUtil.extractSessionId(req);

                    // Rate Limiting — 이전엔 /api/ask 만 누락돼 무제한 LLM 호출(비용) 가능했음.
                    return rateLimitingService.isAllowed(sessionId)
                            .flatMap(allowed -> {
                                if (!allowed) {
                                    return rateLimitingService.getRemainingRequests(sessionId)
                                            .flatMap(remaining -> rateLimitingService.getResetTime(sessionId)
                                                    .map(resetTime -> new DetailedErrorResponse(
                                                            "RATE_LIMIT_EXCEEDED",
                                                            "요청 한도를 초과했습니다.",
                                                            String.format("남은 요청: %d개, 재설정 시간: %d", remaining, resetTime),
                                                            sessionId)))
                                            .flatMap(err -> ServerResponse.status(429)
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue(err));
                                }
                                return processAskRequest(body, sessionId);
                            });
                })
                .onErrorResume(e -> {
                    logger.warn("[ask] request failed: {}", e.getMessage());
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new ErrorResponse(e.getMessage(), Instant.now()));
                });
    }

    /**
     * 두 입력 형식 호환:
     *   1) AskRequest (신규: query + config + sessionId) → askEnhanced
     *   2) AskBody    (레거시: query/provider/model)     → askLegacy
     * 첫 파싱이 실패하면 두 번째 시도 — 의도된 fallthrough 라 에러 무음 처리.
     */
    private Mono<ServerResponse> processAskRequest(String body, String sessionId) {
        try {
            AskRequest askRequest = objectMapper.readValue(body, AskRequest.class);
            if (askRequest.getConfig() != null) {
                // 헤더에서 추출한 sessionId 로 통일 — body sessionId 는 무시(spoofing 차단)
                askRequest.setSessionId(sessionId);
                return askService.askEnhanced(askRequest)
                        .flatMap(response -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Session-ID", sessionId)
                                .bodyValue(response));
            }
        } catch (Exception ignored) {
            // 신규 형식 아님 → 레거시 형식 시도
        }

        try {
            AskBody askBody = objectMapper.readValue(body, AskBody.class);
            return askService.askLegacy(askBody)
                    .flatMap(txt -> ServerResponse.ok()
                            .contentType(MediaType.TEXT_PLAIN)
                            .bodyValue(txt));
        } catch (Exception e) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ErrorResponse("Invalid request format", Instant.now()));
        }
    }

    public Mono<ServerResponse> handleOptions(ServerRequest req) {
        String origin = req.headers().firstHeader("Origin");
        return ServerResponse.ok()
                .header("Access-Control-Allow-Origin", origin != null ? origin : "*")
                .header("Access-Control-Allow-Methods", "POST, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, X-Session-ID")
                .header("Access-Control-Max-Age", "3600")
                .build();
    }

}
