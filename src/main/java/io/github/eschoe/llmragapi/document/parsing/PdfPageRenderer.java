package io.github.eschoe.llmragapi.document.parsing;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 페이지 → PNG 이미지 렌더링.
 * Vision API 호출 전 단계로 사용.
 */
@Component
public class PdfPageRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfPageRenderer.class);

    /**
     * PDF 의 처음 {@code maxPages} 페이지를 PNG 로 렌더.
     */
    public List<RenderedPage> renderToPng(byte[] pdfBytes, int dpi, int maxPages) throws IOException {
        List<RenderedPage> out = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int totalPages = doc.getNumberOfPages();
            int limit = Math.min(totalPages, maxPages);
            log.info("[render] {} of {} pages at {} DPI", limit, totalPages, dpi);

            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < limit; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "PNG", baos);
                out.add(new RenderedPage(i, baos.toByteArray()));
            }
        }
        return out;
    }

    /** 0-based index + PNG bytes. */
    public record RenderedPage(int index, byte[] png) {}
}
