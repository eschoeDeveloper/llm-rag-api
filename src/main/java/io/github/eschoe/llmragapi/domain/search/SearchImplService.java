package io.github.eschoe.llmragapi.domain.search;

import io.github.eschoe.llmragapi.dao.EmbeddingQueryDao;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class SearchImplService implements SearchService {

    private final EmbeddingQueryDao embeddingQueryDao;

    public SearchImplService(EmbeddingQueryDao embeddingQueryDao) {
        this.embeddingQueryDao = embeddingQueryDao;
    }

    @Override
    public Mono<List<TopKCosine>> topKByCosine(SearchBody searchBody) {

        float[] emb = searchBody.embedding();

        if (emb == null || emb.length == 0)
            return Mono.error(new IllegalArgumentException("embedding is required"));

        int k = (searchBody.topK() != null && searchBody.topK() > 0) ? searchBody.topK() : 10;

        return embeddingQueryDao.topKByCosine(emb, k)
                .map(entity -> new TopKCosine(entity.getId(), entity.getTitle(), entity.getContent(), entity.getCreatedAt()))
                .collectList();

    }

}
