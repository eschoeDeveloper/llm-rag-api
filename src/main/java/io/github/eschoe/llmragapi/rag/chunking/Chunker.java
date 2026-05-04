package io.github.eschoe.llmragapi.rag.chunking;

import java.util.List;

/**
 * 문서 텍스트를 RAG에 적합한 청크로 분할한다.
 * 구현체는 chunkSize(목표 글자 수)와 overlap(다음 청크와의 겹침)을 따른다.
 */
public interface Chunker {

    List<String> split(String text, int chunkSize, int overlap);
}
