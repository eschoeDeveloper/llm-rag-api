package io.github.eschoe.llmragapi.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// OCR imports
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

@Service
public class DocumentParsingService {

    private final Tika tika;

    public DocumentParsingService() {
        this.tika = new Tika();
    }

    public Mono<String> extractText(byte[] fileContent, String fileName) {
        return Mono.fromCallable(() -> {
            try {
                System.out.println("[DocumentParsingService] Extracting text from file: " + fileName);
                System.out.println("[DocumentParsingService] File size: " + fileContent.length + " bytes");
                
                // MIME 타입 먼저 확인
                String mimeType = tika.detect(new ByteArrayInputStream(fileContent), fileName);
                System.out.println("[DocumentParsingService] Detected MIME type: " + mimeType);
                
                String extractedText = tika.parseToString(new ByteArrayInputStream(fileContent));
                System.out.println("[DocumentParsingService] Extracted text length: " + (extractedText != null ? extractedText.length() : 0));
                
                if (extractedText != null && !extractedText.trim().isEmpty()) {
                    System.out.println("[DocumentParsingService] First 200 chars: " + extractedText.substring(0, Math.min(200, extractedText.length())));
                } else {
                    System.out.println("[DocumentParsingService] No text extracted or empty text");
                    
                    // PDF인 경우 다른 방법 시도
                    if (mimeType != null && mimeType.toLowerCase().contains("pdf")) {
                        System.out.println("[DocumentParsingService] PDF detected, trying alternative parsing...");
                        return tryAlternativePdfParsing(fileContent);
                    }
                }
                
                return extractedText;
            } catch (IOException | TikaException e) {
                System.err.println("[DocumentParsingService] Parsing error: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("문서 파싱 실패: " + e.getMessage(), e);
            }
        });
    }
    
    private String tryAlternativePdfParsing(byte[] fileContent) {
        try {
            System.out.println("[DocumentParsingService] Trying alternative PDF parsing...");
            
            // Tika의 다른 설정으로 다시 시도
            org.apache.tika.config.TikaConfig config = org.apache.tika.config.TikaConfig.getDefaultConfig();
            org.apache.tika.Tika alternativeTika = new org.apache.tika.Tika(config);
            
            String text = alternativeTika.parseToString(new ByteArrayInputStream(fileContent));
            
            System.out.println("[DocumentParsingService] Alternative parsing result length: " + (text != null ? text.length() : 0));
            if (text != null && !text.trim().isEmpty()) {
                System.out.println("[DocumentParsingService] Alternative parsing successful!");
                return text;
            } else {
                System.out.println("[DocumentParsingService] Alternative parsing also failed, trying OCR...");
                return tryOcrParsing(fileContent);
            }
        } catch (Exception e) {
            System.err.println("[DocumentParsingService] Alternative parsing error: " + e.getMessage());
            return "";
        }
    }
    
    private String tryOcrParsing(byte[] fileContent) {
        try {
            System.out.println("[DocumentParsingService] Starting OCR parsing...");
            
            // Tesseract 인스턴스 생성
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath("tessdata"); // tessdata 폴더 경로
            tesseract.setLanguage("kor+eng"); // 한국어 + 영어
            
            StringBuilder extractedText = new StringBuilder();
            
            // PDFBox 2.x API 사용
            try (PDDocument document = PDDocument.load(new ByteArrayInputStream(fileContent))) {
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                
                System.out.println("[DocumentParsingService] PDF pages: " + document.getNumberOfPages());
                
                // 각 페이지를 이미지로 변환하고 OCR 수행
                for (int page = 0; page < document.getNumberOfPages(); page++) {
                    System.out.println("[DocumentParsingService] Processing page " + (page + 1));
                    
                    BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
                    
                    // OCR 수행
                    String pageText = tesseract.doOCR(bufferedImage);
                    if (pageText != null && !pageText.trim().isEmpty()) {
                        extractedText.append(pageText).append("\n");
                        System.out.println("[DocumentParsingService] Page " + (page + 1) + " OCR result length: " + pageText.length());
                    } else {
                        System.out.println("[DocumentParsingService] Page " + (page + 1) + " OCR result: empty");
                    }
                }
            }
            
            String result = extractedText.toString().trim();
            System.out.println("[DocumentParsingService] OCR completed, total text length: " + result.length());
            
            if (!result.isEmpty()) {
                System.out.println("[DocumentParsingService] OCR first 200 chars: " + result.substring(0, Math.min(200, result.length())));
            }
            
            return result;
            
        } catch (Exception e) {
            System.err.println("[DocumentParsingService] OCR error: " + e.getMessage());
            e.printStackTrace();
            
            // PDF 손상 또는 형식 오류인 경우 안내 메시지
            if (e.getMessage().contains("Header doesn't contain versioninfo") || 
                e.getMessage().contains("Invalid PDF")) {
                System.out.println("[DocumentParsingService] PDF 파일이 손상되었거나 올바른 PDF 형식이 아닙니다.");
                System.out.println("[DocumentParsingService] 다른 PDF 파일로 다시 시도해주세요.");
            }
            
            return "";
        }
    }

    public Mono<String> detectMimeType(byte[] fileContent, String fileName) {
        return Mono.fromCallable(() -> {
            try {
                return tika.detect(new ByteArrayInputStream(fileContent), fileName);
            } catch (IOException e) {
                throw new RuntimeException("MIME 타입 감지 실패: " + e.getMessage(), e);
            }
        });
    }

    public Mono<List<String>> splitIntoChunks(String text, int chunkSize, int overlapSize) {
        return Mono.fromCallable(() -> {
            List<String> chunks = new ArrayList<>();
            
            if (text == null || text.trim().isEmpty()) {
                return chunks;
            }

            String[] sentences = text.split("[.!?]+");
            StringBuilder currentChunk = new StringBuilder();
            
            for (String sentence : sentences) {
                sentence = sentence.trim();
                if (sentence.isEmpty()) continue;
                
                // 현재 청크에 문장을 추가했을 때 크기 확인
                if (currentChunk.length() + sentence.length() + 1 <= chunkSize) {
                    if (currentChunk.length() > 0) {
                        currentChunk.append(" ");
                    }
                    currentChunk.append(sentence).append(".");
                } else {
                    // 현재 청크가 완성되면 저장
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString().trim());
                    }
                    
                    // 새로운 청크 시작 (오버랩 고려)
                    String overlapText = getOverlapText(currentChunk.toString(), overlapSize);
                    currentChunk = new StringBuilder(overlapText);
                    if (currentChunk.length() > 0) {
                        currentChunk.append(" ");
                    }
                    currentChunk.append(sentence).append(".");
                }
            }
            
            // 마지막 청크 추가
            if (currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
            }
            
            return chunks;
        });
    }

    private String getOverlapText(String text, int overlapSize) {
        if (text.length() <= overlapSize) {
            return text;
        }
        return text.substring(text.length() - overlapSize);
    }

    public Mono<DocumentMetadata> extractMetadata(byte[] fileContent, String fileName) {
        return Mono.fromCallable(() -> {
            try {
                org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
                metadata.set(org.apache.tika.metadata.TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
                
                tika.parse(new ByteArrayInputStream(fileContent), metadata);
                
                return new DocumentMetadata(
                    metadata.get(org.apache.tika.metadata.TikaCoreProperties.TITLE),
                    metadata.get(org.apache.tika.metadata.TikaCoreProperties.CREATOR),
                    metadata.get(org.apache.tika.metadata.TikaCoreProperties.CREATED),
                    metadata.get(org.apache.tika.metadata.Metadata.CONTENT_TYPE),
                    metadata.get(org.apache.tika.metadata.Metadata.CONTENT_LENGTH)
                );
            } catch (Exception e) {
                return new DocumentMetadata(fileName, null, null, null, null);
            }
        });
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

        // Getters and Setters
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
