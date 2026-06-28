ALTER TABLE knowledge_document_chunk
    ADD FULLTEXT INDEX ft_chunk_content (content);
