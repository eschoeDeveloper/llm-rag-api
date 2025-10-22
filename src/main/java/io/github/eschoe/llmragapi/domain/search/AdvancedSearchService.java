package io.github.eschoe.llmragapi.domain.search;

import io.github.eschoe.llmragapi.client.LlmContextClient;
import io.github.eschoe.llmragapi.dao.EmbeddingQueryDao;
import io.github.eschoe.llmragapi.entity.EmbeddingRow;
import io.github.eschoe.llmragapi.service.SearchHistoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdvancedSearchService {

    private final LlmContextClient llmContextClient;
    private final EmbeddingQueryDao embeddingQueryDao;
    private final SearchHistoryService searchHistoryService;

    @Value("${app.llm.embedding-model:}")
    private String embeddingModel;

    public AdvancedSearchService(LlmContextClient llmContextClient, 
                                EmbeddingQueryDao embeddingQueryDao,
                                SearchHistoryService searchHistoryService) {
        this.llmContextClient = llmContextClient;
        this.embeddingQueryDao = embeddingQueryDao;
        this.searchHistoryService = searchHistoryService;
    }

    public Mono<AdvancedSearchResponse> search(AdvancedSearchRequest request) {
        Mono<AdvancedSearchResponse> searchMono;
        
        switch (request.getSearchType()) {
            case SEMANTIC:
                searchMono = performSemanticSearch(request);
                break;
            case KEYWORD:
                searchMono = performKeywordSearch(request);
                break;
            case HYBRID:
                searchMono = performHybridSearch(request);
                break;
            default:
                searchMono = performSemanticSearch(request);
        }
        
        return searchMono.flatMap(response -> {
            // 검색 히스토리 저장
            return searchHistoryService.saveSearchHistory(request.getSessionId(), request.getQuery(), response.getResults().size())
                    .thenReturn(response);
        });
    }

    private Mono<AdvancedSearchResponse> performSemanticSearch(AdvancedSearchRequest request) {
        return llmContextClient.embed(embeddingModel, request.getQuery())
                .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, request.getSize()))
                .collectList()
                .map(rows -> {
                    List<SearchResult> results = rows.stream()
                            .filter(row -> applyFilters(row, request.getFilters()))
                            .map(this::convertToSearchResult)
                            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore())) // 점수 내림차순
                            .collect(Collectors.toList());

                    return new AdvancedSearchResponse(
                            results,
                            request.getPage(),
                            request.getSize(),
                            results.size(),
                            "semantic"
                    );
                });
    }

    private Mono<AdvancedSearchResponse> performKeywordSearch(AdvancedSearchRequest request) {
        // 키워드 검색 구현 (의미 검색과 동일하게 처리)
        return llmContextClient.embed(embeddingModel, request.getQuery())
                .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, request.getSize()))
                .collectList()
                .map(rows -> {
                    List<SearchResult> results = rows.stream()
                            .filter(row -> applyFilters(row, request.getFilters()))
                            .map(this::convertToSearchResult)
                            .collect(Collectors.toList());

                    return new AdvancedSearchResponse(
                            results,
                            request.getPage(),
                            request.getSize(),
                            results.size(),
                            "keyword"
                    );
                });
    }

    private Mono<AdvancedSearchResponse> performHybridSearch(AdvancedSearchRequest request) {
        // 하이브리드 검색: 의미 검색 + 키워드 검색 결합
        Mono<List<SearchResult>> semanticResults = performSemanticSearch(request)
                .map(AdvancedSearchResponse::getResults);
        
        Mono<List<SearchResult>> keywordResults = performKeywordSearch(request)
                .map(AdvancedSearchResponse::getResults);

        return Mono.zip(semanticResults, keywordResults)
                .map(tuple -> {
                    List<SearchResult> semantic = tuple.getT1();
                    List<SearchResult> keyword = tuple.getT2();
                    
                    // 하이브리드 점수 계산 (의미 검색 70% + 키워드 검색 30%)
                    List<SearchResult> hybridResults = semantic.stream()
                            .map(result -> {
                                double hybridScore = result.getScore() * 0.7;
                                result.setScore(hybridScore);
                                return result;
                            })
                            .collect(Collectors.toList());

                    return new AdvancedSearchResponse(
                            hybridResults,
                            request.getPage(),
                            request.getSize(),
                            hybridResults.size(),
                            "hybrid"
                    );
                });
    }

    private boolean applyFilters(EmbeddingRow row, List<AdvancedSearchRequest.SearchFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }

        return filters.stream().allMatch(filter -> {
            switch (filter.getField()) {
                case "score":
                    return applyScoreFilter(row.getScore(), filter);
                case "createdAt":
                    return applyDateFilter(row.getCreatedAt() != null ? row.getCreatedAt().toLocalDateTime() : null, filter);
                case "title":
                    return applyStringFilter(row.getTitle(), filter);
                default:
                    return true;
            }
        });
    }

    private boolean applyScoreFilter(Double score, AdvancedSearchRequest.SearchFilter filter) {
        if (score == null) return false;
        
        switch (filter.getOperator()) {
            case GREATER_THAN:
                return score > (Double) filter.getValue();
            case LESS_THAN:
                return score < (Double) filter.getValue();
            case BETWEEN:
                return score >= (Double) filter.getValue() && score <= (Double) filter.getValue2();
            default:
                return true;
        }
    }

    private boolean applyDateFilter(LocalDateTime date, AdvancedSearchRequest.SearchFilter filter) {
        if (date == null) return false;
        
        switch (filter.getOperator()) {
            case GREATER_THAN:
                return date.isAfter((LocalDateTime) filter.getValue());
            case LESS_THAN:
                return date.isBefore((LocalDateTime) filter.getValue());
            case BETWEEN:
                return date.isAfter((LocalDateTime) filter.getValue()) && 
                       date.isBefore((LocalDateTime) filter.getValue2());
            default:
                return true;
        }
    }

    private boolean applyStringFilter(String value, AdvancedSearchRequest.SearchFilter filter) {
        if (value == null) return false;
        
        switch (filter.getOperator()) {
            case CONTAINS:
                return value.toLowerCase().contains(((String) filter.getValue()).toLowerCase());
            case EQUALS:
                return value.equals(filter.getValue());
            default:
                return true;
        }
    }

    private SearchResult convertToSearchResult(EmbeddingRow row) {
        return new SearchResult(
                String.valueOf(row.getId()),
                row.getContent(),
                row.getScore() != null ? row.getScore() : 0.0,
                java.util.Map.of(
                        "title", row.getTitle() != null ? row.getTitle() : "",
                        "createdAt", row.getCreatedAt() != null ? row.getCreatedAt().toString() : "",
                        "source", "database"
                ),
                "database"
        );
    }
}
