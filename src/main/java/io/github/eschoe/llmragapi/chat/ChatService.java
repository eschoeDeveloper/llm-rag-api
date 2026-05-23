package io.github.eschoe.llmragapi.chat;

import io.github.eschoe.llmragapi.chat.history.ChatHistoryStore;
import io.github.eschoe.llmragapi.common.helper.HashUtil;
import io.github.eschoe.llmragapi.common.helper.LlmRagUtil;
import io.github.eschoe.llmragapi.llm.LlmClient;
import io.github.eschoe.llmragapi.llm.cache.LlmCacheService;
import io.github.eschoe.llmragapi.llm.cache.LlmConstants;
import io.github.eschoe.llmragapi.llm.prompt.PromptSerializer;
import io.github.eschoe.llmragapi.rag.RagPipeline;
import io.github.eschoe.llmragapi.rag.prompt.PromptComposer;
import io.github.eschoe.llmragapi.search.Citation;
import io.github.eschoe.llmragapi.search.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 채팅 오케스트레이션.
 *
 * 흐름 (chatEnhanced / chatStream 공통):
 *   1. 입력 검증
 *   2. RagPipeline 실행 → 검색 + rerank + threshold + budget
 *   3. PromptComposer → systemPrompt + userPrompt 빌드 (customPrompt 분기, history 합침)
 *   4. LLM 호출 (캐싱 또는 스트리밍)
 *   5. history 저장 + 응답 메타 빌드
 *
 * 검색·프롬프트 책임은 RagPipeline / PromptComposer 로 분리. 본 클래스는 오케스트레이션 + 캐싱 + 응답 빌드만.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** 사용자 query 최대 길이 — DoS·토큰 비용 폭주 차단. */
    private static final int MAX_QUERY_CHARS = 4000;
    /** customPrompt 최대 길이 — system prompt 자리라 더 관대. */
    private static final int MAX_CUSTOM_PROMPT_CHARS = 8000;
    /** SYSTEM_PROMPT 변경 시 bump → 캐시 자동 무효화. */
    private static final String CTX_VERSION = "ctx-v6";
    private static final String CACHE_PARTITION = "global";

    private final LlmCacheService cache;
    private final HashUtil hash;
    private final LlmClient llmClient;
    private final ChatHistoryStore chatHistoryStore;
    private final PromptSerializer promptSerializer;
    private final RagPipeline ragPipeline;
    private final PromptComposer promptComposer;

    public ChatService(LlmCacheService cache,
                       HashUtil hash,
                       LlmClient llmClient,
                       ChatHistoryStore chatHistoryStore,
                       PromptSerializer promptSerializer,
                       RagPipeline ragPipeline,
                       PromptComposer promptComposer) {
        this.cache = cache;
        this.hash = hash;
        this.llmClient = llmClient;
        this.chatHistoryStore = chatHistoryStore;
        this.promptSerializer = promptSerializer;
        this.ragPipeline = ragPipeline;
        this.promptComposer = promptComposer;
    }

    /** 비스트리밍 RAG — 캐시 hit/miss 적용 + 응답 메타 풀세트 반환. */
    public Mono<ChatResponse> chatEnhanced(ChatRequest request) {
        Instant startTime = Instant.now();
        try { validateInputLength(request); } catch (IllegalArgumentException e) { return Mono.error(e); }

        String query = LlmRagUtil.opt(request.getQuery());
        if (query.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));
        warnIfExternalSearchResults(request);

        String llmProvider = LlmConstants.DEFAULT_PROVIDER;
        String llmModel = LlmRagUtil.chooseModel(llmProvider, null);
        BudgetSplit budget = budgetFor(request);
        String sessionId = sessionIdOrDefault(request);

        return ragPipeline.retrieve(query, ragParamsFor(request, budget.context()))
                .flatMap(searchResults -> promptComposer
                        .compose(query, searchResults, sessionId, request.getCustomPrompt(), budget.history())
                        .flatMap(composed -> invokeLlmCached(llmProvider, llmModel, composed.systemPrompt(), composed.userPrompt())
                                .flatMap(answer -> persistHistory(sessionId, query, answer)
                                        .thenReturn(buildResponse(answer, llmModel, llmProvider, request,
                                                searchResults, composed.retrievalEmpty(), startTime, sessionId)))));
    }

    /**
     * 스트리밍 RAG — 토큰 단위 SSE 이벤트.
     * 캐싱은 의도적으로 미적용 (캐시 hit 시 일괄 응답이 되어버려 UX 의도와 어긋남).
     */
    public Flux<StreamEvent> chatStream(ChatRequest request) {
        try { validateInputLength(request); } catch (IllegalArgumentException e) { return Flux.error(e); }

        String query = LlmRagUtil.opt(request.getQuery());
        if (query.isBlank()) return Flux.error(new IllegalArgumentException("query is required"));
        warnIfExternalSearchResults(request);

        String llmProvider = LlmConstants.DEFAULT_PROVIDER;
        String llmModel = LlmRagUtil.chooseModel(llmProvider, null);
        BudgetSplit budget = budgetFor(request);
        String sessionId = sessionIdOrDefault(request);

        return ragPipeline.retrieve(query, ragParamsFor(request, budget.context()))
                .flatMapMany(searchResults -> {
                    List<Citation> citations = searchResults.stream().map(Citation::from).collect(Collectors.toList());
                    return promptComposer
                            .compose(query, searchResults, sessionId, request.getCustomPrompt(), budget.history())
                            .flatMapMany(composed -> streamWithMeta(query, llmModel, llmProvider, sessionId,
                                    citations, composed.systemPrompt(), composed.userPrompt(), composed.retrievalEmpty()));
                });
    }

    // ---------- 내부 헬퍼 ----------

    private static void validateInputLength(ChatRequest req) {
        String q = req.getQuery();
        if (q != null && q.length() > MAX_QUERY_CHARS) {
            throw new IllegalArgumentException("query 길이 초과 — 최대 " + MAX_QUERY_CHARS + "자");
        }
        String cp = req.getCustomPrompt();
        if (cp != null && cp.length() > MAX_CUSTOM_PROMPT_CHARS) {
            throw new IllegalArgumentException("customPrompt 길이 초과 — 최대 " + MAX_CUSTOM_PROMPT_CHARS + "자");
        }
    }

    /**
     * 외부에서 searchResults 주입 시도 감지 — 무조건 무시 (멀티테넌트 권한 우회 차단).
     * 다른 사용자의 청크 ID 를 박아 답변에 포함시키려는 시도 방지.
     */
    private static void warnIfExternalSearchResults(ChatRequest req) {
        if (req.getSearchResults() != null && !req.getSearchResults().isEmpty()) {
            log.warn("[security] external searchResults ignored (size={}) — server-side retrieval enforced",
                    req.getSearchResults().size());
        }
    }

    private static String sessionIdOrDefault(ChatRequest req) {
        return req.getSessionId() != null ? req.getSessionId() : "default-session";
    }

    /** 토큰 예산 분배 — 검색 1/2, 이력 1/4, 나머지 1/4(시스템·질문·LLM 출력). */
    private record BudgetSplit(int total, int context, int history) {}

    private BudgetSplit budgetFor(ChatRequest request) {
        int total = (request.getConfig() != null && request.getConfig().getMaxTokens() > 0)
                ? request.getConfig().getMaxTokens() : 4000;
        return new BudgetSplit(total, total / 2, total / 4);
    }

    /** RAG 파라미터 (top-k / threshold 포함) — RagPipeline 호출용. */
    private RagPipeline.Params ragParamsFor(ChatRequest request, int contextBudget) {
        int topK = (request.getConfig() != null && request.getConfig().getTopK() > 0)
                ? request.getConfig().getTopK() : 5;
        double threshold = (request.getConfig() != null && request.getConfig().getThreshold() > 0)
                ? request.getConfig().getThreshold() : 0.1;
        return new RagPipeline.Params(topK, threshold, contextBudget);
    }

    private Mono<String> invokeLlmCached(String provider, String model, String systemPrompt, String userPrompt) {
        String inputHash = hash.sha256(model, provider, CTX_VERSION, systemPrompt, userPrompt);
        String ctxHash = hash.sha256(CTX_VERSION, systemPrompt, userPrompt);

        Mono<String> promptMono = cache.getOrBuildPrompt(CACHE_PARTITION, ctxHash,
                () -> Mono.just(promptSerializer.toPromptJson(systemPrompt, userPrompt)));
        Mono<String> answerMono = cache.getOrInvoke(model, inputHash,
                () -> llmClient.chat(provider, model, systemPrompt, userPrompt));

        return promptMono.then(answerMono);
    }

    private Mono<Void> persistHistory(String sessionId, String query, String answer) {
        String questionJson = promptSerializer.toMessageJson("user", query);
        String answerJson = promptSerializer.toMessageJson("assistant", answer);
        return chatHistoryStore.append(sessionId, questionJson)
                .then(chatHistoryStore.append(sessionId, answerJson));
    }

    private ChatResponse buildResponse(String answer, String model, String provider,
                                       ChatRequest request, List<SearchResult> searchResults,
                                       boolean retrievalEmpty, Instant startTime, String sessionId) {
        return new ChatResponse(
                answer, model, 0,
                Map.of(
                        "processingTime", java.time.Duration.between(startTime, Instant.now()).toMillis(),
                        "config", request.getConfig(),
                        "searchResults", searchResults.size(),
                        "averageScore", searchResults.stream().mapToDouble(SearchResult::getScore).average().orElse(0.0),
                        "retrievalEmpty", retrievalEmpty,
                        "citations", searchResults.stream().map(Citation::from).collect(Collectors.toList()),
                        "timestamp", Instant.now(),
                        "provider", provider
                ),
                sessionId
        );
    }

    private Flux<StreamEvent> streamWithMeta(String query, String llmModel, String llmProvider, String sessionId,
                                             List<Citation> citations, String systemPrompt, String userPrompt,
                                             boolean retrievalEmpty) {
        StreamEvent metaEvent = StreamEvent.meta(citations, retrievalEmpty, llmModel);
        StringBuilder accumulated = new StringBuilder();

        Flux<StreamEvent> tokenFlux = llmClient.streamChat(llmProvider, llmModel, systemPrompt, userPrompt)
                .doOnNext(accumulated::append)
                .map(StreamEvent::delta);

        Mono<StreamEvent> doneEvent = Mono.defer(() ->
                persistHistory(sessionId, query, accumulated.toString()).thenReturn(StreamEvent.done()));

        return Flux.concat(Flux.just(metaEvent), tokenFlux, doneEvent);
    }
}
