package io.github.eschoe.llmragapi.rag.chunking;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 단락 → 문장 → 글자 순으로 폴백하며 자른다.
 *
 * 1. 텍스트를 빈 줄(`\n\n+`)로 단락 분리
 * 2. 단락이 chunkSize 이하면 한 청크로 사용
 * 3. 단락이 너무 크면 문장 단위로 다시 분리해 그리디로 채움 (한국어/영어 종결 표현 모두 인식)
 * 4. 단일 문장조차 너무 길면 마지막 안전망으로 글자 수 단위 슬라이스
 *
 * overlap은 이전 청크의 **마지막 문장**을 다음 청크 앞에 붙이는 식으로 적용한다.
 * 문장 경계가 없으면 글자 수 기준으로 fall back.
 */
@Component
public class ParagraphAwareChunker implements Chunker {

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n{2,}");
    private static final Pattern SENTENCE_BREAK = Pattern.compile(
            "(?<=[.!?。!?])\\s+|(?<=다\\.)\\s+|(?<=까\\?)\\s+|(?<=요\\.)\\s+");

    /**
     * 입력 텍스트를 chunkSize(글자) 단위로 분할.
     *
     * 단락 → 문장 → 글자 순으로 폴백:
     *   1. 빈 줄(`\n\n+`)로 단락 분리. 단락이 chunkSize 이하면 그대로 하나의 청크.
     *   2. 단락이 너무 크면 문장 단위로 다시 분리해 그리디 누적.
     *      한국어 종결 표현 인식: "다.", "까?", "요." (영어 마침표/?/! 와 함께)
     *   3. 단일 문장조차 너무 길면 마지막 안전망으로 글자 슬라이스.
     *
     * overlap 처리:
     *   - 청크 flush 직전 마지막 문장(또는 글자 폴백)을 다음 청크 앞에 prepend
     *   - 문장 경계 우선해서 잘라야 임베딩이 의미 단위 보존
     *   - overlap >= chunkSize 면 chunkSize/4 로 강제 (무한 루프 방지)
     *
     * 왜 단락 우선 파싱:
     *   - 단락 경계는 의미 경계와 강한 상관 — 문장 한 가운데서 자르는 것보다 응집력 ↑
     *   - 한 단락이 통째로 들어가면 LLM 답변 정확도 ↑
     */
    @Override
    public List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;
        if (chunkSize <= 0) chunkSize = 1000;
        if (overlap < 0) overlap = 0;
        if (overlap >= chunkSize) overlap = chunkSize / 4;

        StringBuilder current = new StringBuilder();
        for (String paragraph : PARAGRAPH_BREAK.split(text)) {
            String p = paragraph.trim();
            if (p.isEmpty()) continue;

            if (p.length() > chunkSize) {
                flushCurrent(chunks, current, overlap);
                addLargeParagraph(chunks, current, p, chunkSize, overlap);
                continue;
            }

            if (current.length() + p.length() + 2 > chunkSize) {
                flushCurrent(chunks, current, overlap);
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(p);
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private void addLargeParagraph(List<String> chunks, StringBuilder current,
                                   String paragraph, int chunkSize, int overlap) {
        List<String> sentences = Arrays.asList(SENTENCE_BREAK.split(paragraph));
        for (String sentence : sentences) {
            String s = sentence.trim();
            if (s.isEmpty()) continue;

            if (s.length() > chunkSize) {
                flushCurrent(chunks, current, overlap);
                for (int i = 0; i < s.length(); i += chunkSize) {
                    chunks.add(s.substring(i, Math.min(i + chunkSize, s.length())));
                }
                continue;
            }

            if (current.length() + s.length() + 1 > chunkSize) {
                flushCurrent(chunks, current, overlap);
            }
            if (current.length() > 0) current.append(" ");
            current.append(s);
        }
    }

    private void flushCurrent(List<String> chunks, StringBuilder current, int overlap) {
        if (current.isEmpty()) return;
        String chunk = current.toString().trim();
        chunks.add(chunk);

        String tail = takeTail(chunk, overlap);
        current.setLength(0);
        if (!tail.isEmpty()) current.append(tail);
    }

    private String takeTail(String chunk, int overlap) {
        if (overlap == 0 || chunk.length() <= overlap) return "";
        int from = chunk.length() - overlap;
        // 가능한 한 문장 경계에서 자르기
        int sentenceStart = chunk.lastIndexOf(". ", from);
        if (sentenceStart > from - overlap / 2) from = sentenceStart + 2;
        return chunk.substring(Math.min(from, chunk.length())).trim();
    }
}
