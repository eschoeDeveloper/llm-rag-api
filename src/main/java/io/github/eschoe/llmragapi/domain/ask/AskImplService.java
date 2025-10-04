package io.github.eschoe.llmragapi.domain.ask;

import io.github.eschoe.llmragapi.client.LlmContextClient;
import io.github.eschoe.llmragapi.domain.llm.LlmCacheService;
import io.github.eschoe.llmragapi.domain.llm.LlmConstants;
import io.github.eschoe.llmragapi.util.HashUtil;
import io.github.eschoe.llmragapi.util.LlmRagUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class AskImplService implements AskService {

    private final LlmCacheService cache;
    private final HashUtil hash;
    private final LlmRagUtil llmRagUtil;
    private final LlmContextClient llmContextClient;

    public AskImplService(LlmCacheService cache, HashUtil hash, LlmRagUtil llmRagUtil, LlmContextClient llmContextClient) {
        this.cache = cache;
        this.hash = hash;
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

        String userPrompt = llmQuery;

        if(llmQuery.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));

        // 캐시 파티션
        final String ctxVersion = "ctx-v1";
        final String partitionId = "global";

        // 프롬프트 캐시 해시 ( 버전 + system + user )
        String ctxHash = hash.sha256(ctxVersion, systemPrompt, userPrompt);

        Mono<String> promptMono = cache.getOrBuildPrompt(
                partitionId,
                ctxHash,
                () -> Mono.just(toPromptJson(systemPrompt, userPrompt))
        );

        // 응답 캐시 + 분산락 : 모델 / 버전 / 프롬프트 기반 입력 해시
        String inputHash = hash.sha256(llmModel, llmProvider, ctxVersion, systemPrompt, userPrompt);

        Mono<String> answerMono = cache.getOrInvoke(
                llmModel,
                inputHash,
                () -> llmContextClient.chat(llmProvider, llmModel, systemPrompt, userPrompt)
        );

        return promptMono
                .then(answerMono)
                .map(response -> {
                    Instant endTime = Instant.now();
                    Duration processingTime = Duration.between(startTime, endTime); // ← 기존 버그 수정

                    Map<String, Object> metadata = Map.of(
                            "processingTime", processingTime.toMillis(),
                            "config", ask.getConfig(),
                            "timestamp", endTime,
                            "provider", llmProvider
                    );

                    return new AskResponse(response, llmModel, 0, metadata);
                }).onErrorResume(error -> {
                    error.printStackTrace();
                    return Mono.error(error);
                });

    }

    // 직렬화 유틸(기존 스타일 유지)
    private String toPromptJson(String system, String user) {
        return "{\"system\":" + quote(system) + ",\"user\":" + quote(user) + "}";
    }
    private String quote(String s) { return "\"" + s.replace("\"","\\\"") + "\""; }

}
