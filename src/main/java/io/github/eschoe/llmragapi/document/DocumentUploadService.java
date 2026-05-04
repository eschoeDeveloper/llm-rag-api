package io.github.eschoe.llmragapi.document;

import io.github.eschoe.llmragapi.document.parsing.DocumentParsingService;
import io.github.eschoe.llmragapi.llm.cache.LlmCacheService;
import io.github.eschoe.llmragapi.rag.chunking.Chunker;
import io.github.eschoe.llmragapi.rag.embedding.ChunkEmbedder;
import io.github.eschoe.llmragapi.rag.retrieval.EmbeddingQueryDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * 문서 업로드 오케스트레이터.
 *
 * 흐름:
 *  1. 파싱 ({@link DocumentParsingService})
 *  2. 청킹 ({@link Chunker})
 *  3. 청크 임베딩 + 저장 ({@link ChunkEmbedder})
 *  4. 메타 저장 ({@link DocumentMetadataStore})
 *  5. 응답 캐시 무효화 ({@link LlmCacheService})
 */
@Service
public class DocumentUploadService {

    private final DocumentParsingService parsingService;
    private final EmbeddingQueryDao embeddingQueryDao;
    private final Chunker chunker;
    private final ChunkEmbedder chunkEmbedder;
    private final LlmCacheService llmCache;
    private final DocumentMetadataStore metadataStore;

    @Value("${app.document.chunk-size:1000}")
    private int chunkSize;

    @Value("${app.document.overlap-size:100}")
    private int overlapSize;

    public DocumentUploadService(DocumentParsingService parsingService,
                                 EmbeddingQueryDao embeddingQueryDao,
                                 Chunker chunker,
                                 ChunkEmbedder chunkEmbedder,
                                 LlmCacheService llmCache,
                                 DocumentMetadataStore metadataStore) {
        this.parsingService = parsingService;
        this.embeddingQueryDao = embeddingQueryDao;
        this.chunker = chunker;
        this.chunkEmbedder = chunkEmbedder;
        this.llmCache = llmCache;
        this.metadataStore = metadataStore;
    }

    /**
     * 문서 업로드 풀 흐름.
     *
     * 단계:
     *   1. {@link DocumentParsingService} — 텍스트 추출 (Tika/OCR/Vision 자동 라우팅)
     *   2. {@link Chunker} — 단락→문장→글자 폴백 청킹
     *   3. {@link ChunkEmbedder} — 청크별 임베딩 + DB 저장
     *   4. {@link DocumentMetadataStore} — Redis 에 메타 저장 (30일 TTL)
     *   5. {@link LlmCacheService} 응답 캐시 무효화 — 새 문서 추가됐으니 stale 답변 제거
     *
     * 실패 처리 (onErrorResume):
     *   - 어느 단계든 실패 시 status=FAILED, errors=[원인] 응답
     *   - 핸들러가 422 로 변환해 프론트에 명확한 에러 표시
     *
     * 비용 의식적 경로:
     *   - Vision 사용 시 VisionUsageTracker 가 일일 한도 enforcement
     *   - 한도 초과면 1단계에서 즉시 실패 → 임베딩 호출까지 안 감
     */
    public Mono<DocumentUploadResponse> uploadDocument(byte[] fileContent,
                                                       String fileName,
                                                       DocumentUploadRequest request) {
        String documentId = UUID.randomUUID().toString();
        DocumentUploadResponse response = new DocumentUploadResponse(documentId, request.getTitle(), "UPLOADING");

        return parsingService.extractText(fileContent, fileName, request.getSessionId())
                .flatMap(extraction -> {
                    response.setExtractionMode(extraction.mode());
                    if (extraction.warnings() != null && !extraction.warnings().isEmpty()) {
                        response.setWarnings(extraction.warnings());
                    }
                    List<String> chunks = chunker.split(extraction.text(), chunkSize, overlapSize);
                    response.setTotalChunks(chunks.size());
                    response.setStatus("PROCESSING");

                    return chunkEmbedder.embedAndStore(documentId, request, chunks)
                            .flatMap(processedCount -> metadataStore.save(documentId, request, chunks.size())
                                    .thenReturn(processedCount))
                            .flatMap(processedCount -> llmCache.invalidateAnswers().thenReturn(processedCount))
                            .map(processedCount -> {
                                response.setProcessedChunks(processedCount);
                                response.setStatus("COMPLETED");
                                return response;
                            });
                })
                .onErrorResume(e -> {
                    DocumentUploadResponse errorResponse = new DocumentUploadResponse(documentId, request.getTitle(), "FAILED");
                    errorResponse.setErrors(List.of("문서 처리 실패: " + e.getMessage()));
                    return Mono.just(errorResponse);
                });
    }

    public Mono<List<DocumentInfo>> getUserDocuments(String sessionId) {
        return metadataStore.findAll();
    }

    public Mono<DocumentInfo> getDocument(String documentId) {
        return metadataStore.findById(documentId);
    }

    public Mono<Void> deleteDocument(String documentId) {
        return metadataStore.deleteById(documentId)
                .then(embeddingQueryDao.deleteByDocumentId(documentId))
                .then(llmCache.invalidateAnswers())
                .then();
    }
}
