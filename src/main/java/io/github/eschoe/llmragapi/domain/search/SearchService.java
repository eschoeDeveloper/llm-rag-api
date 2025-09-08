package io.github.eschoe.llmragapi.domain.search;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SearchService {
    Mono<List<TopKCosine>> topKByCosine(SearchBody searchBody);
    Mono<SearchResponse> searchEnhanced(SearchRequest request);
}
