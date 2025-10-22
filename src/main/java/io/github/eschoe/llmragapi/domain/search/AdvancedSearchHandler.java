package io.github.eschoe.llmragapi.domain.search;

import io.github.eschoe.llmragapi.global.DetailedErrorResponse;
import io.github.eschoe.llmragapi.service.RateLimitingService;
import io.github.eschoe.llmragapi.util.SessionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class AdvancedSearchHandler {

    private final AdvancedSearchService searchService;
    private final ObjectMapper objectMapper;
    private final SessionUtil sessionUtil;
    private final RateLimitingService rateLimitingService;

    public AdvancedSearchHandler(AdvancedSearchService searchService, 
                               ObjectMapper objectMapper, 
                               SessionUtil sessionUtil,
                               RateLimitingService rateLimitingService) {
        this.searchService = searchService;
        this.objectMapper = objectMapper;
        this.sessionUtil = sessionUtil;
        this.rateLimitingService = rateLimitingService;
    }

    public Mono<ServerResponse> search(ServerRequest request) {
        return request.bodyToMono(String.class)
                .flatMap(body -> {
                    final String sessionId = sessionUtil.extractSessionId(request);
                    
                    // Rate Limiting 체크
                    return rateLimitingService.isAllowed(sessionId)
                            .flatMap(allowed -> {
                                if (!allowed) {
                                    return ServerResponse.status(429)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(new DetailedErrorResponse(
                                                    "RATE_LIMIT_EXCEEDED",
                                                    "요청 한도를 초과했습니다.",
                                                    "검색 요청이 너무 많습니다.",
                                                    sessionId
                                            ));
                                }
                                
                                return processSearchRequest(body, sessionId);
                            });
                })
                .onErrorResume(e -> handleError(e, "unknown"));
    }

    private Mono<ServerResponse> processSearchRequest(String body, String sessionId) {
        try {
            AdvancedSearchRequest request = objectMapper.readValue(body, AdvancedSearchRequest.class);
            request.setSessionId(sessionId);
            
            return searchService.search(request)
                    .flatMap(response -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Session-ID", sessionId)
                            .bodyValue(response));
        } catch (Exception e) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new DetailedErrorResponse(
                            "INVALID_SEARCH_REQUEST",
                            "검색 요청 형식이 올바르지 않습니다.",
                            e.getMessage(),
                            sessionId
                    ));
        }
    }

    private Mono<ServerResponse> handleError(Throwable e, String sessionId) {
        String errorType = "SEARCH_ERROR";
        String userMessage = "검색 중 오류가 발생했습니다.";
        String details = e.getMessage();
        
        if (e.getMessage().contains("timeout")) {
            errorType = "SEARCH_TIMEOUT";
            userMessage = "검색 시간이 초과되었습니다.";
        } else if (e.getMessage().contains("connection")) {
            errorType = "SEARCH_CONNECTION_ERROR";
            userMessage = "검색 서비스 연결에 문제가 있습니다.";
        }
        
        return ServerResponse.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new DetailedErrorResponse(
                        errorType,
                        userMessage,
                        details,
                        sessionId
                ));
    }
}
