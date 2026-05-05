package io.github.eschoe.llmragapi.search;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RAG 인용 — 답변 근거가 된 청크 정보.
 *
 * 클라이언트 UI 의 [근거 N] 칩 클릭 시 모달로 노출됨 (citation provenance).
 *
 * 이전엔 Map.of("documentId", ..., "chunkIndex", ...) 로 ChatService 안에서 5~6 곳에 반복 정의.
 * record 로 통일해 직렬화/역직렬화 오버헤드 감소 + 타입 안전성 확보.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Citation(
        String documentId,
        Integer chunkIndex,
        String title,
        double score,
        String content
) {
    /** SearchResult 로부터 Citation 생성 — metadata 의 키 이름 일관성 유지. */
    public static Citation from(SearchResult r) {
        Object docId = r.getMetadata().get("documentId");
        Object chunkIdx = r.getMetadata().get("chunkIndex");
        Object title = r.getMetadata().get("title");
        return new Citation(
                docId == null ? "" : docId.toString(),
                chunkIdx instanceof Integer i ? i : (chunkIdx == null ? 0 : Integer.parseInt(chunkIdx.toString())),
                title == null ? "" : title.toString(),
                r.getScore(),
                r.getContent()
        );
    }
}
