package io.github.eschoe.llmragapi.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentParsingService {

    private final Tika tika;

    public DocumentParsingService() {
        this.tika = new Tika();
    }

    public Mono<String> extractText(byte[] fileContent, String fileName) {
        return Mono.fromCallable(() -> {
            try {
                return tika.parseToString(new ByteArrayInputStream(fileContent));
            } catch (IOException | TikaException e) {
                throw new RuntimeException("문서 파싱 실패: " + e.getMessage(), e);
            }
        });
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
