# DevMind

[![CI](https://github.com/wwjorker/devmind/actions/workflows/ci.yml/badge.svg)](https://github.com/wwjorker/devmind/actions/workflows/ci.yml)

DevMind is an AI-powered developer knowledge base built as a Java backend portfolio project with a lightweight Vue frontend.

It demonstrates a complete RAG-style workflow instead of a thin AI API wrapper:

```text
knowledge documents
-> document chunks
-> multilingual keyword retrieval
-> prompt building
-> LLM provider routing
-> answers with citations
-> ask logs with token usage
-> bad-case feedback
-> evaluation summary
```

## Project Structure

```text
devmind
+-- backend    Spring Boot backend
+-- frontend   Vue 3 + Vite frontend
+-- .github    CI workflow
```

## Backend

Tech stack:

```text
Java 17
Spring Boot 3.3.x
Spring Security
MyBatis-Plus
MySQL
Flyway
JJWT
Springdoc OpenAPI
DeepSeek API
```

Run backend tests:

```bash
cd backend
mvn test
```

Start the backend on port `8081` after creating the `devmind` database and configuring local database credentials.

Backend documentation: [backend/README.md](backend/README.md)

## Frontend

Tech stack:

```text
Vue 3
Vite
TypeScript
```

Run locally:

```bash
cd frontend
npm install
npm run dev
```

Open:

```text
http://127.0.0.1:5173
```

Frontend documentation: [frontend/README.md](frontend/README.md)

## CI

GitHub Actions runs:

```text
backend: mvn test
frontend: npm ci && npm run build
```

This verifies the backend unit tests and frontend production build on every push and pull request to `main`.

## Interview Highlights

- JWT authentication and user-scoped data isolation
- Flyway-managed schema migration
- RAG ask flow with prompt preview and citations
- Multilingual keyword extraction for Chinese and English technical questions
- Pluggable LLM provider layer with Mock and DeepSeek implementations
- AI ask logs with latency, retrieved chunk ids, and token usage
- Bad-case feedback and lightweight RAG evaluation summary
- Unit tests and GitHub Actions CI
