package io.github.eschoe.llmragapi.search;

import io.github.eschoe.llmragapi.llm.LlmClient;
import io.github.eschoe.llmragapi.rag.config.EmbeddingProperties;
import io.github.eschoe.llmragapi.rag.retrieval.EmbeddingQueryDao;
import io.github.eschoe.llmragapi.rag.retrieval.EmbeddingRow;
import io.github.eschoe.llmragapi.rag.retrieval.HybridRetriever;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdvancedSearchService {

    private final LlmClient llmClient;
    private final EmbeddingQueryDao embeddingQueryDao;
    private final SearchHistoryService searchHistoryService;
    private final HybridRetriever hybridRetriever;
    private final EmbeddingProperties embeddingProps;

    public AdvancedSearchService(LlmClient llmClient,
                                 EmbeddingQueryDao embeddingQueryDao,
                                 SearchHistoryService searchHistoryService,
                                 HybridRetriever hybridRetriever,
                                 EmbeddingProperties embeddingProps) {
        this.llmClient = llmClient;
        this.embeddingQueryDao = embeddingQueryDao;
        this.searchHistoryService = searchHistoryService;
        this.hybridRetriever = hybridRetriever;
        this.embeddingProps = embeddingProps;
    }

    public Mono<AdvancedSearchResponse> search(AdvancedSearchRequest request) {
        Mono<AdvancedSearchResponse> searchMono = switch (request.getSearchType()) {
            case KEYWORD -> performKeywordSearch(request);
            case HYBRID -> performHybridSearch(request);
            default -> performSemanticSearch(request);
        };

        return searchMono.flatMap(response ->
                searchHistoryService.saveSearchHistory(
                        request.getSessionId(), request.getQuery(), response.getResults().size())
                        .thenReturn(response));
    }

    private Mono<AdvancedSearchResponse> performSemanticSearch(AdvancedSearchRequest request) {
        return llmClient.embed(embeddingProps.getModel(), request.getQuery())
                .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, request.getSize()))
                .collectList()
                .map(rows -> buildResponse(rows, request, "semantic"));
    }

    private Mono<AdvancedSearchResponse> performKeywordSearch(AdvancedSearchRequest request) {
        return embeddingQueryDao.topKByKeyword(request.getQuery(), request.getSize())
                .collectList()
                .map(rows -> buildResponse(rows, request, "keyword"));
    }

    private Mono<AdvancedSearchResponse> performHybridSearch(AdvancedSearchRequest request) {
        // candidate pool은 finalK의 2배로 넉넉하게 가져와서 RRF에 더 많은 신호를 준다
        int candidatePool = Math.max(request.getSize() * 2, 10);

        Mono<List<EmbeddingRow>> vectorTop = llmClient.embed(embeddingProps.getModel(), request.getQuery())
                .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, candidatePool))
                .collectList();

        Mono<List<EmbeddingRow>> keywordTop = embeddingQueryDao
                .topKByKeyword(request.getQuery(), candidatePool)
                .collectList();

        return hybridRetriever.fuse(vectorTop, keywordTop, request.getSize())
                .map(rows -> buildResponse(rows, request, "hybrid"));
    }

    private AdvancedSearchResponse buildResponse(List<EmbeddingRow> rows,
                                                 AdvancedSearchRequest request,
                                                 String type) {
        List<SearchResult> results = rows.stream()
                .filter(row -> applyFilters(row, request.getFilters()))
                .map(this::convertToSearchResult)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());

        return new AdvancedSearchResponse(
                results,
                request.getPage(),
                request.getSize(),
                results.size(),
                type
        );
    }

    private boolean applyFilters(EmbeddingRow row, List<AdvancedSearchRequest.SearchFilter> filters) {
        if (filters == null || filters.isEmpty()) return true;
        return filters.stream().allMatch(filter -> switch (filter.getField()) {
            case "score" -> applyScoreFilter(row.getScore(), filter);
            case "createdAt" -> applyDateFilter(
                    row.getCreatedAt() != null ? row.getCreatedAt().toLocalDateTime() : null, filter);
            case "title" -> applyStringFilter(row.getTitle(), filter);
            default -> true;
        });
    }

    private boolean applyScoreFilter(Double score, AdvancedSearchRequest.SearchFilter filter) {
        if (score == null) return false;
        return switch (filter.getOperator()) {
            case GREATER_THAN -> score > (Double) filter.getValue();
            case LESS_THAN -> score < (Double) filter.getValue();
            case BETWEEN -> score >= (Double) filter.getValue() && score <= (Double) filter.getValue2();
            default -> true;
        };
    }

    private boolean applyDateFilter(LocalDateTime date, AdvancedSearchRequest.SearchFilter filter) {
        if (date == null) return false;
        return switch (filter.getOperator()) {
            case GREATER_THAN -> date.isAfter((LocalDateTime) filter.getValue());
            case LESS_THAN -> date.isBefore((LocalDateTime) filter.getValue());
            case BETWEEN -> date.isAfter((LocalDateTime) filter.getValue())
                    && date.isBefore((LocalDateTime) filter.getValue2());
            default -> true;
        };
    }

    private boolean applyStringFilter(String value, AdvancedSearchRequest.SearchFilter filter) {
        if (value == null) return false;
        return switch (filter.getOperator()) {
            case CONTAINS -> value.toLowerCase().contains(((String) filter.getValue()).toLowerCase());
            case EQUALS -> value.equals(filter.getValue());
            default -> true;
        };
    }

    private SearchResult convertToSearchResult(EmbeddingRow row) {
        return new SearchResult(
                String.valueOf(row.getId()),
                row.getContent(),
                row.getScore() != null ? row.getScore() : 0.0,
                Map.of(
                        "title", row.getTitle() != null ? row.getTitle() : "",
                        "createdAt", row.getCreatedAt() != null ? row.getCreatedAt().toString() : "",
                        "source", "database"
                ),
                "database"
        );
    }
}
