package io.github.eschoe.llmragapi.domain.document;

import io.github.eschoe.llmragapi.client.LlmContextClient;
import io.github.eschoe.llmragapi.dao.EmbeddingQueryDao;
import io.github.eschoe.llmragapi.entity.EmbeddingRow;
import io.github.eschoe.llmragapi.service.DocumentParsingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentUploadService {

    private final DocumentParsingService parsingService;
    private final LlmContextClient llmContextClient;
    private final EmbeddingQueryDao embeddingQueryDao;
    private final ReactiveStringRedisTemplate redis;

    @Value("${app.llm.embedding-model:}")
    private String embeddingModel;

    @Value("${app.document.chunk-size:1000}")
    private int chunkSize;

    @Value("${app.document.overlap-size:100}")
    private int overlapSize;

    public DocumentUploadService(DocumentParsingService parsingService,
                               LlmContextClient llmContextClient,
                               EmbeddingQueryDao embeddingQueryDao,
                               ReactiveStringRedisTemplate redis) {
        this.parsingService = parsingService;
        this.llmContextClient = llmContextClient;
        this.embeddingQueryDao = embeddingQueryDao;
        this.redis = redis;
    }

    public Mono<DocumentUploadResponse> uploadDocument(byte[] fileContent, 
                                                      String fileName, 
                                                      DocumentUploadRequest request) {
        String documentId = UUID.randomUUID().toString();
        
        return Mono.just(new DocumentUploadResponse(documentId, request.getTitle(), "UPLOADING"))
                .flatMap(response -> {
                    // 1. 문서 파싱
                    return parsingService.extractText(fileContent, fileName)
                            .flatMap(text -> parsingService.splitIntoChunks(text, chunkSize, overlapSize)
                                    .flatMap(chunks -> {
                                        response.setTotalChunks(chunks.size());
                                        response.setStatus("PROCESSING");
                                        
                                        // 2. 각 청크에 대해 임베딩 생성 및 저장
                                        return processChunks(documentId, chunks, request)
                                                .map(processedCount -> {
                                                    response.setProcessedChunks(processedCount);
                                                    response.setStatus("COMPLETED");
                                                    return response;
                                                });
                                    }));
                })
                .onErrorResume(e -> {
                    DocumentUploadResponse errorResponse = new DocumentUploadResponse(documentId, request.getTitle(), "FAILED");
                    errorResponse.setErrors(List.of("문서 처리 실패: " + e.getMessage()));
                    return Mono.just(errorResponse);
                });
    }

    private Mono<Integer> processChunks(String documentId, List<String> chunks, DocumentUploadRequest request) {
        return Flux.fromIterable(chunks)
                .index()
                .flatMap(tuple -> {
                    long index = tuple.getT1();
                    String chunk = tuple.getT2();
                    
                    // 임베딩 생성
                    return llmContextClient.embed(embeddingModel, chunk)
                            .flatMap(embedding -> {
                                // 데이터베이스에 저장
                                EmbeddingRow row = new EmbeddingRow();
                                row.setId(System.currentTimeMillis() + index); // Long 타입으로 변경
                                row.setContent(chunk);
                                // float[]를 Float[]로 변환
                                Float[] embeddingArray = new Float[embedding.length];
                                for (int i = 0; i < embedding.length; i++) {
                                    embeddingArray[i] = embedding[i];
                                }
                                row.setEmbedding(embeddingArray);
                                row.setTitle(request.getTitle() + " - 청크 " + (index + 1));
                                row.setCreatedAt(OffsetDateTime.now());
                                
                                return embeddingQueryDao.save(row)
                                        .then(Mono.just(1));
                            });
                })
                .reduce(0, Integer::sum)
                .flatMap(processedCount -> {
                    // 문서 메타데이터 저장
                    return saveDocumentMetadata(documentId, request, chunks.size())
                            .thenReturn(processedCount);
                });
    }

    private Mono<Void> saveDocumentMetadata(String documentId, DocumentUploadRequest request, int totalChunks) {
        String key = "document:" + documentId;
        String metadata = String.format(
            "{\"id\":\"%s\",\"title\":\"%s\",\"description\":\"%s\",\"category\":\"%s\",\"totalChunks\":%d,\"uploadedAt\":\"%s\"}",
            documentId,
            request.getTitle().replace("\"", "\\\""),
            request.getDescription() != null ? request.getDescription().replace("\"", "\\\"") : "",
            request.getCategory() != null ? request.getCategory().replace("\"", "\\\"") : "",
            totalChunks,
            LocalDateTime.now().toString()
        );
        
        return redis.opsForValue().set(key, metadata, Duration.ofDays(30))
                .then();
    }

    public Mono<List<DocumentInfo>> getUserDocuments(String sessionId) {
        String pattern = "document:*";
        
        return redis.keys(pattern)
                .flatMap(key -> redis.opsForValue().get(key)
                        .map(this::parseDocumentInfo))
                .collectList();
    }

    public Mono<DocumentInfo> getDocument(String documentId) {
        String key = "document:" + documentId;
        
        return redis.opsForValue().get(key)
                .map(this::parseDocumentInfo);
    }

    public Mono<Void> deleteDocument(String documentId) {
        String key = "document:" + documentId;
        
        return redis.delete(key)
                .then(embeddingQueryDao.deleteByTitle(documentId + "%"))
                .then();
    }

    private DocumentInfo parseDocumentInfo(String metadataJson) {
        try {
            // 간단한 JSON 파싱 (실제로는 Jackson 사용 권장)
            String id = extractValue(metadataJson, "id");
            String title = extractValue(metadataJson, "title");
            String description = extractValue(metadataJson, "description");
            String category = extractValue(metadataJson, "category");
            int totalChunks = Integer.parseInt(extractValue(metadataJson, "totalChunks"));
            String uploadedAt = extractValue(metadataJson, "uploadedAt");
            
            return new DocumentInfo(id, title, description, category, totalChunks, LocalDateTime.parse(uploadedAt));
        } catch (Exception e) {
            return new DocumentInfo("unknown", "파싱 오류", "", "", 0, LocalDateTime.now());
        }
    }

    private String extractValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) {
            // 숫자 값인 경우
            pattern = "\"" + key + "\":";
            start = json.indexOf(pattern);
            if (start == -1) return "";
            start += pattern.length();
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            if (end == -1) return "";
            return json.substring(start, end).trim();
        }
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end);
    }

    public static class DocumentInfo {
        private String id;
        private String title;
        private String description;
        private String category;
        private int totalChunks;
        private LocalDateTime uploadedAt;

        public DocumentInfo() {}

        public DocumentInfo(String id, String title, String description, String category, int totalChunks, LocalDateTime uploadedAt) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.category = category;
            this.totalChunks = totalChunks;
            this.uploadedAt = uploadedAt;
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public int getTotalChunks() { return totalChunks; }
        public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

        public LocalDateTime getUploadedAt() { return uploadedAt; }
        public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    }
}
