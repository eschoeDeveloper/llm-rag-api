package io.github.eschoe.llmragapi.rag.retrieval;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (RRF) 으로 vector·keyword 결과를 합친다.
 *
 * 점수: score(d) = Σ 1 / (k + rank_i(d))
 *  - k는 일반적으로 60 (Cormack et al.)
 *  - 점수 자체의 분포가 달라도 rank만 사용하므로 안전하게 결합 가능
 */
@Component
public class HybridRetriever {

    private static final int RRF_K = 60;

    /**
     * vector 검색과 keyword(PG full-text) 검색 결과를 RRF 로 합산해 단일 리스트 반환.
     *
     * RRF 가 단순 가중평균보다 나은 이유:
     *  - 두 검색이 점수 분포가 달라도 (cosine 0~1 vs ts_rank 임의 스케일) rank 만 사용해 안전 결합
     *  - 어느 한 검색에서만 매칭된 항목도 점수가 살아남음
     *  - 양쪽에서 모두 잡힌 항목은 점수가 누적돼 자연스럽게 상위로
     *
     * k=60 은 원논문 (Cormack et al.) 권장값. 완벽 매칭(rank 1)이 점수 1/61 로 평탄화돼
     * 너무 가파른 cliff 없이 유연하게 결합.
     */
    public Mono<List<EmbeddingRow>> fuse(Mono<List<EmbeddingRow>> vectorTop,
                                         Mono<List<EmbeddingRow>> keywordTop,
                                         int finalK) {
        return Mono.zip(
                vectorTop.defaultIfEmpty(List.of()),
                keywordTop.defaultIfEmpty(List.of())
        ).map(t -> rrf(t.getT1(), t.getT2(), finalK));
    }

    private List<EmbeddingRow> rrf(List<EmbeddingRow> vector, List<EmbeddingRow> keyword, int finalK) {
        Map<Long, Double> scoreById = new LinkedHashMap<>();
        Map<Long, EmbeddingRow> rowById = new LinkedHashMap<>();

        accumulate(vector, scoreById, rowById);
        accumulate(keyword, scoreById, rowById);

        List<Map.Entry<Long, Double>> ranked = new ArrayList<>(scoreById.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<EmbeddingRow> out = new ArrayList<>();
        for (Map.Entry<Long, Double> e : ranked) {
            if (out.size() >= finalK) break;
            EmbeddingRow original = rowById.get(e.getKey());
            // 원본 row 의 score 를 mutate 하지 않고 RRF 점수가 들어간 사본 반환.
            // 같은 row 가 다른 곳에서 cosine score 그대로 필요할 수 있음.
            out.add(copyWithScore(original, e.getValue()));
        }
        return out;
    }

    private EmbeddingRow copyWithScore(EmbeddingRow src, double score) {
        EmbeddingRow copy = new EmbeddingRow();
        copy.setId(src.getId());
        copy.setTitle(src.getTitle());
        copy.setContent(src.getContent());
        copy.setEmbedding(src.getEmbedding());
        copy.setCreatedAt(src.getCreatedAt());
        copy.setDocumentId(src.getDocumentId());
        copy.setChunkIndex(src.getChunkIndex());
        copy.setScore(score);
        return copy;
    }

    private void accumulate(List<EmbeddingRow> rows, Map<Long, Double> scoreById, Map<Long, EmbeddingRow> rowById) {
        for (int i = 0; i < rows.size(); i++) {
            EmbeddingRow row = rows.get(i);
            Long id = row.getId();
            double contribution = 1.0 / (RRF_K + (i + 1));
            scoreById.merge(id, contribution, Double::sum);
            rowById.putIfAbsent(id, row);
        }
    }
}
