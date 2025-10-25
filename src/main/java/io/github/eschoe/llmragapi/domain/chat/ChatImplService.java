package io.github.eschoe.llmragapi.domain.chat;

import io.github.eschoe.llmragapi.client.LlmContextClient;
import io.github.eschoe.llmragapi.dao.EmbeddingQueryDao;
import io.github.eschoe.llmragapi.domain.history.ChatHistoryStore;
import io.github.eschoe.llmragapi.domain.llm.LlmCacheService;
import io.github.eschoe.llmragapi.domain.llm.LlmConstants;
import io.github.eschoe.llmragapi.domain.search.SearchResult;
import io.github.eschoe.llmragapi.util.HashUtil;
import io.github.eschoe.llmragapi.util.LlmRagUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatImplService implements ChatService {

    @Value("${APP_EMBEDDING_MODEL:text-embedding-3-small}")
    private String embeddingModel;

    private final LlmCacheService cache;
    private final HashUtil hash;
    private final LlmRagUtil llmRagUtil;
    private final LlmContextClient llmContextClient;
    private final EmbeddingQueryDao embeddingQueryDao;
    private final ChatHistoryStore chatHistoryStore;

    public ChatImplService(LlmCacheService cache, HashUtil hash, LlmRagUtil llmRagUtil, LlmContextClient llmContextClient, EmbeddingQueryDao embeddingQueryDao, ChatHistoryStore chatHistoryStore) {
        this.cache = cache;
        this.hash = hash;
        this.llmRagUtil = llmRagUtil;
        this.llmContextClient = llmContextClient;
        this.embeddingQueryDao = embeddingQueryDao;
        this.chatHistoryStore = chatHistoryStore;
    }

    // 기존 메서드 (그대로 유지)
    @Override
    public Mono<String> chatLegacy(ChatBody chatBody) {
        String llmQuery = llmRagUtil.opt(chatBody.query());
        String llmProvider = !StringUtils.hasText(chatBody.provider()) ? LlmConstants.DEFAULT_PROVIDER : chatBody.provider();
        String llmModel = llmRagUtil.chooseModel(llmProvider, chatBody.model());

        int k = (chatBody.topK() != null && chatBody.topK() > 0) ? chatBody.topK() : 5;

        if (llmQuery.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));

        Mono<float[]> embedMono = (chatBody.embedding() != null && chatBody.embedding().length > 0)
                ? Mono.just(chatBody.embedding())
                : llmContextClient.embed(embeddingModel, llmQuery);

        return embedMono
                .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, k))
                .collectList()
                .flatMap(embedRow -> {
                    String _context = embedRow.stream()
                            .map(row -> "- " + llmRagUtil.safeSnippet(row.getContent()))
                            .collect(Collectors.joining("\n"));

                    String systemPrompt = LlmConstants.SYSTEM_PROMPT;
                    String userPrompt = "QUESTION:\n" + llmQuery + "\n\nCONTEXT:\n" + _context;

                    return llmContextClient.chat(llmProvider, llmModel, systemPrompt, userPrompt);
                });
    }

    // 새로운 메서드 추가
    // 새로운 메서드 추가
