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

    @Value("${APP_EMBEDDING_MODEL:text-embedding-3-small}")
    private String embeddingModel;

    private final ChatbotUtil chatbotUtil;
    private final LlmContextClient llmContextClient;
    private final EmbeddingQueryDao embeddingQueryDao;

    public ChatHandler(ChatbotUtil chatbotUtil, LlmContextClient llmContextClient, EmbeddingQueryDao embeddingQueryDao) {
        this.chatbotUtil = chatbotUtil;
        this.llmContextClient = llmContextClient;
        this.embeddingQueryDao = embeddingQueryDao;
    }

    public Mono<ServerResponse> chat(ServerRequest req) {
        return req.bodyToMono(ChatBody.class)
                .flatMap(b -> {
                    String q = chatbotUtil.opt(b.query());
                    if (q.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));
                    int k = (b.topK() != null && b.topK() > 0) ? b.topK() : 5;
                    String provider = b.provider() == null ? "openai" : b.provider();
                    String model = chatbotUtil.chooseModel(provider, b.model());

                    Mono<float[]> embMono = (b.embedding() != null && b.embedding().length > 0)
                            ? Mono.just(b.embedding())
                            : llmContextClient.embed(embeddingModel, q); // embeddings via OpenAI by default

                    return embMono
                            .flatMapMany(emb -> embeddingQueryDao.topKByCosine(emb, k))
                            .collectList()
                            .flatMap(docs -> {
                                String context = docs.stream()
                                        .map(d -> "- " + chatbotUtil.safeSnippet(d.getContent()))
                                        .collect(Collectors.joining("\n"));
                                String system = "Answer using ONLY the provided CONTEXT. If missing, say you don't know.";
                                String prompt = "QUESTION:\n" + q + "\n\nCONTEXT:\n" + context;
                                return llmContextClient.chat(provider, model, system, prompt);
                            })
                            .flatMap(txt -> ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValue(txt));
                })
                .onErrorResume(e -> ServerResponse.badRequest().contentType(MediaType.TEXT_PLAIN)
                        .bodyValue("chat error: " + e.getMessage()));
    }

}
