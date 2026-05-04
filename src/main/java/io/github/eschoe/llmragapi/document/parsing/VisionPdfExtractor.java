package io.github.eschoe.llmragapi.document.parsing;

import io.github.eschoe.llmragapi.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * 이미지 only PDF → PNG 페이지 → GPT-4o vision 호출 → 페이지별 텍스트 합성.
 *
 * 비용: gpt-4o high detail 페이지당 약 $0.005~0.008
 *
 * 분리된 책임:
 *  - {@link PdfPageRenderer} : PDF→PNG
 *  - {@link VisionUsageTracker} : 일일 한도 enforcement
 *  - {@link LlmClient}#extractTextFromImage : OpenAI vision 호출
 */
@Component
public class VisionPdfExtractor {

    private static final Logger log = LoggerFactory.getLogger(VisionPdfExtractor.class);

    private final LlmClient llmClient;
    private final VisionUsageTracker usageTracker;
    private final PdfPageRenderer pageRenderer;

    @Value("${app.parsing.pdf.vision-model:gpt-4o}")
    private String visionModel;

    @Value("${app.parsing.pdf.vision-dpi:150}")
    private int renderDpi;

    @Value("${app.parsing.pdf.vision-max-pages:50}")
    private int maxPages;

    @Value("${app.parsing.pdf.vision-concurrency:3}")
    private int concurrency;

    /** 페이지당 vision API 호출 timeout (초). 행을 막아 전체 업로드가 무한 대기되는 것 방지. */
    @Value("${app.parsing.pdf.vision-page-timeout-sec:60}")
    private int pageTimeoutSec;

    public VisionPdfExtractor(LlmClient llmClient,
                              VisionUsageTracker usageTracker,
                              PdfPageRenderer pageRenderer) {
        this.llmClient = llmClient;
        this.usageTracker = usageTracker;
        this.pageRenderer = pageRenderer;
    }

    public Mono<String> extract(byte[] pdfBytes, String sessionId) {
        return Mono.fromCallable(() -> pageRenderer.renderToPng(pdfBytes, renderDpi, maxPages))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(pages -> usageTracker.reserve(sessionId, pages.size()).thenReturn(pages))
                .flatMap(pages -> Flux.fromIterable(pages)
                        .flatMap(page -> llmClient.extractTextFromImage(page.png(), "image/png", visionModel)
                                        .timeout(Duration.ofSeconds(pageTimeoutSec))
                                        .map(text -> formatPage(page.index(), text))
                                        .onErrorResume(e -> {
                                            log.warn("[vision] page {} failed: {}", page.index() + 1, e.getMessage());
                                            return Mono.just(formatPage(page.index(),
                                                    "(이 페이지 추출 실패: " + e.getMessage() + ")"));
                                        }),
                                concurrency)
                        .sort((a, b) -> Integer.compare(extractPageNumber(a), extractPageNumber(b)))
                        .collect(StringBuilder::new, (sb, s) -> sb.append(s).append("\n\n"))
                        .map(StringBuilder::toString)
                        .map(String::trim));
    }

    private String formatPage(int index, String text) {
        return "[Page " + (index + 1) + "]\n" + (text != null ? text.trim() : "");
    }

    private int extractPageNumber(String formatted) {
        int start = formatted.indexOf("[Page ");
        int end = formatted.indexOf("]", start);
        if (start < 0 || end < 0) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(formatted.substring(start + 6, end).trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    public int getMaxPages() { return maxPages; }
    public String getVisionModel() { return visionModel; }
}
