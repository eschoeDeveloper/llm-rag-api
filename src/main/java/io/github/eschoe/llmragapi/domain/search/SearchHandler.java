package io.github.eschoe.llmragapi.domain.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eschoe.llmragapi.dao.EmbeddingQueryDao;
import io.github.eschoe.llmragapi.global.ErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class SearchHandler {

    private final SearchService searchService;
    private final ObjectMapper objectMapper;

    public SearchHandler(SearchService searchService, ObjectMapper objectMapper) {
        this.searchService = searchService;
        this.objectMapper = objectMapper;
    }

    public Mono<ServerResponse> search(ServerRequest req) {
        return req.bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        // JSON 파싱 시도
                        SearchRequest searchRequest = objectMapper.readValue(body, SearchRequest.class);

                        // 새로운 방식인지 확인 (threshold가 있으면 새로운 방식)
                        if (StringUtils.hasLength(searchRequest.getQuery()) && searchRequest.getThreshold() > 0) {
                            return searchService.searchEnhanced(searchRequest)
                                    .flatMap(response -> ServerResponse.ok()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(response));
                        }
                    } catch (Exception e) {
                    }

                    try {
                        // 기존 방식으로 파싱 시도
                        SearchBody searchBody = objectMapper.readValue(body, SearchBody.class);
                        return searchService.topKByCosine(searchBody)
                                .flatMap(results -> ServerResponse.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(results));
                    } catch (Exception e) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue( new ErrorResponse("Invalid request format", Instant.now()));
                    }
                })
                .onErrorResume(e -> {
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue( new ErrorResponse(e.getMessage(), Instant.now()));
                });
    }

}
