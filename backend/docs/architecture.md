# DevMind Architecture

## Goal

DevMind is a Java backend project for a personal developer knowledge base. The system stores learning notes and project reviews, turns long documents into chunks, retrieves relevant chunks, builds a RAG prompt, and routes the final answer generation through a pluggable LLM client.

## Module Overview

```mermaid
flowchart TB
    Auth["auth module"] --> User["user module"]
    Document["document module"] --> Chunk["document chunk service"]
    Search["search module"] --> Chunk
    AI["ai module"] --> Search
    AI --> Prompt["prompt builder"]
    AI --> LLM["llm client router"]
    AI --> Log["ask log service"]
    AI --> Feedback["ask feedback service"]
    LLM --> Mock["mock client"]
    LLM --> DeepSeek["deepseek client"]
```

## Data Model

```mermaid
erDiagram
    user_account ||--o{ knowledge_document : owns
    user_account ||--o{ knowledge_document_chunk : owns
    knowledge_document ||--o{ knowledge_document_chunk : contains
    user_account ||--o{ ai_ask_log : creates
    user_account ||--o{ ai_ask_feedback : creates
    ai_ask_log ||--o{ ai_ask_feedback : receives

    user_account {
        bigint id
        varchar username
        varchar password_hash
        varchar nickname
        varchar email
        tinyint status
    }

    knowledge_document {
        bigint id
        bigint user_id
        varchar title
        mediumtext content
        varchar source_type
        varchar tags
        tinyint status
    }

    knowledge_document_chunk {
        bigint id
        bigint document_id
        bigint user_id
        int chunk_index
        text content
        int token_count
        tinyint status
    }

    ai_ask_log {
        bigint id
        bigint user_id
        varchar question
        varchar retrieval_keyword
        mediumtext prompt_preview
        mediumtext answer
        varchar model_provider
        tinyint mock
        int prompt_tokens
        int completion_tokens
        int total_tokens
        varchar retrieved_chunk_ids
        bigint elapsed_ms
    }

    ai_ask_feedback {
        bigint id
        bigint user_id
        bigint ask_log_id
        tinyint helpful
        varchar reason
        mediumtext expected_answer
        tinyint status
    }
```

## RAG Flow

```mermaid
sequenceDiagram
    participant Client
    participant AI as AiAskService
    participant Search as ChunkSearchService
    participant Prompt as PromptBuilderService
    participant LLM as LlmClientRouter
    participant Log as AiAskLogService

    Client->>AI: POST /api/v1/ai/ask
    AI->>Search: searchChunks(userId, keyword)
    Search-->>AI: retrieved chunks
    AI->>Prompt: buildPrompt(question, chunks)
    Prompt-->>AI: promptPreview
    AI->>LLM: generate(prompt, chunks, citations)
    LLM-->>AI: answer
    AI->>Log: saveSuccessLog(...)
    Log-->>AI: logId
    AI-->>Client: answer + citations + logId
```

## Design Choices

- Soft archive is used for documents and chunks to preserve history.
- Chunks are rebuilt after document updates to keep retrieval results aligned with the latest content.
- Retrieval v0 uses keyword matching first, because it is easy to debug before introducing embeddings.
- `LlmClient` separates model-provider implementation from RAG orchestration.
- Ask logs record question, retrieval keyword, chunk ids, answer, provider, token usage, and elapsed time for later bad-case analysis.
- Ask feedback stores helpfulness labels, reasons, and expected answers so bad cases can become a small evaluation dataset.
- The evaluation summary endpoint aggregates feedback count, bad-case count, bad-case rate, and recent bad cases for RAG quality analysis.
- Flyway manages database schema versioning so local setup and future migrations do not depend on manual SQL copy-paste.

## Next Improvements

- Add DeepSeek real-call smoke test with environment-only API key.
- Add embedding and vector retrieval.
- Add hybrid retrieval: keyword + vector.
- Add reranking.
- Use feedback labels to build retrieval evaluation and bad-case reports.
