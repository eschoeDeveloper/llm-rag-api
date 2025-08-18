package io.github.eschoe.llmragapi.domain.search;

import io.github.eschoe.llmragapi.dao.EmbeddingQueryDao;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class SearchHandler {

    private final EmbeddingQueryDao embeddingQueryDao;

    public SearchHandler(EmbeddingQueryDao embeddingQueryDao) {
        this.embeddingQueryDao = embeddingQueryDao;
    }

    public Mono<ServerResponse> search(ServerRequest req) {
        return req.bodyToMono(SearchBody.class)
                .flatMap(b -> {
                    float[] emb = b.embedding();
                    if (emb == null || emb.length == 0)
                        return Mono.error(new IllegalArgumentException("embedding is required"));
                    int k = (b.topK() != null && b.topK() > 0) ? b.topK() : 10;
                    return embeddingQueryDao.topKByCosine(emb, k).collectList();
                })
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list))
                .onErrorResume(e -> ServerResponse.badRequest().contentType(MediaType.TEXT_PLAIN)
                        .bodyValue("search error: " + e.getMessage()));
    }

}
