package io.github.eschoe.llmragapi.document.parsing;

import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 매 추출마다 새 ParseContext 를 만들어 Tika autoParser 에 전달.
 * (Tika ParseContext 는 thread-safe 하지 않아 매번 새로 만들어야 한다)
 *
 * 모든 PDF/OCR 관련 Tika 설정을 한 곳에서 관리.
 */
@Component
public class TikaContextFactory {

    private static final Logger log = LoggerFactory.getLogger(TikaContextFactory.class);

    @Value("${app.tesseract.language:kor+eng}")
    private String tesseractLanguage;

    @Value("${app.parsing.pdf.ocr-strategy:NO_OCR}")
    private String pdfOcrStrategy;

    @Value("${app.parsing.pdf.ocr-dpi:200}")
    private int ocrDpi;

    @Value("${app.parsing.pdf.ocr-psm:3}")
    private int ocrPsm;

    public ParseContext build(AutoDetectParser autoParser) {
        ParseContext context = new ParseContext();

        TesseractOCRConfig ocrConfig = new TesseractOCRConfig();
        ocrConfig.setLanguage(tesseractLanguage);
        ocrConfig.setPageSegMode(String.valueOf(ocrPsm));
        // tesseract 실행파일 경로/tessdata 는 시스템 환경변수 (TESSDATA_PREFIX, PATH) 로 제어.
        context.set(TesseractOCRConfig.class, ocrConfig);

        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setOcrStrategy(resolveOcrStrategy(pdfOcrStrategy));
        boolean ocrEnabled = pdfConfig.getOcrStrategy() != PDFParserConfig.OCR_STRATEGY.NO_OCR;
        pdfConfig.setExtractInlineImages(ocrEnabled);
        pdfConfig.setOcrDPI(ocrDpi);
        context.set(PDFParserConfig.class, pdfConfig);

        // AutoDetectParser 가 내부에서 child parser 들 호출할 때 같은 context 재사용
        context.set(AutoDetectParser.class, autoParser);

        return context;
    }

    public PDFParserConfig.OCR_STRATEGY resolveOcrStrategy(String value) {
        if (value == null || value.isBlank()) return PDFParserConfig.OCR_STRATEGY.NO_OCR;
        try {
            return PDFParserConfig.OCR_STRATEGY.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown app.parsing.pdf.ocr-strategy='{}', falling back to NO_OCR", value);
            return PDFParserConfig.OCR_STRATEGY.NO_OCR;
        }
    }

    public PDFParserConfig.OCR_STRATEGY currentStrategy() {
        return resolveOcrStrategy(pdfOcrStrategy);
    }
}
