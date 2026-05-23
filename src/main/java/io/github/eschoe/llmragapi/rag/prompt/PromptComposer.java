package io.github.eschoe.llmragapi.rag.prompt;

import io.github.eschoe.llmragapi.chat.history.ChatHistoryStore;
import io.github.eschoe.llmragapi.llm.cache.LlmConstants;
import io.github.eschoe.llmragapi.search.SearchResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 프롬프트 빌드 — 단일 책임.
 *
 * 입력: 사용자 query, 검색 결과, sessionId(history 조회용), customPrompt(옵션), 이력 토큰 예산
 * 출력: (systemPrompt, userPrompt) — LLM 호출에 그대로 사용
 *
 * 책임:
 *   - CONTEXT 블록 빌드 ([근거 N] 형식, snippet truncate)
 *   - PREVIOUS_CONVERSATION 블록 빌드 (token budget 내 latest-first fit)
 *   - customPrompt 분기 (있으면 SYSTEM_PROMPT 대체)
 *   - history 조회 timeout (5초) + onErrorReturn empty (RAG 자체는 끊기지 않게)
 *
 * ChatService 에서 분리한 이유: chatEnhanced/chatStream 두 곳에서 동일 빌드 흐름 중복.
 * customPrompt 분기·CONTEXT 형식 변경 시 여기 한 곳만 수정하면 됨.
 */
@Component
public class PromptComposer {

    private final ChatHistoryStore chatHistoryStore;
    private final ContextBudget contextBudget;

    public PromptComposer(ChatHistoryStore chatHistoryStore, ContextBudget contextBudget) {
        this.chatHistoryStore = chatHistoryStore;
        this.contextBudget = contextBudget;
    }

    public record Composed(String systemPrompt, String userPrompt, boolean retrievalEmpty) {}

    /**
     * 검색 결과 + 이력을 합쳐 system/user prompt 생성.
     * history 조회는 5초 timeout — 실패 시 빈 이력으로 진행 (fail-open).
     */
    public Mono<Composed> compose(String query,
                                  List<SearchResult> searchResults,
                                  String sessionId,
                                  String customPrompt,
                                  int historyTokenBudget) {
        boolean retrievalEmpty = searchResults.isEmpty();
        String contextBlock = buildContextBlock(searchResults);
        String systemPrompt = (customPrompt != null && !customPrompt.isBlank())
                ? customPrompt
                : LlmConstants.SYSTEM_PROMPT;

        return chatHistoryStore.recent(sessionId, 10)
                .collectList()
                .timeout(Duration.ofSeconds(5))
                .onErrorReturn(List.of())
                .map(rawHistory -> contextBudget.fitStringsLatestFirst(rawHistory, historyTokenBudget))
                .map(historyMessages -> {
                    String conversationContext = historyMessages.isEmpty()
                            ? ""
                            : "\n\nPREVIOUS CONVERSATION:\n" + String.join("\n", historyMessages);
                    String userPrompt = "QUESTION:\n" + query
                            + "\n\nCONTEXT:\n" + contextBlock
                            + conversationContext;
                    return new Composed(systemPrompt, userPrompt, retrievalEmpty);
                });
    }

    private String buildContextBlock(List<SearchResult> searchResults) {
        if (searchResults.isEmpty()) {
            return "(관련 컨텍스트를 찾지 못했습니다.)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult r = searchResults.get(i);
            sb.append(String.format("[근거 %d] (점수: %.3f) %s%n",
                    i + 1, r.getScore(), contextBudget.snippet(r.getContent())));
        }
        return sb.toString().trim();
    }
}
