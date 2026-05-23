package io.github.eschoe.llmragapi.rag.prompt;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import io.github.eschoe.llmragapi.search.SearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 검색 결과를 LLM 프롬프트에 넣기 전에 토큰 예산 내로 잘라낸다.
 * jtokkit 의 {@code o200k_base} 인코더 사용 (gpt-4o, gpt-4o-mini 와 동일).
 * cl100k 보다 한국어 토큰 효율이 살짝 좋다 — gpt-4o 환경 기준.
 */
@Component
public class ContextBudget {

    /** 토크나이저 호출 비용을 줄이기 위한 fallback 휴리스틱. */
    private static final int CHARS_PER_TOKEN_FALLBACK = 4;

    private final Encoding encoding;

    public ContextBudget() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        // gpt-4o 계열 인코더. 없으면 cl100k_base 로 폴백.
        Encoding e;
        try {
            e = registry.getEncoding(EncodingType.O200K_BASE);
        } catch (Throwable t) {
            e = registry.getEncoding(EncodingType.CL100K_BASE);
        }
        this.encoding = e;
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            return encoding.countTokens(text);
        } catch (Throwable t) {
            // 토크나이저 실패 시 보수적 휴리스틱으로 폴백
            return Math.max(1, text.length() / CHARS_PER_TOKEN_FALLBACK);
        }
    }

    /**
     * maxTokens 안에 들어가도록 candidates 를 위에서부터 채워 넣는다.
     * 그리디 전략 — 첫 번째 청크가 budget 초과면 그 자리에서 멈춘다.
     * (나중 청크가 더 작아도 시도하지 않음. score 순서 보존이 더 중요.)
     * 이유: rerank 로 score desc 정렬된 상태라 위쪽이 더 중요한 청크.
     * 큰 청크 하나 버리고 작은 거 여러 개 쑤셔 넣으면 답변 품질이 떨어짐.
     * Edge case: 첫 청크 자체가 예산을 초과하면 빈 결과 대신 truncate 해서라도 1개는 보존.
     * 빈 결과 → "관련 컨텍스트를 찾지 못했습니다" 메시지로 떨어져 사용자가 정상 답변을 못 받음.
     * 일부라도 있는 게 무 반환보다 답변 품질에 도움.
     */
    public List<SearchResult> fit(List<SearchResult> candidates, int maxTokens) {
        if (candidates == null || candidates.isEmpty() || maxTokens <= 0) {
            return List.of();
        }
        List<SearchResult> kept = new ArrayList<>();
        int used = 0;
        for (SearchResult r : candidates) {
            int cost = estimateTokens(r.getContent());
            if (used + cost > maxTokens) {
                // 첫 청크부터 초과 → truncate 해서라도 포함 (품질 보호)
                if (kept.isEmpty()) {
                    String truncated = truncate(r.getContent(), maxTokens);
                    SearchResult fallback = new SearchResult(
                            r.getId(), truncated, r.getScore(), r.getMetadata(), r.getSource());
                    kept.add(fallback);
                }
                break;
            }
            kept.add(r);
            used += cost;
        }
        return kept;
    }

    /**
     * 대화 이력처럼 "최신부터 우선" 채우는 텍스트 리스트용.
     */
    public List<String> fitStringsLatestFirst(List<String> messages, int maxTokens) {
        if (messages == null || messages.isEmpty() || maxTokens <= 0) return List.of();
        List<String> kept = new ArrayList<>();
        int used = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            String m = messages.get(i);
            int cost = estimateTokens(m);
            if (used + cost > maxTokens) break;
            kept.addFirst(m);
            used += cost;
        }
        return kept;
    }

    /**
     * 단일 텍스트를 maxTokens 길이에 맞게 자른다.
     * jtokkit 으로 정확한 토큰 자르기 수행 후 문장/단어 경계 보정.
     */
    public String truncate(String text, int maxTokens) {
        if (text == null) return "";
        if (estimateTokens(text) <= maxTokens) return text;

        // jtokkit 으로 정확한 토큰 자르기.
        String truncated;
        try {
            IntArrayList ids = encoding.encode(text);
            int take = Math.min(maxTokens, ids.size());
            IntArrayList sub = new IntArrayList();
            for (int i = 0; i < take; i++) sub.add(ids.get(i));
            truncated = encoding.decode(sub);
        } catch (Throwable t) {
            // 실패 시 char 기반 폴백
            int maxChars = maxTokens * CHARS_PER_TOKEN_FALLBACK;
            truncated = text.length() > maxChars ? text.substring(0, maxChars) : text;
        }

        // 문장 경계 다듬기
        int sentenceEnd = lastIndexOfAny(truncated, ". ", "! ", "? ", "다. ", "까? ", "요. ");
        if (sentenceEnd > truncated.length() / 2) {
            return truncated.substring(0, sentenceEnd + 1).trim() + " …";
        }
        int wordEnd = truncated.lastIndexOf(' ');
        if (wordEnd > truncated.length() / 2) {
            return truncated.substring(0, wordEnd).trim() + " …";
        }
        return truncated.trim() + " …";
    }

    /**
     * 검색 결과 콘텐츠를 프롬프트에 포함하기 좋은 길이로 잘라낸다.
     */
    public String snippet(String text) {
        return truncate(text == null ? "" : text.replace('\n', ' ').trim(), 100);
    }

    private int lastIndexOfAny(String s, String... needles) {
        int best = -1;
        for (String n : needles) {
            int idx = s.lastIndexOf(n);
            if (idx > best) best = idx;
        }
        return best;
    }
}
