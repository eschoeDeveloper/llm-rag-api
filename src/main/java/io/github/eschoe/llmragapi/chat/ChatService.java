package io.github.eschoe.llmragapi.chat;

import io.github.eschoe.llmragapi.chat.history.ChatHistoryStore;
import io.github.eschoe.llmragapi.common.helper.HashUtil;
import io.github.eschoe.llmragapi.common.helper.LlmRagUtil;
import io.github.eschoe.llmragapi.common.metrics.MetricsService;
import io.github.eschoe.llmragapi.llm.LlmClient;
import io.github.eschoe.llmragapi.llm.cache.LlmCacheService;
import io.github.eschoe.llmragapi.llm.cache.LlmConstants;
import io.github.eschoe.llmragapi.llm.prompt.PromptSerializer;
import io.github.eschoe.llmragapi.rag.config.EmbeddingProperties;
import io.github.eschoe.llmragapi.rag.prompt.ContextBudget;
import io.github.eschoe.llmragapi.rag.rerank.Reranker;
import io.github.eschoe.llmragapi.rag.retrieval.EmbeddingQueryDao;
import io.github.eschoe.llmragapi.search.Citation;
import io.github.eschoe.llmragapi.search.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final LlmCacheService cache;
    private final HashUtil hash;
    private final LlmRagUtil llmRagUtil;
    private final LlmClient llmClient;
    private final EmbeddingQueryDao embeddingQueryDao;
    private final ChatHistoryStore chatHistoryStore;
    private final Reranker reranker;
    private final ContextBudget contextBudget;
    private final PromptSerializer promptSerializer;
    private final EmbeddingProperties embeddingProps;
    private final MetricsService metrics;

    public ChatService(LlmCacheService cache,
                           HashUtil hash,
                           LlmRagUtil llmRagUtil,
                           LlmClient llmClient,
                           EmbeddingQueryDao embeddingQueryDao,
                           ChatHistoryStore chatHistoryStore,
                           Reranker reranker,
                           ContextBudget contextBudget,
                           PromptSerializer promptSerializer,
                           EmbeddingProperties embeddingProps,
                           MetricsService metrics) {
        this.cache = cache;
        this.hash = hash;
        this.llmRagUtil = llmRagUtil;
        this.llmClient = llmClient;
        this.embeddingQueryDao = embeddingQueryDao;
        this.chatHistoryStore = chatHistoryStore;
        this.reranker = reranker;
        this.contextBudget = contextBudget;
        this.promptSerializer = promptSerializer;
        this.embeddingProps = embeddingProps;
        this.metrics = metrics;
    }

    
    public Mono<String> chatLegacy(ChatBody chatBody) {
        String llmQuery = LlmRagUtil.opt(chatBody.query());
        String llmProvider = !StringUtils.hasText(chatBody.provider()) ? LlmConstants.DEFAULT_PROVIDER : chatBody.provider();
        String llmModel = LlmRagUtil.chooseModel(llmProvider, chatBody.model());

        int k = (chatBody.topK() != null && chatBody.topK() > 0) ? chatBody.topK() : 5;

        if (llmQuery.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));

        Mono<float[]> embedMono = (chatBody.embedding() != null && chatBody.embedding().length > 0)
                ? Mono.just(chatBody.embedding())
                : llmClient.embed(embeddingProps.getModel(), llmQuery);

        return embedMono
                .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, k))
                .collectList()
                .flatMap(embedRow -> {
                    String _context = embedRow.stream()
                            .map(row -> "- " + contextBudget.snippet(row.getContent()))
                            .collect(Collectors.joining("\n"));

                    String systemPrompt = LlmConstants.SYSTEM_PROMPT;
                    String userPrompt = "QUESTION:\n" + llmQuery + "\n\nCONTEXT:\n" + _context;

                    return llmClient.chat(llmProvider, llmModel, systemPrompt, userPrompt);
                });
    }

    /**
     * RAG 채팅 메인 파이프라인.
     *
     * 흐름:
     *   1. 임베딩 생성 → vector 검색 (candidate pool = k * 2)
     *   2. Reranker (Cohere or NoOp) 로 재정렬
     *   3. threshold 필터링 + top-k 자르기 (rerank 이후 적용 — 약한 cosine 으로 잘려나간 좋은 후보 살림)
     *   4. ContextBudget 으로 토큰 한도 내 fit
     *   5. PREVIOUS_CONVERSATION 추가 (history budget = maxTokens/4)
     *   6. 프롬프트 캐시 + 응답 캐시 (둘 다 ctxVersion + 입력해시 기준)
     *   7. 응답 + citations + warnings + retrievalEmpty 메타 반환
     *
     * 토큰 분배:
     *   - 검색 컨텍스트 = maxTokens / 2
     *   - 대화 이력     = maxTokens / 4
     *   - 나머지 1/4   = system prompt + question + LLM 출력 여유분
     *
     * ctxVersion 은 SYSTEM_PROMPT 변경 시 bump 해서 캐시 무효화.
     */
    public Mono<ChatResponse> chatEnhanced(ChatRequest request) {

        Instant startTime = Instant.now();

        String llmQuery = LlmRagUtil.opt(request.getQuery());
        if (llmQuery.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));

        String llmProvider = LlmConstants.DEFAULT_PROVIDER;
        String llmModel = LlmRagUtil.chooseModel(llmProvider, null);

        int k = (request.getConfig() != null && request.getConfig().getTopK() > 0)
                ? request.getConfig().getTopK() : 5;

        double threshold = (request.getConfig() != null && request.getConfig().getThreshold() > 0)
                ? request.getConfig().getThreshold() : 0.1;

        // maxTokens 의 절반은 검색 컨텍스트, 1/4은 대화 이력에 배정.
        int totalBudget = (request.getConfig() != null && request.getConfig().getMaxTokens() > 0)
                ? request.getConfig().getMaxTokens()
                : 4000;
        int contextTokenBudget = totalBudget / 2;
        int historyTokenBudget = totalBudget / 4;

        // reranker 가 제대로 작동할 수 있도록 candidate pool 을 finalK 의 2배로 더 가져온다.
        int candidatePool = Math.max(k * 2, 10);

        final String ctxVersion = "ctx-v6";
        final String partitionId = "global";
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default-session";

        Mono<List<SearchResult>> searchResultsMono;
        if (request.getSearchResults() != null && !request.getSearchResults().isEmpty()) {
            searchResultsMono = Mono.just(request.getSearchResults());
        } else {
            // threshold 는 rerank 이후 적용 — 좋은 후보가 cosine 점수 낮다고 잘려나가지 않도록.
            searchResultsMono = llmClient.embed(embeddingProps.getModel(), llmQuery)
                    .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, candidatePool))
                    .collectList()
                    .map(rows -> rows.stream()
                            .map(r -> new SearchResult(
                                    String.valueOf(r.getId()),
                                    r.getContent(),
                                    r.getScore() != null ? r.getScore() : 0.0,
                                    Map.of(
                                            "title", r.getTitle() != null ? r.getTitle() : "",
                                            "createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "",
                                            "documentId", r.getDocumentId() != null ? r.getDocumentId() : "",
                                            "chunkIndex", r.getChunkIndex() != null ? r.getChunkIndex() : 0
                                    ),
                                    "database"))
                            .collect(Collectors.toList()));
        }

        return searchResultsMono
                .doOnNext(raw -> log.debug("[RAG] candidate pool: {} rows, scores={}",
                        raw.size(),
                        raw.stream().map(r -> String.format("%.3f", r.getScore())).limit(10).collect(Collectors.toList())))
                .flatMap(rawResults -> reranker.rerank(llmQuery, rawResults))
                .map(reranked -> reranked.stream()
                        .filter(r -> r.getScore() >= threshold)
                        .limit(k)
                        .collect(Collectors.toList()))
                .doOnNext(filtered -> log.debug("[RAG] after threshold>={} & top-{}: {} kept",
                        threshold, k, filtered.size()))
                .map(filtered -> contextBudget.fit(filtered, contextTokenBudget))
                .doOnNext(fitted -> log.debug("[RAG] after token budget {}: {} kept", contextTokenBudget, fitted.size()))
                .flatMap(searchResults -> {

                    boolean retrievalEmpty = searchResults.isEmpty();
                    if (retrievalEmpty) metrics.incr("retrieval_empty").subscribe();
                    String contextBlock;
                    if (retrievalEmpty) {
                        contextBlock = "(관련 컨텍스트를 찾지 못했습니다.)";
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < searchResults.size(); i++) {
                            SearchResult r = searchResults.get(i);
                            sb.append(String.format("[근거 %d] (점수: %.3f) %s%n",
                                    i + 1, r.getScore(), contextBudget.snippet(r.getContent())));
                        }
                        contextBlock = sb.toString().trim();
                    }

                    return chatHistoryStore.recent(sessionId, 10)
                            .collectList()
                            .timeout(Duration.ofSeconds(5))
                            .onErrorReturn(List.of())
                            .map(rawHistory -> contextBudget.fitStringsLatestFirst(rawHistory, historyTokenBudget))
                            .flatMap(historyMessages -> {

                                String conversationContext = historyMessages.isEmpty()
                                        ? ""
                                        : "\n\nPREVIOUS CONVERSATION:\n" + String.join("\n", historyMessages);

                                // customPrompt 가 있으면 기본 SYSTEM_PROMPT 대신 사용
                                // (사용자가 어시스턴트 톤/제약을 직접 정의할 수 있게)
                                String systemPrompt = (request.getCustomPrompt() != null && !request.getCustomPrompt().isBlank())
                                        ? request.getCustomPrompt()
                                        : LlmConstants.SYSTEM_PROMPT;
                                String userPrompt = "QUESTION:\n" + llmQuery
                                        + "\n\nCONTEXT:\n" + contextBlock
                                        + conversationContext;

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

                                return promptMono.then(answerMono)
                                        .flatMap(answer -> {
                                            String questionJson = promptSerializer.toMessageJson("user", llmQuery);
                                            String answerJson = promptSerializer.toMessageJson("assistant", answer);

                                            return chatHistoryStore.append(sessionId, questionJson)
                                                    .then(chatHistoryStore.append(sessionId, answerJson))
                                                    .thenReturn(new ChatResponse(
                                                            answer,
                                                            llmModel,
                                                            0,
                                                            Map.of(
                                                                    "processingTime", Duration.between(startTime, Instant.now()).toMillis(),
                                                                    "config", request.getConfig(),
                                                                    "searchResults", searchResults.size(),
                                                                    "averageScore", searchResults.stream()
                                                                            .mapToDouble(SearchResult::getScore)
                                                                            .average().orElse(0.0),
                                                                    "retrievalEmpty", retrievalEmpty,
                                                                    // citations 에 content 도 포함 (frontend 모달에서 원본 청크 표시 — citation provenance)
                                                                    // Citation record 사용 — Map.of() 보다 직렬화/GC 부담 적음.
                                                                    "citations", searchResults.stream()
                                                                            .map(Citation::from)
                                                                            .collect(Collectors.toList()),
                                                                    "timestamp", Instant.now(),
                                                                    "provider", llmProvider
                                                            ),
                                                            sessionId
                                                    ));
                                        });
                            });
                });
    }

    /**
     * 스트리밍 RAG 채팅.
     *
     * 흐름:
     *   1. 검색·rerank·budget 동기 처리 (chatEnhanced 와 동일)
     *   2. citations 메타를 첫 SSE 이벤트로 emit
     *   3. LLM 응답을 토큰 단위로 stream
     *   4. 완료 시 history 저장 (ASSISTANT 메시지 누적)
     *
     * 캐싱은 생략 — 스트리밍에서는 캐시 hit 시 일괄 응답이 되어버려 UX 의도와 어긋남.
     * 동일 prompt 재사용은 일반 chatEnhanced 엔드포인트로 유도.
     *
     * 이벤트 형식:
     *   data: {"type":"meta","citations":[...]}
     *   data: {"type":"delta","content":"안녕"}
     *   data: {"type":"delta","content":"하세요"}
     *   data: {"type":"done"}
     */
    public Flux<StreamEvent> chatStream(ChatRequest request) {
        String llmQuery = LlmRagUtil.opt(request.getQuery());
        if (llmQuery.isBlank()) {
            return Flux.error(new IllegalArgumentException("query is required"));
        }

        String llmProvider = LlmConstants.DEFAULT_PROVIDER;
        String llmModel = LlmRagUtil.chooseModel(llmProvider, null);

        int k = (request.getConfig() != null && request.getConfig().getTopK() > 0)
                ? request.getConfig().getTopK() : 5;
        double threshold = (request.getConfig() != null && request.getConfig().getThreshold() > 0)
                ? request.getConfig().getThreshold() : 0.1;
        int totalBudget = (request.getConfig() != null && request.getConfig().getMaxTokens() > 0)
                ? request.getConfig().getMaxTokens() : 4000;
        int contextTokenBudget = totalBudget / 2;
        int historyTokenBudget = totalBudget / 4;
        int candidatePool = Math.max(k * 2, 10);
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default-session";

        // 1) 검색 → rerank → threshold → top-k → budget (동기 부분)
        Mono<List<SearchResult>> retrievedMono = llmClient.embed(embeddingProps.getModel(), llmQuery)
                .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, candidatePool))
                .collectList()
                .map(rows -> rows.stream()
                        .map(r -> new SearchResult(
                                String.valueOf(r.getId()),
                                r.getContent(),
                                r.getScore() != null ? r.getScore() : 0.0,
                                Map.of(
                                        "title", r.getTitle() != null ? r.getTitle() : "",
                                        "createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "",
                                        "documentId", r.getDocumentId() != null ? r.getDocumentId() : "",
                                        "chunkIndex", r.getChunkIndex() != null ? r.getChunkIndex() : 0
                                ),
                                "database"))
                        .collect(Collectors.toList()))
                .flatMap(rawResults -> reranker.rerank(llmQuery, rawResults))
                .map(reranked -> reranked.stream()
                        .filter(r -> r.getScore() >= threshold)
                        .limit(k)
                        .collect(Collectors.toList()))
                .map(filtered -> contextBudget.fit(filtered, contextTokenBudget));

        return retrievedMono.flatMapMany(searchResults -> {
            // citations — Citation record list
            List<Citation> citations = searchResults.stream()
                    .map(Citation::from)
                    .collect(Collectors.toList());

            boolean retrievalEmpty = searchResults.isEmpty();
            String contextBlock;
            if (retrievalEmpty) {
                contextBlock = "(관련 컨텍스트를 찾지 못했습니다.)";
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < searchResults.size(); i++) {
                    SearchResult r = searchResults.get(i);
                    sb.append(String.format("[근거 %d] (점수: %.3f) %s%n",
                            i + 1, r.getScore(), contextBudget.snippet(r.getContent())));
                }
                contextBlock = sb.toString().trim();
            }

            return chatHistoryStore.recent(sessionId, 10)
                    .collectList()
                    .timeout(Duration.ofSeconds(5))
                    .onErrorReturn(List.of())
                    .map(rawHistory -> contextBudget.fitStringsLatestFirst(rawHistory, historyTokenBudget))
                    .flatMapMany(historyMessages -> {
                        String conversationContext = historyMessages.isEmpty()
                                ? ""
                                : "\n\nPREVIOUS CONVERSATION:\n" + String.join("\n", historyMessages);

                        String systemPrompt = (request.getCustomPrompt() != null && !request.getCustomPrompt().isBlank())
                                ? request.getCustomPrompt()
                                : LlmConstants.SYSTEM_PROMPT;
                        String userPrompt = "QUESTION:\n" + llmQuery
                                + "\n\nCONTEXT:\n" + contextBlock
                                + conversationContext;

                        StringBuilder accumulated = new StringBuilder();

                        StreamEvent metaEvent = StreamEvent.meta(citations, retrievalEmpty, llmModel);

                        Flux<StreamEvent> tokenFlux = llmClient.streamChat(llmProvider, llmModel, systemPrompt, userPrompt)
                                .doOnNext(accumulated::append)
                                .map(StreamEvent::delta);

                        Mono<StreamEvent> doneEvent = Mono.defer(() -> {
                            // 완료 시 history 저장
                            String questionJson = promptSerializer.toMessageJson("user", llmQuery);
                            String answerJson = promptSerializer.toMessageJson("assistant", accumulated.toString());
                            return chatHistoryStore.append(sessionId, questionJson)
                                    .then(chatHistoryStore.append(sessionId, answerJson))
                                    .thenReturn(StreamEvent.done());
                        });

                        return Flux.concat(
                                Flux.just(metaEvent),
                                tokenFlux,
                                doneEvent
                        );
                    });
        });
    }
}
