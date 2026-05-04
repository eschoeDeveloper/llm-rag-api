package io.github.eschoe.llmragapi.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 업로드된 문서 메타데이터의 Redis 저장·조회·삭제만 담당.
 *
 * Redis 키 스킴:
 *   document:{documentId}  → DocumentInfo JSON, TTL 30일
 */
@Repository
public class DocumentMetadataStore {

    private static final Logger log = LoggerFactory.getLogger(DocumentMetadataStore.class);
    private static final Duration TTL = Duration.ofDays(30);

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public DocumentMetadataStore(ReactiveStringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Mono<Void> save(String documentId, DocumentUploadRequest request, int totalChunks) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", documentId);
        payload.put("title", request.getTitle());
        payload.put("description", request.getDescription() != null ? request.getDescription() : "");
        payload.put("category", request.getCategory() != null ? request.getCategory() : "");
        payload.put("totalChunks", totalChunks);
        payload.put("uploadedAt", LocalDateTime.now().toString());

        try {
            String json = objectMapper.writeValueAsString(payload);
            return redis.opsForValue().set(key(documentId), json, TTL).then();
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("Failed to serialize document metadata", e));
        }
    }

    public Mono<DocumentInfo> findById(String documentId) {
        return redis.opsForValue().get(key(documentId))
                .map(this::deserialize);
    }

    public Mono<List<DocumentInfo>> findAll() {
        return redis.keys("document:*")
                .flatMap(key -> redis.opsForValue().get(key).map(this::deserialize))
                .collectList();
    }

    public Mono<Long> deleteById(String documentId) {
        return redis.delete(key(documentId));
    }

    private String key(String documentId) {
        return "document:" + documentId;
    }

    @SuppressWarnings("unchecked")
    private DocumentInfo deserialize(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            String id = String.valueOf(map.getOrDefault("id", "unknown"));
            String title = String.valueOf(map.getOrDefault("title", ""));
            String description = String.valueOf(map.getOrDefault("description", ""));
            String category = String.valueOf(map.getOrDefault("category", ""));
            int totalChunks = ((Number) map.getOrDefault("totalChunks", 0)).intValue();
            Object uploadedAtRaw = map.get("uploadedAt");
            LocalDateTime uploadedAt = uploadedAtRaw != null
                    ? LocalDateTime.parse(uploadedAtRaw.toString())
                    : LocalDateTime.now();
            return new DocumentInfo(id, title, description, category, totalChunks, uploadedAt);
        } catch (Exception e) {
            log.warn("Failed to parse document metadata JSON: {}", e.getMessage());
            return new DocumentInfo("unknown", "파싱 오류", "", "", 0, LocalDateTime.now());
        }
    }
}
