package io.github.eschoe.llmragapi.domain.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eschoe.llmragapi.domain.rag.RAGConfig;
import io.github.eschoe.llmragapi.global.DetailedErrorResponse;
import io.github.eschoe.llmragapi.service.RateLimitingService;
import io.github.eschoe.llmragapi.util.SessionUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class ChatHandler {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final SessionUtil sessionUtil;
    private final RateLimitingService rateLimitingService;

    public ChatHandler(ChatService chatService, ObjectMapper objectMapper, SessionUtil sessionUtil, RateLimitingService rateLimitingService) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.sessionUtil = sessionUtil;
        this.rateLimitingService = rateLimitingService;
    }

    public Mono<ServerResponse> chat(ServerRequest req) {
        return req.bodyToMono(String.class)
                .flatMap(body -> {
                    final String sessionId = sessionUtil.extractSessionId(req);
                    
                    // Rate Limiting 체크
                    return rateLimitingService.isAllowed(sessionId)
                            .flatMap(allowed -> {
                                if (!allowed) {
                                    return rateLimitingService.getRemainingRequests(sessionId)
                                            .flatMap(remaining -> rateLimitingService.getResetTime(sessionId)
                                                    .map(resetTime -> new DetailedErrorResponse(
                                                            "RATE_LIMIT_EXCEEDED",
                                                            "요청 한도를 초과했습니다.",
                                                            String.format("남은 요청: %d개, 재설정 시간: %d", remaining, resetTime),
                                                            sessionId
                                                    )))
                                            .flatMap(errorResponse -> ServerResponse.status(429)
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue(errorResponse));
                                }
                                
                                return processChatRequest(body, sessionId);
                            });
                })
                .onErrorResume(e -> handleError(e, "unknown"));
    }
    
    private Mono<ServerResponse> processChatRequest(String body, String sessionId) {
        try {
            // JSON 파싱 시도
            ChatRequest chatRequest = objectMapper.readValue(body, ChatRequest.class);

            // 세션 ID 설정
            if (chatRequest.getSessionId() != null && sessionUtil.isValidSessionId(chatRequest.getSessionId())) {
                chatRequest.setSessionId(chatRequest.getSessionId());
            } else {
                chatRequest.setSessionId(sessionId);
            }

            // 새로운 방식인지 확인 (config가 있으면 새로운 방식)
            if (chatRequest.getConfig() != null) {
                return chatService.chatEnhanced(chatRequest)
                        .flatMap(response -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Session-ID", chatRequest.getSessionId())
                                .bodyValue(response));
            }
        } catch (Exception e) {
            // JSON 파싱 실패 시 기존 방식으로 시도
        }

        try {
            // 기존 방식으로 파싱 시도
            ChatBody chatBody = objectMapper.readValue(body, ChatBody.class);
            
            // ChatBody를 ChatRequest로 변환하여 컨텍스트 유지 기능 사용
            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setQuery(chatBody.query());
            chatRequest.setSessionId(sessionId);
            
            // 기본 config 설정
            RAGConfig defaultConfig = new RAGConfig();
            defaultConfig.setTopK(5);
            defaultConfig.setThreshold(0.7);
            chatRequest.setConfig(defaultConfig);
            
            return chatService.chatEnhanced(chatRequest)
                    .flatMap(response -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Session-ID", sessionId)
                            .bodyValue(response));
        } catch (Exception e) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new DetailedErrorResponse(
                        "INVALID_REQUEST_FORMAT",
                        "요청 형식이 올바르지 않습니다.",
                        "JSON 파싱 오류: " + e.getMessage(),
                        sessionId
                    ));
        }
    }
    
    private Mono<ServerResponse> handleError(Throwable e, String sessionId) {
        String errorType = "UNKNOWN_ERROR";
        String userMessage = "알 수 없는 오류가 발생했습니다.";
        String details = e.getMessage();
        
        if (e instanceof IllegalArgumentException) {
            errorType = "INVALID_INPUT";
            userMessage = "입력값이 올바르지 않습니다.";
        } else if (e.getMessage().contains("timeout")) {
            errorType = "TIMEOUT_ERROR";
            userMessage = "요청 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.";
        } else if (e.getMessage().contains("connection")) {
            errorType = "CONNECTION_ERROR";
            userMessage = "서버 연결에 문제가 있습니다. 잠시 후 다시 시도해주세요.";
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