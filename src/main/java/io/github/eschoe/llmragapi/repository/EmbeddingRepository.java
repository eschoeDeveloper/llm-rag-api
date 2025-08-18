package io.github.eschoe.llmragapi.repository;

import io.github.eschoe.llmragapi.entity.EmbeddingRow;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface EmbeddingRepository extends ReactiveCrudRepository<EmbeddingRow, Long> {

}