//    public Mono<ChatResponse> chatEnhanced(ChatRequest request) {
//        Instant startTime = Instant.now();
//
//        String llmQuery = llmRagUtil.opt(request.getQuery());
//        String llmProvider = LlmConstants.DEFAULT_PROVIDER;
//        String llmModel = llmRagUtil.chooseModel(llmProvider, null);
//
//        if (llmQuery.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));
//
//        // 검색 결과가 있으면 활용, 없으면 새로 검색
//        Mono<List<SearchResult>> searchResultsMono;
//        if (request.getSearchResults() != null && !request.getSearchResults().isEmpty()) {
//            searchResultsMono = Mono.just(request.getSearchResults());
//        } else {
//            // 벡터 검색 수행
//            int k = request.getConfig() != null ? request.getConfig().getTopK() : 5;
//            double threshold = request.getConfig() != null ? request.getConfig().getThreshold() : 0.7;
//
//            searchResultsMono = llmContextClient.embed(embeddingModel, llmQuery)
//                    .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, k))
//                    .collectList()
//                    .map(embedRows -> embedRows.stream()
//                            .filter(row -> row.getScore() != null && row.getScore() >= threshold) // 임계값 필터링
//                            .map(row -> new SearchResult(
//                                    String.valueOf(row.getId()),
//                                    row.getContent(),
//                                    row.getScore() != null ? row.getScore() : 0.0,
//                                    Map.of(
//                                            "title", row.getTitle() != null ? row.getTitle() : "",
//                                            "createdAt", row.getCreatedAt() != null ? row.getCreatedAt().toString() : ""
//                                    ),
//                                    "database"
//                            ))
//                            .collect(Collectors.toList()));
//        }
//
//        return searchResultsMono
//                .flatMap(searchResults -> {
//                    // 컨텍스트 구성 (점수 정보 포함)
//                    String _context = searchResults.stream()
//                            .map(result -> String.format("- %s (점수: %.3f)",
//                                    llmRagUtil.safeSnippet(result.getContent()),
//                                    result.getScore()))
//                            .collect(Collectors.joining("\n"));
//
//                    String systemPrompt = LlmConstants.SYSTEM_PROMPT;
//                    String userPrompt = "QUESTION:\n" + llmQuery + "\n\nCONTEXT:\n" + _context;
//
//                    return llmContextClient.chat(llmProvider, llmModel, systemPrompt, userPrompt)
//                            .map(response -> {
//                                Instant endTime = Instant.now();
//                                Duration processingTime = Duration.between(startTime, endTime);
//
//                                return new ChatResponse(
//                                        response,
//                                        llmModel,
//                                        0, // LlmContextClient에서 토큰 정보를 제공하지 않으면 0
//                                        Map.of(
//                                                "processingTime", processingTime.toMillis(),
//                                                "config", request.getConfig(),
//                                                "searchResults", searchResults.size(),
//                                                "averageScore", searchResults.stream()
//                                                        .mapToDouble(SearchResult::getScore)
//                                                        .average()
//                                                        .orElse(0.0),
//                                                "timestamp", endTime,
//                                                "provider", llmProvider
//                                        )
//                                );
//                            });
//                });
//    }


    @Override
    public Mono<ChatResponse> chatEnhanced(ChatRequest request) {

        Instant startTime = Instant.now();

        String llmQuery = LlmRagUtil.opt(request.getQuery());
        if(llmQuery.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));

        String llmProvider = LlmConstants.DEFAULT_PROVIDER;
        String llmModel = LlmRagUtil.chooseModel(llmProvider, null);

        int k = (request.getConfig() != null && request.getConfig().getTopK() > 0)
                ? request.getConfig().getTopK() : 5;

        double threshold = (request.getConfig() != null && request.getConfig().getThreshold() > 0)
                ? request.getConfig().getThreshold() : 0.1;  // 임계값을 0.1로 더 낮춤

        final String ctxVersion = "ctx-v1";           // 프롬프트 스키마 버전
        final String partitionId = "global";            // 고정 파티션 키
        
        // 세션 ID 처리 (없으면 기본값 사용)
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default-session";

        // ---- 검색 결과 준비 (있으면 사용, 없으면 임베딩→TopK) ----
        Mono<List<SearchResult>> searchResultsMono;

        if (request.getSearchResults() != null && !request.getSearchResults().isEmpty()) {
            searchResultsMono = Mono.just(request.getSearchResults());
        } else {

            searchResultsMono = llmContextClient.embed(embeddingModel, llmQuery)
                    .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, k))
                    .collectList()
                    .map(rows -> {
                        System.out.println("[ChatImplService] Raw search results count: " + rows.size());
                        if (!rows.isEmpty()) {
                            System.out.println("[ChatImplService] Raw scores: " + 
                                rows.stream().map(r -> r.getScore()).collect(Collectors.toList()));
                        }
                        return rows.stream()
                            .peek(r -> System.out.println("[ChatImplService] Filtering: score=" + r.getScore() + ", threshold=" + threshold + ", pass=" + (r.getScore() != null && r.getScore() >= threshold)))
                            .filter(r -> r.getScore() != null && r.getScore() >= threshold)
                            .map(r -> new SearchResult(
                                String.valueOf(r.getId()),
                                r.getContent(),
                                r.getScore() != null ? r.getScore() : 0.0,
                                Map.of(
                                    "title", r.getTitle() != null ? r.getTitle() : "",
                                    "createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : ""
                                ),
                                "database"))
                            .collect(Collectors.toList());
                    });
        }

        return searchResultsMono.flatMap(searchResults -> {

            // 디버그 로그 추가
            System.out.println("[ChatImplService] Search results count: " + searchResults.size());
            if (!searchResults.isEmpty()) {
                System.out.println("[ChatImplService] Top result score: " + searchResults.get(0).getScore());
                System.out.println("[ChatImplService] Top result content preview: " + 
                    (searchResults.get(0).getContent().length() > 100 ? 
                     searchResults.get(0).getContent().substring(0, 100) + "..." : 
                     searchResults.get(0).getContent()));
            }

            String contextBlock = searchResults.isEmpty()
                    ? "- (관련 컨텍스트를 찾지 못했습니다. 일반 지식으로만 답변하세요.)"
                    : searchResults.stream()
                    .map(r -> String.format("- %s (점수: %.3f)",
                            llmRagUtil.safeSnippet(r.getContent()), r.getScore()))
                    .collect(Collectors.joining("\n"));

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

                        String systemPrompt = LlmConstants.SYSTEM_PROMPT;
                        String userPrompt = "QUESTION:\n" + llmQuery + "\n\nCONTEXT:\n" + contextBlock + conversationContext;

                        // 디버그 로그
                        System.out.println("[ChatImplService] Search results count: " + searchResults.size());
                        if (!searchResults.isEmpty()) {
                            System.out.println("[ChatImplService] Average score: " + 
                                searchResults.stream().mapToDouble(SearchResult::getScore).average().orElse(0.0));
                            System.out.println("[ChatImplService] Top result score: " + 
                                searchResults.get(0).getScore());
                        }
                        System.out.println("[ChatImplService] Context block length: " + contextBlock.length());
                        System.out.println("[ChatImplService] User prompt preview: " + 
                            (userPrompt.length() > 500 ? userPrompt.substring(0, 500) + "..." : userPrompt));

                        String ctxHash = hash.sha256(ctxVersion, systemPrompt, userPrompt);

                        // 1) 프롬프트 캐시 (기존 메서드 그대로 사용)
                        Mono<String> promptMono = cache.getOrBuildPrompt(
                                partitionId,                          // ← 항상 "global"
                                ctxHash,
                                () -> Mono.just(toPromptJson(systemPrompt, userPrompt))
                        );

                        // 2) 응답 캐시 + 락 (기존 메서드 그대로 사용)
                        String inputHash = hash.sha256(llmModel, llmProvider, ctxVersion, systemPrompt, userPrompt);
                        Mono<String> answerMono = cache.getOrInvoke(
                                llmModel,
                                inputHash,
                                () -> llmContextClient.chat(llmProvider, llmModel, systemPrompt, userPrompt)
                        );

                        return promptMono.then(answerMono)
                                .flatMap(answer -> {
                                    // 대화 히스토리에 저장 (질문과 답변을 JSON 형태로)
                                    String questionJson = String.format("{\"role\":\"user\",\"content\":\"%s\",\"timestamp\":\"%s\"}", 
                                            llmQuery.replace("\"", "\\\""), Instant.now());
                                    String answerJson = String.format("{\"role\":\"assistant\",\"content\":\"%s\",\"timestamp\":\"%s\"}", 
                                            answer.replace("\"", "\\\""), Instant.now());
                                    
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
                                                            "averageScore", searchResults.stream().mapToDouble(SearchResult::getScore).average().orElse(0.0),
                                                            "timestamp", Instant.now(),
                                                            "provider", llmProvider
                                                    ),
                                                    sessionId
                                            ));
                                });
                    });
        });
    }

    private String toPromptJson(String systemPrompt, String userPrompt) {
        return "{\"system\":" + quote(systemPrompt) + ",\"user\":" + quote(userPrompt) + "}";
    }

    private String quote(String s) { return "\"" + s.replace("\"","\\\"") + "\""; }

}
