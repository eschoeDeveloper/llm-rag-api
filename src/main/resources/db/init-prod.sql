/* 운영 DB 초기화 — 한 번만 실행. */
/* 적용 순서: extension → schema → table → indexes. */
/* 모두 IF NOT EXISTS 라 재실행해도 안전. */

/* 1) pgvector 확장 */
CREATE EXTENSION IF NOT EXISTS vector;

/* 2) 스키마 */
CREATE SCHEMA IF NOT EXISTS chatbot;

/* 3) 임베딩 테이블 (1536차원 = OpenAI text-embedding-3-small) */
CREATE TABLE IF NOT EXISTS chatbot.embeddings (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255),
    content TEXT,
    embedding vector(1536),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    document_id VARCHAR(64) DEFAULT '',
    chunk_index INTEGER DEFAULT 0
);

/* 4) document_id 인덱스 (delete-by-document 가속) */
CREATE INDEX IF NOT EXISTS idx_embeddings_document_id
    ON chatbot.embeddings (document_id);

/* 5) HNSW 인덱스 (cosine 유사도 검색) */
/* pgvector 0.5.0+ 필요. 데이터가 많을 때 풀스캔 대비 수십~수백 배 빠름. */
CREATE INDEX IF NOT EXISTS idx_embeddings_embedding_hnsw
    ON chatbot.embeddings
    USING hnsw (embedding vector_cosine_ops);
/* 6) Full-text GIN 인덱스 (keyword 검색 가속) */
CREATE INDEX IF NOT EXISTS idx_embeddings_content_fts
    ON chatbot.embeddings
    USING GIN (to_tsvector('simple', coalesce(content, '')));
