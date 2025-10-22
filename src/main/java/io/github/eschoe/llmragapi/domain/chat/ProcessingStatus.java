package io.github.eschoe.llmragapi.domain.chat;

public enum ProcessingStatus {
    EMBEDDING("임베딩 생성 중..."),
    SEARCHING("관련 문서 검색 중..."),
    LOADING_HISTORY("대화 히스토리 로딩 중..."),
    GENERATING("답변 생성 중..."),
    COMPLETED("완료"),
    ERROR("오류 발생");

    private final String description;

    ProcessingStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
