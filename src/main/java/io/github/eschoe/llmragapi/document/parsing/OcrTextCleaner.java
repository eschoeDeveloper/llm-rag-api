package io.github.eschoe.llmragapi.document.parsing;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * OCR 결과의 흔한 노이즈 제거.
 *
 * 1. 한글 음절 사이 공백 제거: "칼 로 리" → "칼로리"
 * 2. 매 페이지 반복되는 짧은 라인(슬라이드 푸터) 제거 — 3회 이상 반복 + 60자 이하
 * 3. 잡음 라인 제거 — 알파벳/한글/숫자 비율 30% 미만의 짧은 줄
 * 4. 연속 빈 줄 축약
 */
@Component
public class OcrTextCleaner {

    public String clean(String text) {
        if (text == null || text.isEmpty()) return "";

        // 1) 한글 음절 사이 공백 제거 (두 번 적용)
        String s = text.replaceAll("([\\p{IsHangul}]) (?=[\\p{IsHangul}])", "$1");
        s = s.replaceAll("([\\p{IsHangul}]) (?=[\\p{IsHangul}])", "$1");

        // 2,3) 라인별 처리
        String[] lines = s.split("\\R");
        Map<String, Integer> lineCount = new HashMap<>();
        for (String line : lines) {
            String key = line.trim();
            if (!key.isEmpty() && key.length() <= 60) {
                lineCount.merge(key, 1, Integer::sum);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append('\n');
                continue;
            }
            // 반복 푸터/헤더
            if (lineCount.getOrDefault(trimmed, 0) >= 3 && trimmed.length() <= 60) continue;
            // 잡음 라인
            if (trimmed.length() <= 30 && meaningfulRatio(trimmed) < 0.3) continue;
            sb.append(trimmed).append('\n');
        }

        // 4) 연속 빈 줄 축약
        return sb.toString().replaceAll("(\\R){3,}", "\n\n").trim();
    }

    private double meaningfulRatio(String s) {
        if (s.isEmpty()) return 0.0;
        long meaningful = s.chars()
                .filter(c -> Character.isLetterOrDigit(c)
                        || (c >= 0xAC00 && c <= 0xD7A3))   // 한글 완성형
                .count();
        return (double) meaningful / s.length();
    }
}
