package io.github.eschoe.llmragapi.domain.chat;

import io.github.eschoe.llmragapi.client.LlmContextClient;
import io.github.eschoe.llmragapi.dao.EmbeddingQueryDao;
import io.github.eschoe.llmragapi.util.ChatbotUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Component
public class ChatHandler {

    private final ChatService chatService;

    public ChatHandler(ChatService chatService) {
        this.chatService = chatService;
    }

    public Mono<ServerResponse> chat(ServerRequest req) {
        return req.bodyToMono(ChatBody.class)
                .flatMap(chatService::chat)
                .flatMap(txt -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(txt))
                .onErrorResume(e -> ServerResponse.badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(e));
    }

}
