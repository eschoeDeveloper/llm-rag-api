package io.github.eschoe.llmragapi.domain.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eschoe.llmragapi.domain.rag.RAGConfig;
import io.github.eschoe.llmragapi.global.ErrorResponse;
import io.github.eschoe.llmragapi.util.SessionUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class ChatHandler {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final SessionUtil sessionUtil;

    public ChatHandler(ChatService chatService, ObjectMapper objectMapper, SessionUtil sessionUtil) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.sessionUtil = sessionUtil;
    }

    public Mono<ServerResponse> chat(ServerRequest req) {
        return req.bodyToMono(String.class)
                .flatMap(body -> {
                    // 세션 ID 추출 (전체 메서드에서 사용)
                    final String sessionId = sessionUtil.extractSessionId(req);
                    
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
                                            .header("X-Session-ID", chatRequest.getSessionId())  // 세션 ID를 응답 헤더에 포함
                                            .bodyValue(response));
                        }
                    } catch (Exception e) {
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
                                .bodyValue( new ErrorResponse("Invalid request format", Instant.now()) );
                    }
                })
                .onErrorResume(e -> {
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new ErrorResponse(e.getMessage(), Instant.now()));
                });
    }

//    public Mono<ServerResponse> chat(ServerRequest req) {
//        return req.bodyToMono(ChatBody.class)
//                .flatMap(chatService::chat)
//                .flatMap(txt -> ServerResponse.ok()
//                        .contentType(MediaType.TEXT_PLAIN)
//                        .bodyValue(txt))
//                .onErrorResume(e -> ServerResponse.badRequest()
//                        .contentType(MediaType.TEXT_PLAIN)
//                        .bodyValue(e));
//    }

}
