-- 테스트용 스키마
CREATE SCHEMA IF NOT EXISTS chatbot;

CREATE TABLE IF NOT EXISTS chatbot.embeddings (
    id BIGINT PRIMARY KEY,
    title VARCHAR(255),
    content TEXT,
    embedding VECTOR(1536),
    created_at TIMESTAMP
);

-- 테스트용 데이터
INSERT INTO chatbot.embeddings (id, title, content, embedding, created_at) VALUES 
(1, 'Test Document 1', 'This is a test document about machine learning.', ARRAY[0.1, 0.2, 0.3], CURRENT_TIMESTAMP),
(2, 'Test Document 2', 'This is another test document about artificial intelligence.', ARRAY[0.4, 0.5, 0.6], CURRENT_TIMESTAMP);
