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

    @Value("${app.llm.embedding-model:text-bedding-3-small}")
    private String embeddingModel;

    private final ChatbotUtil chatbotUtil;
    private final LlmContextClient llmContextClient;

    AskHandler(LlmContextClient llmContextClient, ChatbotUtil chatbotUtil) {
        this.chatbotUtil = chatbotUtil;
        this.llmContextClient = llmContextClient;
    }

    public Mono<ServerResponse> ask(ServerRequest req) {
        return req.bodyToMono(AskBody.class)
                .flatMap(b -> {
                    String query = chatbotUtil.opt(b.query());
                    if (query.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));
                    String provider = b.provider() == null ? "openai" : b.provider();
                    String model = chatbotUtil.chooseModel(provider, b.model());
                    String system = "You are a concise helpful assistant.";
                    return llmContextClient.chat(provider, model, system, query)
                            .flatMap(txt -> ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValue(txt));
                })
                .onErrorResume(e -> ServerResponse.badRequest().contentType(MediaType.TEXT_PLAIN)
                        .bodyValue("ask error: " + e.getMessage()));
    }

}
