package io.github.eschoe.llmragapi.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 임베딩 모델 단일 설정 소스.
 * 이전에는 ChatService, SearchService, AdvancedSearchService가 각자 @Value를 들고 있어
 * 한 곳만 바꾸면 모델이 mismatch 되는 위험이 있었다.
 */
@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {

    /** 사용할 임베딩 모델. 기본값은 OpenAI text-embedding-3-small (1536차원). */
    private String model = "text-embedding-3-small";

    /** 모델이 반환해야 하는 차원. 스키마와 일치해야 한다. */
    private int expectedDim = 1536;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getExpectedDim() {
        return expectedDim;
    }

    public void setExpectedDim(int expectedDim) {
        this.expectedDim = expectedDim;
    }
}
