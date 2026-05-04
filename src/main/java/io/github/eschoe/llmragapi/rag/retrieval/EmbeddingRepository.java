package io.github.eschoe.llmragapi.rag.retrieval;

import io.github.eschoe.llmragapi.rag.retrieval.EmbeddingRow;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface EmbeddingRepository extends ReactiveCrudRepository<EmbeddingRow, Long> {

}
