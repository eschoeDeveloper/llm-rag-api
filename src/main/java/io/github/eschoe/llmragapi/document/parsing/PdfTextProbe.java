package io.github.eschoe.llmragapi.document.parsing;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PDF의 embedded text 양을 빠르게 측정.
 * image-only PDF 판정 + OCR/Vision 라우팅 결정에 사용.
 * 손상된 PDF 등 probe 실패 시 {@code Result.usable() == false} 로 반환 — Tika 본격 파싱에 위임.
 */
@Component
public class PdfTextProbe {

    private static final Logger log = LoggerFactory.getLogger(PdfTextProbe.class);

    public Result probe(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false);
            String text = stripper.getText(doc);
            int chars = text == null ? 0 : text.replaceAll("\\s+", "").length();
            return new Result(doc.getNumberOfPages(), chars, true);
        } catch (Exception e) {
            log.debug("[probe] PDF probe failed (will fall through to Tika): {}", e.getMessage());
            return new Result(0, 0, false);
        }
    }

    public record Result(int pages, int totalChars, boolean ok) {
        public boolean usable() { return ok && pages > 0; }

        public double charsPerPage() {
            return pages == 0 ? 0.0 : (double) totalChars / pages;
        }
    }
}
