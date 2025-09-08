package io.github.eschoe.llmragapi.domain.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eschoe.llmragapi.global.ErrorResponse;
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

    public ChatHandler(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    public Mono<ServerResponse> chat(ServerRequest req) {
        return req.bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        // JSON 파싱 시도
                        ChatRequest chatRequest = objectMapper.readValue(body, ChatRequest.class);

                        // 새로운 방식인지 확인 (config가 있으면 새로운 방식)
                        if (chatRequest.getConfig() != null) {
                            return chatService.chatEnhanced(chatRequest)
                                    .flatMap(response -> ServerResponse.ok()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(response));
                        }
                    } catch (Exception e) {
                    }

                    try {
                        // 기존 방식으로 파싱 시도
                        ChatBody chatBody = objectMapper.readValue(body, ChatBody.class);
                        return chatService.chatLegacy(chatBody)
                                .flatMap(txt -> ServerResponse.ok()
                                        .contentType(MediaType.TEXT_PLAIN)
                                        .bodyValue(txt));
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
