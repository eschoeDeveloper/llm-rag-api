package io.github.eschoe.llmragapi.dao;

import io.github.eschoe.llmragapi.entity.EmbeddingRow;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.sql.Types;
import java.time.OffsetDateTime;

@Repository
public class EmbeddingQueryDao {

    private final DatabaseClient dbClient;

    public EmbeddingQueryDao(DatabaseClient dbClient) {
        this.dbClient = dbClient;
    }

    public Flux<EmbeddingRow> topKByCosine(float[] q, int k) {

        String sql = """
                    SELECT id,  title, content, (1 - cosine_distance(embedding, :queryVector::vector)) as score, created_at
                    FROM chatbot.embeddings
                    ORDER BY cosine_distance(embedding, :queryVector::vector)
                    LIMIT :limit
                """;

        return dbClient.sql(sql)
                .bind("queryVector", q)
                .bind("limit", k)
                .map((row, meta) -> {
                    EmbeddingRow r = new EmbeddingRow();
                    r.setId(row.get("id", Long.class));
                    r.setTitle(row.get("title", String.class));
                    r.setContent(row.get("content", String.class));
                    r.setScore(row.get("score", Double.class));
                    r.setCreatedAt(row.get("created_at", OffsetDateTime.class));
                    return r;
                })
                .all();

    }

}
