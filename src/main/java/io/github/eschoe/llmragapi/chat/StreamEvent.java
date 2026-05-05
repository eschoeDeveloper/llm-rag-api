package io.github.eschoe.llmragapi.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.eschoe.llmragapi.search.Citation;

import java.util.List;

/**
 * SSE 스트리밍 이벤트 — chatStream 에서 Flux 로 emit.
 *
 * 단일 record 로 4가지 타입 (meta/delta/done/error) 표현. 정적 팩토리로 의도 명시.
 *
 * 핫패스(토큰 단위 emit) 에서 매번 Map.of(...) 로 신규 객체 + entry 노드 만들던 오버헤드 제거.
 * record 는 단일 객체 + 필드 직접 접근이라 GC 부담 감소 + Jackson reflection 캐시 hit↑.
 *
 * @JsonInclude(NON_NULL) 로 필드 누락 시 wire 직렬화도 슬림.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StreamEvent(
        String type,
        List<Citation> citations,
        Boolean retrievalEmpty,
        String model,
        String content,
        String message
) {
    /** 검색 메타 — RAG retrieval 끝나고 LLM 호출 직전에 한 번 emit. */
    public static StreamEvent meta(List<Citation> citations, boolean retrievalEmpty, String model) {
        return new StreamEvent("meta", citations, retrievalEmpty, model, null, null);
    }

    /** LLM 토큰 1개. 가장 많이 emit (수십~수백 회). */
    public static StreamEvent delta(String content) {
        return new StreamEvent("delta", null, null, null, content, null);
    }

    /** 종료 신호 — history 저장 완료 후 emit. */
    public static StreamEvent done() {
        return new StreamEvent("done", null, null, null, null, null);
    }

    /** 스트림 도중 에러 발생 시. */
    public static StreamEvent error(String message) {
        return new StreamEvent("error", null, null, null, null, message);
    }
}
