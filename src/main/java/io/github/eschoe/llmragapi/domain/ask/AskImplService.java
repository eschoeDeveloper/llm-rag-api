package io.github.eschoe.llmragapi.domain.ask;

import io.github.eschoe.llmragapi.client.LlmContextClient;
import io.github.eschoe.llmragapi.domain.history.ChatHistoryStore;
import io.github.eschoe.llmragapi.domain.llm.LlmCacheService;
import io.github.eschoe.llmragapi.domain.llm.LlmConstants;
import io.github.eschoe.llmragapi.util.HashUtil;
import io.github.eschoe.llmragapi.util.LlmRagUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AskImplService implements AskService {

    private final LlmCacheService cache;
    private final HashUtil hash;
    private final LlmRagUtil llmRagUtil;
    private final LlmContextClient llmContextClient;
    private final ChatHistoryStore chatHistoryStore;

    public AskImplService(LlmCacheService cache, HashUtil hash, LlmRagUtil llmRagUtil, LlmContextClient llmContextClient, ChatHistoryStore chatHistoryStore) {
        this.cache = cache;
        this.hash = hash;
        this.llmRagUtil = llmRagUtil;
        this.llmContextClient = llmContextClient;
        this.chatHistoryStore = chatHistoryStore;
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

        // 세션 ID 처리 (없으면 기본값 사용)
        String sessionId = ask.getSessionId() != null ? ask.getSessionId() : "default-session";

        // 이전 대화 히스토리 가져오기 (최근 10개) - 타임아웃 5초
        return chatHistoryStore.recent(sessionId, 10)
                .collectList()
                .timeout(Duration.ofSeconds(5))
                .onErrorReturn(List.of())  // 타임아웃 시 빈 리스트 반환
                .flatMap(historyMessages -> {
                    
                    // 대화 히스토리를 프롬프트에 포함
                    String conversationContext = "";
                    if (!historyMessages.isEmpty()) {
                        conversationContext = "\n\nPREVIOUS CONVERSATION:\n" + 
                            historyMessages.stream()
                                .collect(Collectors.joining("\n"));
                    }

                    String userPrompt = llmQuery + conversationContext;

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
                            .flatMap(response -> {
                                // 대화 히스토리에 저장 (질문과 답변을 JSON 형태로)
                                String questionJson = String.format("{\"role\":\"user\",\"content\":\"%s\",\"timestamp\":\"%s\"}", 
                                        llmQuery.replace("\"", "\\\""), Instant.now());
                                String answerJson = String.format("{\"role\":\"assistant\",\"content\":\"%s\",\"timestamp\":\"%s\"}", 
                                        response.replace("\"", "\\\""), Instant.now());
                                
                                return chatHistoryStore.append(sessionId, questionJson)
                                        .then(chatHistoryStore.append(sessionId, answerJson))
                                        .thenReturn(new AskResponse(response, llmModel, 0, Map.of(
                                                "processingTime", Duration.between(startTime, Instant.now()).toMillis(),
                                                "config", ask.getConfig(),
                                                "timestamp", Instant.now(),
                                                "provider", llmProvider,
                                                "sessionId", sessionId
                                        )));
                            });
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
