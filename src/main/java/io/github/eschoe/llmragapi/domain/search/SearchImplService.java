package io.github.eschoe.llmragapi.domain.search;

import io.github.eschoe.llmragapi.client.LlmContextClient;
import io.github.eschoe.llmragapi.dao.EmbeddingQueryDao;
import io.github.eschoe.llmragapi.util.LlmRagUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchImplService implements SearchService {

    @Value("${APP_EMBEDDING_MODEL:text-embedding-3-small}")
    private String embeddingModel;

    private final EmbeddingQueryDao embeddingQueryDao;
    private final LlmRagUtil llmRagUtil;
    private final LlmContextClient llmContextClient;

    public SearchImplService(EmbeddingQueryDao embeddingQueryDao, LlmRagUtil llmRagUtil, LlmContextClient llmContextClient) {
        this.embeddingQueryDao = embeddingQueryDao;
        this.llmRagUtil = llmRagUtil;
        this.llmContextClient = llmContextClient;
    }

    @Override
    public Mono<List<TopKCosine>> topKByCosine(SearchBody searchBody) {
        float[] emb = searchBody.embedding();

        if (emb == null || emb.length == 0)
            return Mono.error(new IllegalArgumentException("embedding is required"));

        int k = (searchBody.topK() != null && searchBody.topK() > 0) ? searchBody.topK() : 10;

        return embeddingQueryDao.topKByCosine(emb, k)
                .map(entity -> new TopKCosine(
                        entity.getId(),
                        entity.getTitle(),
                        entity.getContent(),
                        entity.getScore(),// score 필드 추가
                        entity.getCreatedAt()
                ))
                .collectList();
    }

    // 새로운 메서드 추가
    @Override
    public Mono<SearchResponse> searchEnhanced(SearchRequest request) {
        Instant startTime = Instant.now();

        String query = llmRagUtil.opt(request.getQuery());
        if (query.isBlank()) {
            return Mono.error(new IllegalArgumentException("query is required"));
        }

        return llmContextClient.embed(embeddingModel, query)
                .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, request.getTopK()))
                .collectList()
                .map(embedRows -> {
                    // 임계값 필터링
                    List<SearchResult> filteredResults = embedRows.stream()
                            .filter(row -> row.getScore() != null && row.getScore() >= request.getThreshold())
                            .map(row -> new SearchResult(
                                    String.valueOf(row.getId()),
                                    row.getContent(),
                                    row.getScore() != null ? row.getScore() : 0.0,
                                    Map.of(
                                            "title", row.getTitle() != null ? row.getTitle() : "",
                                            "createdAt", row.getCreatedAt() != null ? row.getCreatedAt().toString() : ""
                                    ),
                                    "database"
                            ))
                            .collect(Collectors.toList());

                    // 평균 점수 계산
                    double averageScore = filteredResults.stream()
                            .mapToDouble(SearchResult::getScore)
                            .average()
                            .orElse(0.0);

                    Instant endTime = Instant.now();
                    Duration processingTime = Duration.between(startTime, endTime);

                    return new SearchResponse(
                            filteredResults,
                            filteredResults.size(),
                            averageScore,
                            Map.of(
                                    "processingTime", processingTime.toMillis(),
                                    "query", query,
                                    "topK", request.getTopK(),
                                    "threshold", request.getThreshold(),
                                    "timestamp", endTime
                            )
                    );
                });
    }

}
