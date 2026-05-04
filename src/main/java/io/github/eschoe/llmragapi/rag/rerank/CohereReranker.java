package io.github.eschoe.llmragapi.rag.rerank;

import io.github.eschoe.llmragapi.search.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cohere Rerank API 기반 Reranker.
 * 멀티링구얼 모델 사용 — 한국어 쿼리/문서 모두 지원.
 *
 * 호출 비용 (rerank-multilingual-v3.0): 1,000건당 $1
 *  - 한 RAG 호출 = 1건 (문서 N개 한 번에 평가)
 *  - 즉 1,000번 채팅 = $1
 *
 * 호출 실패 시 입력 그대로 반환해 fallback (RAG 끊기지 않음).
 *
 * 활성 조건: COHERE_API_KEY env 또는 spring.ai.cohere.api-key 프로퍼티 설정.
 */
public class CohereReranker implements Reranker {

    private static final Logger log = LoggerFactory.getLogger(CohereReranker.class);
    private static final String API_URL = "https://api.cohere.com/v2/rerank";

    private final String apiKey;
    private final String model;
    private final WebClient webClient;

    public CohereReranker(String apiKey, String model, WebClient.Builder builder) {
        this.apiKey = apiKey;
        this.model = model;
        this.webClient = builder.build();
    }

    /**
     * Cohere /v2/rerank API 호출 → 재정렬된 결과 반환.
     *
     * 핵심 동작:
     *  - 1~0 후보면 호출 생략 (재정렬할 게 없음)
     *  - return_documents=false 로 응답 크기 최소화 (인덱스만 받아서 원본 매핑)
     *  - 10초 timeout 후 자동 NoOp fallback — RAG 흐름은 깨지지 않음
     *  - 점수 desc 정렬은 Cohere 가 보장하지만 명시적으로 한 번 더 sort
     *
     * 점수 분포 차이 주의:
     *  - vector cosine: 0~1, 보통 0.4~0.7 대역
     *  - Cohere relevance_score: 0~1, 보통 0.5~0.95 대역 (분포 다름)
     *  - 후속 threshold 비교는 같은 스케일이라고 가정하고 작동 — 운영 시 threshold 재튜닝 필요할 수 있음
     */
    @Override
    public Mono<List<SearchResult>> rerank(String query, List<SearchResult> candidates) {
        if (candidates == null || candidates.isEmpty()) return Mono.just(List.of());
        if (candidates.size() == 1) return Mono.just(candidates);

        List<String> documents = candidates.stream()
                .map(SearchResult::getContent)
                .collect(Collectors.toList());

        Map<String, Object> body = Map.of(
                "model", model,
                "query", query,
                "documents", documents,
                "top_n", candidates.size(),
                "return_documents", false
        );

        return webClient.post()
                .uri(API_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(10))
                .map(response -> applyRanking(candidates, response))
                .onErrorResume(e -> {
                    log.warn("[rerank] Cohere call failed, falling back to vector order: {}", e.getMessage());
                    return Mono.just(candidates);
                });
    }

    @SuppressWarnings("unchecked")
    private List<SearchResult> applyRanking(List<SearchResult> candidates, Map<String, Object> response) {
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        if (results == null || results.isEmpty()) return candidates;

        List<SearchResult> reranked = new ArrayList<>();
        for (Map<String, Object> r : results) {
            int idx = ((Number) r.get("index")).intValue();
            double score = ((Number) r.get("relevance_score")).doubleValue();
            if (idx < 0 || idx >= candidates.size()) continue;
            SearchResult original = candidates.get(idx);
            // 새 score 로 갈아끼우되 원본 metadata 보존
            SearchResult next = new SearchResult(
                    original.getId(),
                    original.getContent(),
                    score,
                    original.getMetadata(),
                    original.getSource()
            );
            reranked.add(next);
        }
        // Cohere가 이미 score desc로 반환하지만 명시적으로 정렬
        reranked.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());
        log.debug("[rerank] Cohere reranked {} -> {} items, top score {}",
                candidates.size(), reranked.size(),
                reranked.isEmpty() ? "n/a" : String.format("%.3f", reranked.get(0).getScore()));
        return reranked;
    }
}
