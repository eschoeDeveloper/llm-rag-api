package io.github.eschoe.llmragapi.document.parsing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 문서 텍스트 추출 결과.
 *  - text: 후처리까지 거친 최종 텍스트
 *  - mode: 추출 경로 (text / ocr / mixed / unknown)
 *  - warnings: 사용자에게 보일 경고 (예: "image-only PDF — PPTX 권장")
 */
public record ExtractionResult(String text, String mode, List<String> warnings) {

    public static ExtractionResult of(String text, String mode) {
        return new ExtractionResult(text, mode, Collections.emptyList());
    }

    public ExtractionResult withWarning(String warning) {
        if (warning == null || warning.isBlank()) return this;
        List<String> next = new ArrayList<>(warnings);
        next.add(warning);
        return new ExtractionResult(text, mode, next);
    }
}
