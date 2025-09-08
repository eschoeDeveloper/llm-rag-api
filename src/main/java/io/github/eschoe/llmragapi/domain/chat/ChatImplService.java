package io.github.eschoe.llmragapi.domain.chat;

import io.github.eschoe.llmragapi.client.LlmContextClient;
import io.github.eschoe.llmragapi.dao.EmbeddingQueryDao;
import io.github.eschoe.llmragapi.domain.llm.LlmConstants;
import io.github.eschoe.llmragapi.domain.search.SearchResult;
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

    private final LlmRagUtil llmRagUtil;
    private final LlmContextClient llmContextClient;
    private final EmbeddingQueryDao embeddingQueryDao;

    public ChatImplService(LlmRagUtil llmRagUtil, LlmContextClient llmContextClient, EmbeddingQueryDao embeddingQueryDao) {
        this.llmRagUtil = llmRagUtil;
        this.llmContextClient = llmContextClient;
        this.embeddingQueryDao = embeddingQueryDao;
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
    public Mono<ChatResponse> chatEnhanced(ChatRequest request) {
        Instant startTime = Instant.now();

        String llmQuery = llmRagUtil.opt(request.getQuery());
        String llmProvider = LlmConstants.DEFAULT_PROVIDER;
        String llmModel = llmRagUtil.chooseModel(llmProvider, null);

        if (llmQuery.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));

        // 검색 결과가 있으면 활용, 없으면 새로 검색
        Mono<List<SearchResult>> searchResultsMono;
        if (request.getSearchResults() != null && !request.getSearchResults().isEmpty()) {
            searchResultsMono = Mono.just(request.getSearchResults());
        } else {
            // 벡터 검색 수행
            int k = request.getConfig() != null ? request.getConfig().getTopK() : 5;
            double threshold = request.getConfig() != null ? request.getConfig().getThreshold() : 0.7;

            searchResultsMono = llmContextClient.embed(embeddingModel, llmQuery)
                    .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, k))
                    .collectList()
                    .map(embedRows -> embedRows.stream()
                            .filter(row -> row.getScore() != null && row.getScore() >= threshold) // 임계값 필터링
                            .map(row -> new SearchResult(
                                    String.valueOf(row.getId()),
                                    row.getContent(),
                                    row.getScore() != null ? row.getScore() : 0.0,
                                    Map.of(
                                            "title", row.getTitle() != null ? row.getTitle() : "",
                                            "createdAt", row.getCreatedAt() != null ? row.getCreatedAt().toString() : ""
                                    ),
                                    "database"
                            ))
                            .collect(Collectors.toList()));
        }

        return searchResultsMono
                .flatMap(searchResults -> {
                    // 컨텍스트 구성 (점수 정보 포함)
                    String _context = searchResults.stream()
                            .map(result -> String.format("- %s (점수: %.3f)",
                                    llmRagUtil.safeSnippet(result.getContent()),
                                    result.getScore()))
                            .collect(Collectors.joining("\n"));

                    String systemPrompt = LlmConstants.SYSTEM_PROMPT;
                    String userPrompt = "QUESTION:\n" + llmQuery + "\n\nCONTEXT:\n" + _context;

                    return llmContextClient.chat(llmProvider, llmModel, systemPrompt, userPrompt)
                            .map(response -> {
                                Instant endTime = Instant.now();
                                Duration processingTime = Duration.between(startTime, endTime);

                                return new ChatResponse(
                                        response,
                                        llmModel,
                                        0, // LlmContextClient에서 토큰 정보를 제공하지 않으면 0
                                        Map.of(
                                                "processingTime", processingTime.toMillis(),
                                                "config", request.getConfig(),
                                                "searchResults", searchResults.size(),
                                                "averageScore", searchResults.stream()
                                                        .mapToDouble(SearchResult::getScore)
                                                        .average()
                                                        .orElse(0.0),
                                                "timestamp", endTime,
                                                "provider", llmProvider
                                        )
                                );
                            });
                });
    }
}
