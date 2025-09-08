package io.github.eschoe.llmragapi.domain.ask;

import io.github.eschoe.llmragapi.client.LlmContextClient;
import io.github.eschoe.llmragapi.domain.llm.LlmConstants;
import io.github.eschoe.llmragapi.util.LlmRagUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class AskImplService implements AskService {

    private final LlmRagUtil llmRagUtil;
    private final LlmContextClient llmContextClient;

    public AskImplService(LlmRagUtil llmRagUtil, LlmContextClient llmContextClient) {
        this.llmRagUtil = llmRagUtil;
        this.llmContextClient = llmContextClient;
    }

    @Override
    public Mono<String> askLegacy(AskBody ask) {

        String llmQuery = llmRagUtil.opt(ask.query());
        String llmProvider = !StringUtils.hasText(ask.provider()) ? LlmConstants.DEFAULT_PROVIDER : ask.provider();
        String llmModel = llmRagUtil.chooseModel(llmProvider, ask.model());

        String systemPrompt = LlmConstants.SYSTEM_PROMPT;

        if (llmQuery.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));

        return llmContextClient.chat(llmProvider, llmModel, systemPrompt, llmQuery);

    }

    @Override
    public Mono<AskResponse> askEnhanced(AskRequest ask) {

        Instant startTime = Instant.now();

        String llmQuery = llmRagUtil.opt(ask.getQuery());
        String llmProvider = LlmConstants.DEFAULT_PROVIDER; // 기본값 사용
        String llmModel = llmRagUtil.chooseModel(llmProvider, null); // 기본 모델 사용
        String systemPrompt = LlmConstants.SYSTEM_PROMPT;

        if(llmQuery.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));

        return llmContextClient.chat(llmProvider, llmModel, systemPrompt, llmQuery)
                .map(response -> {

                    Instant endTime = Instant.now();
                    Duration processingTime = Duration.between(endTime, Instant.now());

                    int tokens = 0;

                    Map<String, Object> metadata = Map.of(
                            "processingTime", processingTime.toMillis(),
                            "config", ask.getConfig(),
                            "timestamp", endTime,
                            "provider", llmProvider
                    );

                    return new AskResponse(response, llmModel, 0, metadata);

                });

    }
}
