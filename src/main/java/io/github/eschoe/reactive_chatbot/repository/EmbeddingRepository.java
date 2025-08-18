package io.github.eschoe.reactive_chatbot.repository;

import io.github.eschoe.reactive_chatbot.entity.EmbeddingRow;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface EmbeddingRepository extends ReactiveCrudRepository<EmbeddingRow, Long> {

}
