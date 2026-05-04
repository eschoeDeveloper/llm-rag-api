package io.github.eschoe.llmragapi.rag.rerank;

import io.github.eschoe.llmragapi.search.SearchResult;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 입력 그대로 반환하는 기본 Reranker.
 * RagRerankConfig가 다른 빈이 없을 때 이걸 등록한다.
 */
public class NoOpReranker implements Reranker {

    @Override
    public Mono<List<SearchResult>> rerank(String query, List<SearchResult> candidates) {
        return Mono.just(candidates);
    }
}
