-- V2: 소스 추적 컬럼 + 검색 인덱스 추가
-- 자동 적용되지 않음. 운영 DB에 수동 적용 필요.
-- 적용 전: 기존 데이터가 있다면 백업 권장.

BEGIN;

-- 1) 소스 추적용 컬럼
ALTER TABLE chatbot.embeddings
    ADD COLUMN IF NOT EXISTS document_id VARCHAR(64) DEFAULT '',
    ADD COLUMN IF NOT EXISTS chunk_index INTEGER DEFAULT 0;

-- 기존 데이터를 위한 임시 마이그레이션: title이 "TITLE - 청크 N" 패턴이면 chunk_index 추출 시도.
-- 새로 적재되는 데이터는 코드에서 직접 채운다.
UPDATE chatbot.embeddings
SET chunk_index = COALESCE(
    NULLIF(
        substring(title FROM '청크\s+(\d+)\s*$'),
        ''
    )::INTEGER - 1,
    0
)
WHERE chunk_index = 0 AND title ~ '청크\s+\d+\s*$';

-- 2) document_id 인덱스 (deleteByDocumentId 가속)
CREATE INDEX IF NOT EXISTS idx_embeddings_document_id
    ON chatbot.embeddings (document_id);

-- 3) pgvector HNSW 인덱스 (cosine)
-- 데이터가 많을 때 검색 속도가 풀 스캔 대비 수십~수백 배 빨라진다.
-- 사전 요건: pgvector 0.5.0+ (`CREATE EXTENSION vector`)
CREATE INDEX IF NOT EXISTS idx_embeddings_embedding_hnsw
    ON chatbot.embeddings
    USING hnsw (embedding vector_cosine_ops);

-- 4) PostgreSQL full-text 검색용 GIN 인덱스
-- topKByKeyword 가속.
CREATE INDEX IF NOT EXISTS idx_embeddings_content_fts
    ON chatbot.embeddings
    USING GIN (to_tsvector('simple', coalesce(content, '')));

COMMIT;
