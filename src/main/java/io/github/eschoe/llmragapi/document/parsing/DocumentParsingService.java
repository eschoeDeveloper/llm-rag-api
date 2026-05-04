package io.github.eschoe.llmragapi.document.parsing;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 문서 → 텍스트 추출 오케스트레이터.
 *
 * 라우팅 결정만 담당:
 *  - PDF + image-only + visionFallback 활성 → {@link VisionPdfExtractor}
 *  - 그 외 → Tika {@link AutoDetectParser}
 *
 * 내부 책임은 다음으로 분리:
 *  - {@link PdfTextProbe}        — embedded text 양 측정
 *  - {@link OcrTextCleaner}      — OCR 결과 정제
 *  - {@link TikaContextFactory}  — Tika ParseContext 빌더
 *  - {@link TesseractDiagnostics}— 시작 시 진단 (별도 컴포넌트)
 */
@Service
public class DocumentParsingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParsingService.class);

    private final Tika tika;
    private final AutoDetectParser autoParser;
    private final PdfTextProbe pdfTextProbe;
    private final OcrTextCleaner ocrCleaner;
    private final TikaContextFactory tikaContextFactory;
    private final VisionPdfExtractor visionExtractor;

    @Value("${app.parsing.pdf.image-only-threshold-chars-per-page:50}")
    private int imageOnlyThresholdCharsPerPage;

    @Value("${app.parsing.pdf.vision-fallback:false}")
    private boolean visionFallback;

    public DocumentParsingService(PdfTextProbe pdfTextProbe,
                                  OcrTextCleaner ocrCleaner,
                                  TikaContextFactory tikaContextFactory,
                                  VisionPdfExtractor visionExtractor) {
        this.tika = new Tika();
        this.autoParser = new AutoDetectParser();
        this.pdfTextProbe = pdfTextProbe;
        this.ocrCleaner = ocrCleaner;
        this.tikaContextFactory = tikaContextFactory;
        this.visionExtractor = visionExtractor;
    }

    /**
     * 입력 파일 → 텍스트 추출. 라우팅 결정만 담당.
     *
     * 라우팅 우선순위:
     *   1. visionFallback 활성 + PDF + 페이지당 글자 수 < threshold → Vision (GPT-4o)
     *      (image-only PDF — Tika OCR 보다 멀티모달 LLM 이 한국어/표 인식 정확)
     *   2. 그 외 모두 → Tika AutoDetectParser
     *      (텍스트 PDF, PPTX, DOCX, XLSX, TXT, MD, HTML 등)
     *
     * Vision 경로는 별도 Mono — Tika 호출 안 함 (이미 Vision 으로 충분).
     * Tika 경로는 내부에서 다시 OCR strategy 에 따라 Tesseract OCR 가능.
     */
    public Mono<ExtractionResult> extractText(byte[] fileContent, String fileName, String sessionId) {
        // PDF + image-only + visionFallback 활성 → vision 경로
        if (visionFallback) {
            String mime = detectMime(fileContent, fileName);
            if (mime != null && mime.contains("pdf")) {
                PdfTextProbe.Result probe = pdfTextProbe.probe(fileContent);
                if (probe.usable() && probe.charsPerPage() < imageOnlyThresholdCharsPerPage) {
                    log.info("[parse] image-only PDF detected ({} pages, {} chars/page avg) — using GPT-4o vision",
                            probe.pages(), String.format("%.1f", probe.charsPerPage()));
                    return runVisionExtraction(fileContent, probe, sessionId);
                }
            }
        }
        // 그 외 모든 경로 — Tika
        return Mono.fromCallable(() -> doTikaExtract(fileContent, fileName));
    }

    private Mono<ExtractionResult> runVisionExtraction(byte[] fileContent,
                                                        PdfTextProbe.Result probe,
                                                        String sessionId) {
        int pagesToProcess = Math.min(probe.pages(), visionExtractor.getMaxPages());
        double estimatedCost = pagesToProcess * 0.005;
        List<String> warnings = new ArrayList<>();
        warnings.add(String.format(
                "GPT-4o vision으로 추출했습니다 (%d페이지, 모델 %s, 예상 비용 약 $%.2f). " +
                "Tesseract OCR 대비 정확도 ↑, 비용 발생 ↑.",
                pagesToProcess, visionExtractor.getVisionModel(), estimatedCost));
        if (probe.pages() > visionExtractor.getMaxPages()) {
            warnings.add(String.format(
                    "PDF 총 %d페이지 중 %d페이지만 처리 (설정된 max-pages 한도). 나머지는 누락됨.",
                    probe.pages(), pagesToProcess));
        }
        return visionExtractor.extract(fileContent, sessionId)
                .map(text -> {
                    if (text == null || text.isBlank()) {
                        throw new RuntimeException("Vision 추출 결과가 비어 있습니다.");
                    }
                    return new ExtractionResult(text, "vision", warnings);
                });
    }

    private ExtractionResult doTikaExtract(byte[] fileContent, String fileName) {
        String mimeType = detectMime(fileContent, fileName);
        if (mimeType == null) mimeType = "application/octet-stream";
        log.info("[parse] file={} size={}B mime={}", fileName, fileContent.length, mimeType);

        List<String> warnings = new ArrayList<>();
        String mode = "text";

        // PDF probe로 image-only 판정 시 사용자에 안내 (NO_OCR 환경에서)
        if (mimeType.contains("pdf")) {
            PdfTextProbe.Result probe = pdfTextProbe.probe(fileContent);
            if (probe.usable()) {
                log.debug("[probe] PDF: {} pages, {} chars total ({} chars/page avg)",
                        probe.pages(), probe.totalChars(), String.format("%.1f", probe.charsPerPage()));
                if (probe.charsPerPage() < imageOnlyThresholdCharsPerPage) {
                    PDFParserConfig.OCR_STRATEGY strategy = tikaContextFactory.currentStrategy();
                    if (strategy == PDFParserConfig.OCR_STRATEGY.NO_OCR) {
                        throw new RuntimeException(String.format(
                                "이미지 only PDF로 판정되었습니다 (%d페이지, 평균 %.0f자/페이지). " +
                                "PPTX 원본 업로드를 권장합니다. " +
                                "PDF로 진행하려면 Tesseract 설치 후 PDF_OCR_STRATEGY=AUTO 설정이 필요합니다.",
                                probe.pages(), probe.charsPerPage()));
                    }
                    log.debug("[probe] image-only PDF — OCR fallback will run (strategy={})", strategy);
                    warnings.add(String.format(
                            "이미지 only PDF로 감지되어 OCR로 처리합니다 (%d페이지, 평균 %.0f자/페이지). " +
                            "한국어 슬라이드는 OCR 인식률이 낮을 수 있어 PPTX 원본을 권장합니다.",
                            probe.pages(), probe.charsPerPage()));
                    mode = "ocr";
                }
            }
        }

        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        ParseContext context = tikaContextFactory.build(autoParser);

        try {
            autoParser.parse(new ByteArrayInputStream(fileContent), handler, metadata, context);
            String raw = handler.toString().trim();
            String text = ocrCleaner.clean(raw);
            log.info("[parse] extracted {} chars from {} (raw {} chars before OCR cleanup)",
                    text.length(), fileName, raw.length());
            if (text.isEmpty()) {
                String hint = mimeType.contains("pdf")
                        ? " (이미지 only PDF/스캔본일 가능성. Tesseract 설치 후 app.parsing.pdf.ocr-strategy=AUTO 로 변경하면 OCR 추출됨)"
                        : "";
                throw new RuntimeException("추출된 텍스트가 없습니다." + hint);
            }
            return new ExtractionResult(text, mode, warnings);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[parse] failed for {} (mime={}): {}", fileName, mimeType, e.getMessage(), e);
            throw new RuntimeException("문서 파싱 실패: " + e.getMessage() +
                    " (파일이 손상되었거나 지원하지 않는 형식일 수 있습니다)", e);
        }
    }

    public Mono<String> detectMimeType(byte[] fileContent, String fileName) {
        return Mono.fromCallable(() -> {
            String mime = detectMime(fileContent, fileName);
            if (mime == null) throw new RuntimeException("MIME 타입 감지 실패");
            return mime;
        });
    }

    public Mono<DocumentMetadata> extractMetadata(byte[] fileContent, String fileName) {
        return Mono.fromCallable(() -> {
            try {
                Metadata metadata = new Metadata();
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
                tika.parse(new ByteArrayInputStream(fileContent), metadata);
                return new DocumentMetadata(
                        metadata.get(TikaCoreProperties.TITLE),
                        metadata.get(TikaCoreProperties.CREATOR),
                        metadata.get(TikaCoreProperties.CREATED),
                        metadata.get(Metadata.CONTENT_TYPE),
                        metadata.get(Metadata.CONTENT_LENGTH)
                );
            } catch (Exception e) {
                return new DocumentMetadata(fileName, null, null, null, null);
            }
        });
    }

    private String detectMime(byte[] fileContent, String fileName) {
        try {
            return tika.detect(new ByteArrayInputStream(fileContent), fileName);
        } catch (IOException e) {
            log.warn("MIME detection failed for {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    public static class DocumentMetadata {
        private String title;
        private String creator;
        private String creationDate;
        private String contentType;
        private String contentLength;

        public DocumentMetadata() {}

        public DocumentMetadata(String title, String creator, String creationDate, String contentType, String contentLength) {
            this.title = title;
            this.creator = creator;
            this.creationDate = creationDate;
            this.contentType = contentType;
            this.contentLength = contentLength;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getCreator() { return creator; }
        public void setCreator(String creator) { this.creator = creator; }

        public String getCreationDate() { return creationDate; }
        public void setCreationDate(String creationDate) { this.creationDate = creationDate; }

        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }

        public String getContentLength() { return contentLength; }
        public void setContentLength(String contentLength) { this.contentLength = contentLength; }
    }
}
