package io.github.eschoe.llmragapi.document.parsing;

import jakarta.annotation.PostConstruct;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 시작 시 한 번 — 시스템에 tesseract 가 설치되어 있는지, 한국어 학습 데이터가 있는지 확인.
 * OCR 전략이 NO_OCR 이면 검사 생략.
 *
 * `[parser-init]` 로그 prefix 로 시작 시점에 잘 보이게.
 */
@Component
public class TesseractDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(TesseractDiagnostics.class);

    @Value("${app.parsing.pdf.ocr-strategy:NO_OCR}")
    private String pdfOcrStrategy;

    @Value("${app.tesseract.language:kor+eng}")
    private String tesseractLanguage;

    @PostConstruct
    void diagnose() {
        log.info("[parser-init] PDF OCR strategy: {}", pdfOcrStrategy);
        log.info("[parser-init] Tesseract language: {}", tesseractLanguage);
        log.info("[parser-init] TESSDATA_PREFIX env: {}", System.getenv("TESSDATA_PREFIX"));

        if (resolveStrategy(pdfOcrStrategy) == PDFParserConfig.OCR_STRATEGY.NO_OCR) {
            log.info("[parser-init] OCR is OFF — image-only PDFs will return empty text");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("tesseract", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                log.warn("[parser-init] tesseract --version timed out");
                return;
            }
            String out = new String(p.getInputStream().readAllBytes()).trim();
            int exit = p.exitValue();
            if (exit != 0) {
                log.warn("[parser-init] tesseract returned exit code {}: {}", exit, out);
                return;
            }
            String firstLine = out.lines().findFirst().orElse(out);
            log.info("[parser-init] Tesseract OK: {}", firstLine);

            ProcessBuilder pbLangs = new ProcessBuilder("tesseract", "--list-langs");
            pbLangs.redirectErrorStream(true);
            Process pl = pbLangs.start();
            pl.waitFor(5, TimeUnit.SECONDS);
            String langs = new String(pl.getInputStream().readAllBytes());
            log.info("[parser-init] Tesseract languages:\n{}", langs.trim());
            if (!langs.contains("kor")) {
                log.warn("[parser-init] 'kor' (한국어) 학습 데이터 없음. " +
                        "https://github.com/tesseract-ocr/tessdata/raw/main/kor.traineddata 받아서 tessdata 폴더에 넣어주세요.");
            }
        } catch (Exception e) {
            log.error("[parser-init] Tesseract NOT found on PATH (OCR will silently fail). " +
                    "Install tesseract and add to PATH, or restart IDE/shell after install. Error: {}", e.getMessage());
        }
    }

    private PDFParserConfig.OCR_STRATEGY resolveStrategy(String value) {
        if (value == null || value.isBlank()) return PDFParserConfig.OCR_STRATEGY.NO_OCR;
        try {
            return PDFParserConfig.OCR_STRATEGY.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PDFParserConfig.OCR_STRATEGY.NO_OCR;
        }
    }
}
