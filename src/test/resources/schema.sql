-- 테스트용 스키마
CREATE SCHEMA IF NOT EXISTS chatbot;

CREATE TABLE IF NOT EXISTS chatbot.embeddings (
    id BIGINT PRIMARY KEY,
    title VARCHAR(255),
    content TEXT,
    embedding VECTOR(1536),
    created_at TIMESTAMP,
    document_id VARCHAR(64) DEFAULT '',
    chunk_index INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_embeddings_document_id
    ON chatbot.embeddings (document_id);

-- 테스트용 데이터
INSERT INTO chatbot.embeddings (id, title, content, embedding, created_at, document_id, chunk_index) VALUES
(1, 'Test Document 1', 'This is a test document about machine learning.', ARRAY[0.1, 0.2, 0.3], CURRENT_TIMESTAMP, 'doc-1', 0),
(2, 'Test Document 2', 'This is another test document about artificial intelligence.', ARRAY[0.4, 0.5, 0.6], CURRENT_TIMESTAMP, 'doc-2', 0);
