package io.github.eschoe.llmragapi.domain.ask;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eschoe.llmragapi.global.ErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class AskHandler {

    private final AskService askService;
    private final ObjectMapper objectMapper;

    public AskHandler(AskService askService, ObjectMapper objectMapper) {
        this.askService = askService;
        this.objectMapper = objectMapper;
    }

    public Mono<ServerResponse> ask(ServerRequest req) {
        return req.bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        // JSON 파싱 시도
                        AskRequest askRequest = objectMapper.readValue(body, AskRequest.class);

                        // 새로운 방식인지 확인 (config가 있으면 새로운 방식)
                        if (askRequest.getConfig() != null) {
                            return askService.askEnhanced(askRequest)
                                    .flatMap(response -> ServerResponse.ok()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .header("X-Session-ID", askRequest.getSessionId() != null ? askRequest.getSessionId() : "default-session")
                                            .bodyValue(response));
                        }
                    } catch (Exception e) {
                    }

                    try {
                        // 기존 방식으로 파싱 시도
                        AskBody askBody = objectMapper.readValue(body, AskBody.class);
                        return askService.askLegacy(askBody)
                                .flatMap(txt -> ServerResponse.ok()
                                        .contentType(MediaType.TEXT_PLAIN)
                                        .bodyValue(txt));
                    } catch (Exception e) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(
                                        new ErrorResponse("Invalid request format", Instant.now())
                                );
                    }
                })
                .onErrorResume(e -> {
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new ErrorResponse(e.getMessage(), Instant.now()));
                });
    }

    public Mono<ServerResponse> handleOptions(ServerRequest req) {
        return ServerResponse.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "POST, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, X-Session-ID")
                .build();
    }

}
