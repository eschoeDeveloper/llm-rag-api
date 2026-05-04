package io.github.eschoe.llmragapi.rag.rerank;

import io.github.eschoe.llmragapi.search.SearchResult;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 검색 결과를 LLM 프롬프트에 넣기 전에 재정렬한다.
 * vector cosine 점수만으로는 답변 품질이 보장되지 않아 cross-encoder/rule 기반
 * 재정렬을 끼워 넣을 수 있는 확장 지점이다.
 */
public interface Reranker {

    Mono<List<SearchResult>> rerank(String query, List<SearchResult> candidates);
}
