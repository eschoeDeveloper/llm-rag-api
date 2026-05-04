package io.github.eschoe.llmragapi.rag.retrieval;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Repository
public class EmbeddingQueryDao {

    private final DatabaseClient dbClient;

    public EmbeddingQueryDao(DatabaseClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * pgvector cosine 유사도 기준 top-K 검색.
     *
     * 쿼리:
     *   ORDER BY embedding <=> :queryVector::vector LIMIT :limit
     *
     * pgvector 연산자:
     *   <=> = cosine distance (0 = 동일, 1 = 직교)
     *   <-> = L2 distance
     *   <#> = negative inner product
     *
     * score = 1 - cosine_distance = cosine_similarity (0~1, 높을수록 유사).
     *
     * 보안:
     *   - 벡터를 String.format 으로 SQL 에 직접 보간하지 않음 (이전 버전 SQL injection 위험 있었음)
     *   - :queryVector 바인드 후 ::vector 캐스트 — pgvector 는 string→vector 캐스트 지원
     *
     * 인덱스:
     *   - V2 마이그레이션에서 hnsw (vector_cosine_ops) 인덱스 추가
     *   - 데이터 많을 때 풀 스캔 대비 ×100 빠름
     */
    public Flux<EmbeddingRow> topKByCosine(float[] q, int k) {

        String vectorString = toVectorLiteral(q);

        String sql = """
            SELECT id, title, content,
                   1 - (embedding <=> :queryVector::vector) as score,
                   created_at, document_id, chunk_index
            FROM chatbot.embeddings
            ORDER BY embedding <=> :queryVector::vector
            LIMIT :limit
            """;

        return dbClient.sql(sql)
                .bind("queryVector", vectorString)
                .bind("limit", k)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    /**
     * PostgreSQL full-text 검색 기반 키워드 매칭.
     * tsvector 컬럼/GIN 인덱스 없이도 동작하지만, 운영에서는 인덱스 추가 권장.
     * (V2 마이그레이션 참조)
     */
    public Flux<EmbeddingRow> topKByKeyword(String query, int k) {
        if (query == null || query.isBlank()) return Flux.empty();

        String sql = """
            SELECT id, title, content,
                   ts_rank_cd(to_tsvector('simple', coalesce(content, '')), plainto_tsquery('simple', :query)) as score,
                   created_at, document_id, chunk_index
            FROM chatbot.embeddings
            WHERE to_tsvector('simple', coalesce(content, '')) @@ plainto_tsquery('simple', :query)
            ORDER BY score DESC
            LIMIT :limit
            """;

        return dbClient.sql(sql)
                .bind("query", query)
                .bind("limit", k)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    public Mono<EmbeddingRow> save(EmbeddingRow row) {
        String sql = """
                INSERT INTO chatbot.embeddings (id, title, content, embedding, created_at, document_id, chunk_index)
                VALUES (:id, :title, :content, :embedding::vector, :createdAt, :documentId, :chunkIndex)
                """;

        return dbClient.sql(sql)
                .bind("id", row.getId())
                .bind("title", row.getTitle())
                .bind("content", row.getContent())
                .bind("embedding", row.getEmbedding())
                .bind("createdAt", row.getCreatedAt())
                .bind("documentId", row.getDocumentId() != null ? row.getDocumentId() : "")
                .bind("chunkIndex", row.getChunkIndex() != null ? row.getChunkIndex() : 0)
                .fetch()
                .rowsUpdated()
                .thenReturn(row);
    }

    public Mono<Long> deleteByDocumentId(String documentId) {
        String sql = "DELETE FROM chatbot.embeddings WHERE document_id = :documentId";

        return dbClient.sql(sql)
                .bind("documentId", documentId)
                .fetch()
                .rowsUpdated();
    }

    public Mono<Long> deleteByTitle(String titlePattern) {
        String sql = "DELETE FROM chatbot.embeddings WHERE title LIKE :titlePattern";

        return dbClient.sql(sql)
                .bind("titlePattern", titlePattern)
                .fetch()
                .rowsUpdated();
    }

    private EmbeddingRow mapRow(io.r2dbc.spi.Row row) {
        EmbeddingRow r = new EmbeddingRow();
        r.setId(row.get("id", Long.class));
        r.setTitle(row.get("title", String.class));
        r.setContent(row.get("content", String.class));
        Number score = row.get("score", Number.class);
        r.setScore(score != null ? score.doubleValue() : 0.0);
        r.setCreatedAt(row.get("created_at", OffsetDateTime.class));
        r.setDocumentId(row.get("document_id", String.class));
        r.setChunkIndex(row.get("chunk_index", Integer.class));
        return r;
    }

    private String toVectorLiteral(float[] q) {
        return "[" + IntStream.range(0, q.length)
                .mapToObj(i -> String.valueOf(q[i]))
                .collect(Collectors.joining(",")) + "]";
    }
}
