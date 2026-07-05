-- The default InnoDB FULLTEXT parser tokenizes by whitespace and punctuation,
-- so Chinese text is indexed as one giant token and Chinese queries never match.
-- Rebuild the chunk content index with the built-in ngram parser (bigram by default)
-- so FULLTEXT works for both Chinese and English content.
ALTER TABLE knowledge_document_chunk
    DROP INDEX ft_chunk_content;

ALTER TABLE knowledge_document_chunk
    ADD FULLTEXT INDEX ft_chunk_content (content) WITH PARSER ngram;
