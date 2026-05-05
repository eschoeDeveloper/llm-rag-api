package io.github.eschoe.llmragapi.ask;

import io.github.eschoe.llmragapi.chat.history.ChatHistoryStore;
import io.github.eschoe.llmragapi.common.helper.HashUtil;
import io.github.eschoe.llmragapi.common.helper.LlmRagUtil;
import io.github.eschoe.llmragapi.llm.LlmClient;
import io.github.eschoe.llmragapi.llm.cache.LlmCacheService;
import io.github.eschoe.llmragapi.llm.cache.LlmConstants;
import io.github.eschoe.llmragapi.llm.prompt.PromptSerializer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AskService {

    private final LlmCacheService cache;
    private final HashUtil hash;
    private final LlmRagUtil llmRagUtil;
    private final LlmClient llmClient;
    private final ChatHistoryStore chatHistoryStore;
    private final PromptSerializer promptSerializer;

    public AskService(LlmCacheService cache,
                          HashUtil hash,
                          LlmRagUtil llmRagUtil,
                          LlmClient llmClient,
                          ChatHistoryStore chatHistoryStore,
                          PromptSerializer promptSerializer) {
        this.cache = cache;
        this.hash = hash;
        this.llmRagUtil = llmRagUtil;
        this.llmClient = llmClient;
        this.chatHistoryStore = chatHistoryStore;
        this.promptSerializer = promptSerializer;
    }

    
    public Mono<String> askLegacy(AskBody ask) {
        String llmQuery = llmRagUtil.opt(ask.query());
        String llmProvider = !StringUtils.hasText(ask.provider()) ? LlmConstants.DEFAULT_PROVIDER : ask.provider();
        String llmModel = llmRagUtil.chooseModel(llmProvider, ask.model());

        if (llmQuery.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));

        return llmClient.chat(llmProvider, llmModel, LlmConstants.SYSTEM_PROMPT, llmQuery);
    }

    
    /** 입력 길이 한도 — DoS / 토큰 비용 폭주 차단. */
    private static final int MAX_QUERY_CHARS = 4000;
    private static final int MAX_CUSTOM_PROMPT_CHARS = 8000;

    public Mono<AskResponse> askEnhanced(AskRequest ask) {

        Instant startTime = Instant.now();

        if (ask.getQuery() != null && ask.getQuery().length() > MAX_QUERY_CHARS) {
            return Mono.error(new IllegalArgumentException("query 길이 초과 — 최대 " + MAX_QUERY_CHARS + "자"));
        }
        if (ask.getCustomPrompt() != null && ask.getCustomPrompt().length() > MAX_CUSTOM_PROMPT_CHARS) {
            return Mono.error(new IllegalArgumentException("customPrompt 길이 초과 — 최대 " + MAX_CUSTOM_PROMPT_CHARS + "자"));
        }

        String llmQuery = LlmRagUtil.opt(ask.getQuery());
        if (llmQuery.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));

        String llmProvider = LlmConstants.DEFAULT_PROVIDER;
        String llmModel = LlmRagUtil.chooseModel(llmProvider, null);
        // customPrompt 가 있으면 기본 SYSTEM_PROMPT 대신 사용
        String systemPrompt = (ask.getCustomPrompt() != null && !ask.getCustomPrompt().isBlank())
                ? ask.getCustomPrompt()
                : LlmConstants.SYSTEM_PROMPT;

        String sessionId = ask.getSessionId() != null ? ask.getSessionId() : "default-session";

        return chatHistoryStore.recent(sessionId, 10)
                .collectList()
                .timeout(Duration.ofSeconds(5))
                .onErrorReturn(List.of())
                .flatMap(historyMessages -> {

                    String conversationContext = historyMessages.isEmpty()
                            ? ""
                            : "\n\nPREVIOUS CONVERSATION:\n" + String.join("\n", historyMessages);

                    String userPrompt = llmQuery + conversationContext;

                    final String ctxVersion = "ctx-v6";
                    final String partitionId = "global";

                    String ctxHash = hash.sha256(ctxVersion, systemPrompt, userPrompt);

                    Mono<String> promptMono = cache.getOrBuildPrompt(
                            partitionId,
                            ctxHash,
                            () -> Mono.just(promptSerializer.toPromptJson(systemPrompt, userPrompt))
                    );

                    String inputHash = hash.sha256(llmModel, llmProvider, ctxVersion, systemPrompt, userPrompt);

                    Mono<String> answerMono = cache.getOrInvoke(
                            llmModel,
                            inputHash,
                            () -> llmClient.chat(llmProvider, llmModel, systemPrompt, userPrompt)
                    );

                    return promptMono
                            .then(answerMono)
                            .flatMap(response -> {
                                String questionJson = promptSerializer.toMessageJson("user", llmQuery);
                                String answerJson = promptSerializer.toMessageJson("assistant", response);

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
                });
    }

}
