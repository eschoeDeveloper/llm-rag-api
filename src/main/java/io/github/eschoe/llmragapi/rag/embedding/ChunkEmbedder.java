package io.github.eschoe.llmragapi.rag.embedding;

import io.github.eschoe.llmragapi.document.DocumentUploadRequest;
import io.github.eschoe.llmragapi.llm.LlmClient;
import io.github.eschoe.llmragapi.rag.config.EmbeddingProperties;
import io.github.eschoe.llmragapi.rag.retrieval.EmbeddingQueryDao;
import io.github.eschoe.llmragapi.rag.retrieval.EmbeddingRow;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 청크 리스트 → (각 청크 임베딩 + EmbeddingRow 생성 + DB 저장) → 처리 개수 반환.
 * DocumentUploadService 에서 분리해 단일 책임으로.
 */
@Component
public class ChunkEmbedder {

    private final LlmClient llmClient;
    private final EmbeddingQueryDao embeddingQueryDao;
    private final EmbeddingProperties embeddingProps;

    public ChunkEmbedder(LlmClient llmClient,
                         EmbeddingQueryDao embeddingQueryDao,
                         EmbeddingProperties embeddingProps) {
        this.llmClient = llmClient;
        this.embeddingQueryDao = embeddingQueryDao;
        this.embeddingProps = embeddingProps;
    }

    /**
     * 청크별 임베딩 생성 + DB 저장. 처리된 청크 수 반환.
     */
    public Mono<Integer> embedAndStore(String documentId,
                                       DocumentUploadRequest request,
                                       List<String> chunks) {
        return Flux.fromIterable(chunks)
                .index()
                .flatMap(tuple -> {
                    long index = tuple.getT1();
                    String chunk = tuple.getT2();
                    return llmClient.embed(embeddingProps.getModel(), chunk)
                            .flatMap(embedding -> {
                                EmbeddingRow row = buildRow(documentId, request, index, chunk, embedding);
                                return embeddingQueryDao.save(row).thenReturn(1);
                            });
                })
                .reduce(0, Integer::sum);
    }

    private EmbeddingRow buildRow(String documentId,
                                  DocumentUploadRequest request,
                                  long chunkIndex,
                                  String chunk,
                                  float[] embedding) {
        EmbeddingRow row = new EmbeddingRow();
        row.setId(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
        row.setContent(chunk);
        Float[] arr = new Float[embedding.length];
        for (int i = 0; i < embedding.length; i++) arr[i] = embedding[i];
        row.setEmbedding(arr);
        row.setTitle(request.getTitle());
        row.setCreatedAt(OffsetDateTime.now());
        row.setDocumentId(documentId);
        row.setChunkIndex((int) chunkIndex);
        return row;
    }
}
