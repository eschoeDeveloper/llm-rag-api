package io.github.eschoe.llmragapi.domain.search;

import io.github.eschoe.llmragapi.dao.EmbeddingQueryDao;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class SearchHandler {

    private final SearchService searchService;

    public SearchHandler(SearchService searchService) {
        this.searchService = searchService;
    }

    public Mono<ServerResponse> searchHandler(ServerRequest req) {
        return req.bodyToMono(SearchBody.class)
                .flatMap(searchService::topKByCosine)
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list))
                .onErrorResume(e -> ServerResponse.badRequest().contentType(MediaType.TEXT_PLAIN)
                        .bodyValue("search error: " + e.getMessage()));
    }

}
