# DevMind Frontend

Vue 3 + Vite frontend for the DevMind AI knowledge base backend.

## Features

- Login and registration
- Knowledge document list and creation
- AI Ask workflow
- Citations and token usage display
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

## Build

```bash
npm run build
```

## Notes

This frontend does not store model API keys. DeepSeek or other provider keys belong only in the backend runtime environment.
