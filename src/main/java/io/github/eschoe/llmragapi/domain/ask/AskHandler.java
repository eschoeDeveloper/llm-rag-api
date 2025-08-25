package io.github.eschoe.llmragapi.domain.ask;

import io.github.eschoe.llmragapi.client.LlmContextClient;
import io.github.eschoe.llmragapi.util.ChatbotUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class AskHandler {

    private final AskService askService;

    public AskHandler(AskService askService) {
        this.askService = askService;

    }

    public Mono<ServerResponse> ask(ServerRequest req) {

        return req.bodyToMono(AskBody.class)
                .flatMap(askService::ask)
                .flatMap(txt -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(txt))
                .onErrorResume(e -> ServerResponse.badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(e));

    }

}
