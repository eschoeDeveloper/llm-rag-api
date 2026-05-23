package io.github.eschoe.llmragapi.rag;

import io.github.eschoe.llmragapi.common.metrics.MetricsService;
import io.github.eschoe.llmragapi.llm.LlmClient;
import io.github.eschoe.llmragapi.rag.config.EmbeddingProperties;
import io.github.eschoe.llmragapi.rag.prompt.ContextBudget;
import io.github.eschoe.llmragapi.rag.rerank.Reranker;
import io.github.eschoe.llmragapi.rag.retrieval.EmbeddingQueryDao;
import io.github.eschoe.llmragapi.search.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG retrieval 파이프라인 — 단일 책임.
 *
 * 입력: 사용자 query (text), 설정 (top-k / threshold / 토큰 예산)
 * 출력: 최종 컨텍스트로 사용할 SearchResult 리스트
 *
 * 단계:
 *   1. embed: 쿼리 → 1536차원 벡터
 *   2. retrieve: pgvector HNSW 로 candidate pool (= k * 2) 검색
 *   3. rerank: Cohere or NoOp 으로 cross-encoder 재정렬
 *   4. threshold + top-k: rerank 점수 기준 (cosine 점수 약한 좋은 후보 살림)
 *   5. budget: jtokkit 토큰 예산 내로 truncate
 *
 * 비어있는 결과 시 retrieval_empty metric 카운트 — 후속 분석용.
 *
 * ChatService 에서 분리한 이유: chatEnhanced/chatStream 두 곳에서 동일 흐름 중복이라
 * 단일 클래스로 추출. 검색 정책 변경(예: hybrid 도입)은 여기 한 곳만 수정.
 */
@Component
public class RagPipeline {

    private static final Logger log = LoggerFactory.getLogger(RagPipeline.class);

    private final LlmClient llmClient;
    private final EmbeddingQueryDao embeddingQueryDao;
    private final EmbeddingProperties embeddingProps;
    private final Reranker reranker;
    private final ContextBudget contextBudget;
    private final MetricsService metrics;

    public RagPipeline(LlmClient llmClient,
                       EmbeddingQueryDao embeddingQueryDao,
                       EmbeddingProperties embeddingProps,
                       Reranker reranker,
                       ContextBudget contextBudget,
                       MetricsService metrics) {
        this.llmClient = llmClient;
        this.embeddingQueryDao = embeddingQueryDao;
        this.embeddingProps = embeddingProps;
        this.reranker = reranker;
        this.contextBudget = contextBudget;
        this.metrics = metrics;
    }

    public record Params(int topK, double threshold, int contextTokenBudget) {}

    /**
     * 전체 파이프라인 실행 — query → ranked SearchResults.
     * candidatePool 은 reranker 가 제대로 일할 수 있도록 finalK * 2 (최소 10).
     */
    public Mono<List<SearchResult>> retrieve(String query, Params p) {
        int candidatePool = Math.max(p.topK() * 2, 10);

        return llmClient.embed(embeddingProps.getModel(), query)
                .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, candidatePool))
                .collectList()
                .map(this::rowsToSearchResults)
                .doOnNext(raw -> log.debug("[RAG] candidate pool: {} rows", raw.size()))
                .flatMap(rawResults -> reranker.rerank(query, rawResults))
                .map(reranked -> filterAndLimit(reranked, p.threshold(), p.topK()))
                .doOnNext(filtered -> log.debug("[RAG] after threshold>={} & top-{}: {} kept",
                        p.threshold(), p.topK(), filtered.size()))
                .map(filtered -> contextBudget.fit(filtered, p.contextTokenBudget()))
                .doOnNext(fitted -> {
                    log.debug("[RAG] after token budget {}: {} kept", p.contextTokenBudget(), fitted.size());
                    if (fitted.isEmpty()) metrics.incr("retrieval_empty").subscribe();
                });
    }

    private List<SearchResult> rowsToSearchResults(List<? extends io.github.eschoe.llmragapi.rag.retrieval.EmbeddingRow> rows) {
        return rows.stream()
                .map(r -> new SearchResult(
                        String.valueOf(r.getId()),
                        r.getContent(),
                        r.getScore() != null ? r.getScore() : 0.0,
                        Map.of(
                                "title", r.getTitle() != null ? r.getTitle() : "",
                                "createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "",
                                "documentId", r.getDocumentId() != null ? r.getDocumentId() : "",
                                "chunkIndex", r.getChunkIndex() != null ? r.getChunkIndex() : 0
                        ),
                        "database"))
                .collect(Collectors.toList());
    }

    private List<SearchResult> filterAndLimit(List<SearchResult> reranked, double threshold, int topK) {
        return reranked.stream()
                .filter(r -> r.getScore() >= threshold)
                .limit(topK)
                .collect(Collectors.toList());
    }
}
