# DevMind Frontend

Vue 3 + Vite frontend for the DevMind AI knowledge base backend.

## Features

- Login and registration
- Knowledge document list and creation
- AI Ask workflow
- Citations and token usage display
- Fallback state display when retrieval returns no chunks
- Recent ask logs with success/failure status, provider, chunk count, and latency
- Helpful / bad-case feedback submission
- RAG evaluation summary

## Local Development

Start the backend first:

```text
http://localhost:8081
```

Then start the frontend:

```bash
npm install
npm run dev
```

Open:

```text
http://127.0.0.1:5173
```

The Vite dev server proxies `/api` requests to the backend.

After signing in, create the sample Redis document and ask a question. The AI Ask panel shows:

```text
answer
provider
logId
retrieval keywords
citations
token usage
retrieved chunks
prompt preview
recent ask logs
```

To verify the no-context fallback, ask:

```text
What is Kubernetes pod eviction policy?
```

If the knowledge base has no related chunks, the backend returns a fallback answer and skips the LLM provider.

## Build

```bash
npm run build
```

## Notes

This frontend does not store model API keys. DeepSeek or other provider keys belong only in the backend runtime environment.
