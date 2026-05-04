package io.github.eschoe.llmragapi.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eschoe.llmragapi.common.exception.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class SearchHandler {

    private static final Logger log = LoggerFactory.getLogger(SearchHandler.class);

    private final SearchService searchService;
    private final ObjectMapper objectMapper;

    public SearchHandler(SearchService searchService, ObjectMapper objectMapper) {
        this.searchService = searchService;
        this.objectMapper = objectMapper;
    }

    public Mono<ServerResponse> search(ServerRequest req) {
        return req.bodyToMono(String.class)
                .flatMap(body -> {
                    SearchRequest searchRequest = tryParse(body, SearchRequest.class);
                    if (searchRequest != null
                            && StringUtils.hasLength(searchRequest.getQuery())
                            && searchRequest.getThreshold() > 0) {
                        return searchService.searchEnhanced(searchRequest)
                                .flatMap(response -> ServerResponse.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(response));
                    }

                    SearchBody searchBody = tryParse(body, SearchBody.class);
                    if (searchBody != null && searchBody.embedding() != null && searchBody.embedding().length > 0) {
                        return searchService.topKByCosine(searchBody)
                                .flatMap(results -> ServerResponse.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(results));
                    }

                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new ErrorResponse(
                                    "Body must contain either {query, topK, threshold>0} or {embedding[], topK}",
                                    Instant.now()));
                })
                .onErrorResume(e -> {
                    log.error("Search request failed", e);
                    return ServerResponse.status(500)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new ErrorResponse(
                                    e.getClass().getSimpleName() + ": " + e.getMessage(),
                                    Instant.now()));
                });
    }

    private <T> T tryParse(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            return null;
        }
    }
}
