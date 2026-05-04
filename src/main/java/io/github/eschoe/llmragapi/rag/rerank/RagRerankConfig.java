package io.github.eschoe.llmragapi.rag.rerank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class RagRerankConfig {

    private static final Logger log = LoggerFactory.getLogger(RagRerankConfig.class);

    /**
     * Cohere Rerank 활성 조건: COHERE_API_KEY 가 비어있지 않을 때.
     * 빈 문자열이면 NoOpReranker 가 활성됨.
     */
    @Bean
    @Primary
    public Reranker reranker(
            @Value("${spring.ai.cohere.api-key:${COHERE_API_KEY:}}") String cohereKey,
            @Value("${spring.ai.cohere.rerank-model:rerank-multilingual-v3.0}") String cohereModel,
            WebClient.Builder webClientBuilder) {
        if (cohereKey != null && !cohereKey.isBlank()) {
            log.info("[rerank] Cohere reranker enabled (model={})", cohereModel);
            return new CohereReranker(cohereKey, cohereModel, webClientBuilder);
        }
        log.info("[rerank] No Cohere API key — using NoOpReranker. " +
                "Set COHERE_API_KEY in .env.local to enable real reranking.");
        return new NoOpReranker();
    }

    @Bean
    @ConditionalOnMissingBean(Reranker.class)
    public Reranker fallbackNoOp() {
        return new NoOpReranker();
    }
}
