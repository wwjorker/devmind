<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  apiRequest,
  clearToken,
  getToken,
  setToken,
  type AskLogItem,
  type AskResponse,
  type DocumentItem,
  type EvaluationSummary,
  type LoginResponse,
  type PageResult,
  type UserProfile
} from './api';
import { icons } from './icons';

const token = ref(getToken() || '');
const user = ref<UserProfile | null>(null);
const activeView = ref<'documents' | 'ask' | 'evaluation'>('ask');
const documents = ref<DocumentItem[]>([]);
const selectedDocumentId = ref<number | null>(null);
const askResponse = ref<AskResponse | null>(null);
const restoredFromLog = ref(false);
const restoredAskLogStatus = ref<number | null>(null);
const askLogs = ref<AskLogItem[]>([]);
const evaluation = ref<EvaluationSummary | null>(null);
const loading = reactive({
  auth: false,
  documents: false,
  createDocument: false,
  ask: false,
  askLogs: false,
  feedback: false,
  evaluation: false
});
const toast = ref('');
const error = ref('');

const authForm = reactive({
  mode: 'login' as 'login' | 'register',
  username: 'testuser',
  password: '123456',
  nickname: 'Test User',
  email: 'testuser@example.com'
});

const documentForm = reactive({
  title: 'Redis cache penetration review',
  sourceType: 'bug_review',
  tags: 'redis,cache,backend',
  summary: 'A short review note about cache penetration.',
  content: `Problem:
The API may hit the database repeatedly when a missing key is requested many times.

Root cause:
The system only caches existing data, so non-existing data bypasses Redis every time.

Solution:
Cache empty values for a short TTL, validate illegal parameters early, and add rate limiting for abnormal traffic.

Interview talking point:
Cache penetration is different from cache breakdown and cache avalanche.`
});

const askForm = reactive({
  question: 'How should I explain Redis cache penetration in an interview?'
});

const feedbackForm = reactive({
  helpful: false,
  reason: 'The answer is acceptable, but this test marks it as a bad case for evaluation.',
  expectedAnswer: 'The answer should mention empty-value caching, parameter validation, rate limiting, and monitoring miss rates.'
});

const selectedDocument = computed(() =>
  documents.value.find((document) => document.id === selectedDocumentId.value) || documents.value[0] || null
);

const isAuthed = computed(() => Boolean(token.value));
const currentAskState = computed(() => {
  if (!askResponse.value) {
    return 'Ready';
  }
  if (restoredAskLogStatus.value === 0) {
    return 'Failed';
  }
  if (askResponse.value.modelProvider === 'knowledge-base-fallback') {
    return 'Fallback';
  }
  return 'Success';
});

const currentAskStateClass = computed(() => currentAskState.value.toLowerCase());

function showToast(message: string) {
  toast.value = message;
  window.setTimeout(() => {
    if (toast.value === message) {
      toast.value = '';
    }
  }, 2800);
}

function setError(message: string) {
  error.value = message;
}

async function login() {
  loading.auth = true;
  setError('');
  try {
    if (authForm.mode === 'register') {
      await apiRequest<UserProfile>('/api/v1/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          username: authForm.username,
          password: authForm.password,
          nickname: authForm.nickname,
          email: authForm.email
        })
      });
      showToast('Account created. Signing in...');
    }

    const loginData = await apiRequest<LoginResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        username: authForm.username,
        password: authForm.password
      })
    });
    token.value = loginData.token;
    setToken(loginData.token);
    await loadCurrentUser();
    await Promise.all([loadDocuments(), loadEvaluation(), loadAskLogs()]);
    showToast('Signed in');
  } catch (err) {
    setError(err instanceof Error ? err.message : 'Authentication failed');
  } finally {
    loading.auth = false;
  }
}

async function loadCurrentUser() {
  user.value = await apiRequest<UserProfile>('/api/v1/auth/me');
}

function clearLocalSession() {
  clearToken();
  token.value = '';
  user.value = null;
  documents.value = [];
  askResponse.value = null;
  restoredFromLog.value = false;
  restoredAskLogStatus.value = null;
  askLogs.value = [];
  evaluation.value = null;
}

async function logout() {
  try {
    if (token.value) {
      await apiRequest('/api/v1/auth/logout', { method: 'POST' });
    }
  } catch {
    // Local cleanup should still happen even if Redis or the backend is temporarily unavailable.
  } finally {
    clearLocalSession();
    showToast('Signed out');
  }
}

async function loadDocuments() {
  loading.documents = true;
  setError('');
  try {
    const page = await apiRequest<PageResult<DocumentItem>>('/api/v1/documents?pageNo=1&pageSize=20');
    documents.value = page.records;
    selectedDocumentId.value = page.records[0]?.id ?? null;
  } catch (err) {
    setError(err instanceof Error ? err.message : 'Failed to load documents');
  } finally {
    loading.documents = false;
  }
}

async function createDocument() {
  loading.createDocument = true;
  setError('');
  try {
    const document = await apiRequest<DocumentItem>('/api/v1/documents', {
      method: 'POST',
      body: JSON.stringify(documentForm)
    });
    selectedDocumentId.value = document.id;
    await loadDocuments();
    showToast('Document created and chunked');
  } catch (err) {
    setError(err instanceof Error ? err.message : 'Failed to create document');
  } finally {
    loading.createDocument = false;
  }
}

async function ask() {
  loading.ask = true;
  setError('');
  try {
    askResponse.value = await apiRequest<AskResponse>('/api/v1/ai/ask', {
      method: 'POST',
      body: JSON.stringify({ question: askForm.question })
    });
    restoredFromLog.value = false;
    restoredAskLogStatus.value = null;
    activeView.value = 'ask';
    await Promise.all([loadEvaluation(), loadAskLogs()]);
    showToast('AI answer generated');
  } catch (err) {
    setError(err instanceof Error ? err.message : 'Failed to ask AI');
  } finally {
    loading.ask = false;
  }
}

async function submitFeedback(helpful: boolean) {
  if (!askResponse.value?.logId) {
    setError('Ask AI first, then submit feedback.');
    return;
  }
  loading.feedback = true;
  setError('');
  try {
    await apiRequest(`/api/v1/ai/ask-logs/${askResponse.value.logId}/feedback`, {
      method: 'POST',
      body: JSON.stringify({
        helpful,
        reason: feedbackForm.reason,
        expectedAnswer: feedbackForm.expectedAnswer
      })
    });
    await loadEvaluation();
    showToast(helpful ? 'Marked as helpful' : 'Bad case saved');
  } catch (err) {
    setError(err instanceof Error ? err.message : 'Failed to submit feedback');
  } finally {
    loading.feedback = false;
  }
}

async function loadEvaluation() {
  if (!isAuthed.value) {
    return;
  }
  loading.evaluation = true;
  try {
    evaluation.value = await apiRequest<EvaluationSummary>('/api/v1/ai/evaluation/summary?recentLimit=5');
  } catch {
    evaluation.value = null;
  } finally {
    loading.evaluation = false;
  }
}

function parseChunkIds(value: string | null) {
  if (!value) {
    return [];
  }
  return value
    .split(',')
    .map((chunkId) => Number(chunkId.trim()))
    .filter((chunkId) => Number.isFinite(chunkId));
}

function restoreAskFromLog(log: AskLogItem, notify = true) {
  const chunkIds = parseChunkIds(log.retrievedChunkIds);

  askResponse.value = {
    logId: log.id,
    question: log.question,
    retrievalKeyword: log.retrievalKeyword,
    answer: log.answer,
    modelProvider: log.modelProvider,
    mock: log.mock,
    promptPreview: log.promptPreview || '',
    promptTokens: log.promptTokens,
    completionTokens: log.completionTokens,
    totalTokens: log.totalTokens,
    retrievedChunks: [],
    citations: chunkIds.map((chunkId) => ({
      chunkId,
      documentId: 0,
      documentTitle: 'Restored chunk id from ask log',
      chunkIndex: 0,
      score: 0
    }))
  };
  askForm.question = log.question;
  activeView.value = 'ask';
  restoredFromLog.value = true;
  restoredAskLogStatus.value = log.status;

  if (notify) {
    showToast('Answer restored from ask log');
  }
}

async function loadAskLogs(restoreLatest = false) {
  if (!isAuthed.value) {
    return;
  }
  loading.askLogs = true;
  try {
    const page = await apiRequest<PageResult<AskLogItem>>('/api/v1/ai/ask-logs?pageNo=1&pageSize=6');
    askLogs.value = page.records;
    if (restoreLatest && !askResponse.value && page.records.length > 0) {
      restoreAskFromLog(page.records[0], false);
    }
  } catch {
    askLogs.value = [];
  } finally {
    loading.askLogs = false;
  }
}

async function refreshAll() {
  await Promise.all([loadDocuments(), loadEvaluation(), loadAskLogs(!askResponse.value)]);
}

function formatDate(value: string | null) {
  if (!value) {
    return 'Just now';
  }
  return value.replace('T', ' ').slice(0, 16);
}

onMounted(async () => {
  if (!token.value) {
    return;
  }
  try {
    await loadCurrentUser();
    await Promise.all([loadDocuments(), loadEvaluation(), loadAskLogs(true)]);
  } catch {
    clearLocalSession();
  }
});
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">D</div>
        <div>
          <strong>DevMind</strong>
          <span>AI Knowledge Base</span>
        </div>
      </div>

      <nav class="nav-list" aria-label="Primary">
        <button :class="{ active: activeView === 'documents' }" @click="activeView = 'documents'">
          <span v-html="icons.documents"></span>
          Documents
        </button>
        <button :class="{ active: activeView === 'ask' }" @click="activeView = 'ask'">
          <span v-html="icons.ask"></span>
          AI Ask
        </button>
        <button :class="{ active: activeView === 'evaluation' }" @click="activeView = 'evaluation'">
          <span v-html="icons.chart"></span>
          Evaluation
        </button>
      </nav>

      <div class="sidebar-note">
        <span>Backend</span>
        <strong>localhost:8081</strong>
      </div>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">Developer learning workspace</p>
          <h1>RAG notes, answers, and feedback in one place</h1>
        </div>
        <div class="topbar-actions">
          <div v-if="user" class="user-chip">
            <span>{{ user.nickname?.slice(0, 1) || user.username.slice(0, 1) }}</span>
            <div>
              <strong>{{ user.nickname || user.username }}</strong>
              <small>{{ user.email }}</small>
            </div>
          </div>
          <button v-if="isAuthed" class="icon-button" title="Refresh" @click="refreshAll">
            <span v-html="icons.refresh"></span>
          </button>
          <button v-if="isAuthed" class="icon-button" title="Sign out" @click="logout">
            <span v-html="icons.logout"></span>
          </button>
        </div>
      </header>

      <section v-if="!isAuthed" class="auth-panel">
        <div class="auth-copy">
          <h2>Connect to your DevMind backend</h2>
          <p>Use a local account to test document management, AI ask logs, and bad-case feedback.</p>
        </div>
        <form class="auth-form" @submit.prevent="login">
          <div class="segmented">
            <button type="button" :class="{ active: authForm.mode === 'login' }" @click="authForm.mode = 'login'">Login</button>
            <button type="button" :class="{ active: authForm.mode === 'register' }" @click="authForm.mode = 'register'">Register</button>
          </div>
          <label>
            Username
            <input v-model="authForm.username" autocomplete="username" />
          </label>
          <label>
            Password
            <input v-model="authForm.password" type="password" autocomplete="current-password" />
          </label>
          <template v-if="authForm.mode === 'register'">
            <label>
              Nickname
              <input v-model="authForm.nickname" />
            </label>
            <label>
              Email
              <input v-model="authForm.email" type="email" />
            </label>
          </template>
          <button class="primary-button" type="submit" :disabled="loading.auth">
            {{ loading.auth ? 'Working...' : authForm.mode === 'login' ? 'Sign in' : 'Create account' }}
          </button>
        </form>
      </section>

      <template v-else>
        <section class="status-grid">
          <div class="metric">
            <span>Documents</span>
            <strong>{{ documents.length }}</strong>
          </div>
          <div class="metric">
            <span>Ask state</span>
            <strong>{{ currentAskState }}</strong>
          </div>
          <div class="metric">
            <span>Retrieved chunks</span>
            <strong>{{ askResponse?.retrievedChunks?.length ?? 0 }}</strong>
          </div>
          <div class="metric">
            <span>Ask logs</span>
            <strong>{{ askLogs.length }}</strong>
          </div>
        </section>

        <section class="main-grid">
          <div class="panel document-panel">
            <div class="panel-header">
              <div>
                <h2>Knowledge documents</h2>
                <p>Source material for retrieval and citations.</p>
              </div>
              <button class="icon-button" title="Reload documents" @click="loadDocuments">
                <span v-html="icons.refresh"></span>
              </button>
            </div>

            <div class="document-list">
              <button
                v-for="document in documents"
                :key="document.id"
                :class="{ selected: document.id === selectedDocument?.id }"
                @click="selectedDocumentId = document.id"
              >
                <strong>{{ document.title }}</strong>
                <span>{{ document.sourceType }} - {{ document.tags }}</span>
                <small>{{ formatDate(document.updatedAt || document.createdAt) }}</small>
              </button>
              <div v-if="!loading.documents && documents.length === 0" class="empty-state">No documents yet. Create one to test retrieval.</div>
            </div>

            <form class="document-form" @submit.prevent="createDocument">
              <div class="form-row">
                <label>
                  Title
                  <input v-model="documentForm.title" />
                </label>
                <label>
                  Type
                  <input v-model="documentForm.sourceType" />
                </label>
              </div>
              <label>
                Tags
                <input v-model="documentForm.tags" />
              </label>
              <label>
                Content
                <textarea v-model="documentForm.content" rows="7"></textarea>
              </label>
              <button class="secondary-button" type="submit" :disabled="loading.createDocument">
                <span v-html="icons.plus"></span>
                {{ loading.createDocument ? 'Creating...' : 'Create document' }}
              </button>
            </form>
          </div>

          <div class="panel ask-panel">
            <div class="panel-header">
              <div>
                <h2>AI Ask</h2>
                <p>Ask against retrieved knowledge chunks.</p>
              </div>
            </div>

            <form class="ask-form" @submit.prevent="ask">
              <textarea v-model="askForm.question" rows="4"></textarea>
              <button class="primary-button" type="submit" :disabled="loading.ask">
                <span v-html="icons.send"></span>
                {{ loading.ask ? 'Generating...' : 'Ask DevMind' }}
              </button>
            </form>

            <div v-if="askResponse" class="answer-card">
              <div class="answer-meta">
                <span :class="['state-pill', currentAskStateClass]">{{ currentAskState }}</span>
                <span>{{ askResponse.mock ? 'Mock/local' : 'Real model' }}</span>
                <span>{{ askResponse.modelProvider }}</span>
                <span>keyword: {{ askResponse.retrievalKeyword }}</span>
                <span>logId: {{ askResponse.logId }}</span>
                <span v-if="restoredFromLog" class="restored-pill">Restored from log</span>
              </div>
              <pre>{{ askResponse.answer }}</pre>

              <div class="citation-list">
                <h3>Citations</h3>
                <div v-for="citation in askResponse.citations" :key="citation.chunkId" class="citation">
                  <strong>#{{ citation.chunkId }}</strong>
                  <span>{{ citation.documentTitle }}</span>
                  <small>score {{ citation.score }}</small>
                </div>
                <div v-if="askResponse.citations.length === 0" class="empty-state compact">
                  {{ restoredFromLog ? 'This historical log has no stored citation ids.' : 'No citations. This is expected for no-context fallback.' }}
                </div>
              </div>

              <div class="token-strip">
                <span>Prompt {{ askResponse.promptTokens ?? '-' }}</span>
                <span>Completion {{ askResponse.completionTokens ?? '-' }}</span>
                <span>Total {{ askResponse.totalTokens ?? '-' }}</span>
                <span>Chunks {{ askResponse.retrievedChunks?.length ?? 0 }}</span>
              </div>

              <details class="debug-details">
                <summary>Prompt preview and retrieved chunks</summary>
                <pre>{{ askResponse.promptPreview }}</pre>
                <div class="chunk-list">
                  <div v-for="chunk in askResponse.retrievedChunks" :key="chunk.chunkId" class="chunk-row">
                    <strong>#{{ chunk.chunkId }} - {{ chunk.documentTitle }}</strong>
                    <span>{{ chunk.content }}</span>
                    <small>score {{ chunk.score }} - tokens {{ chunk.tokenCount }}</small>
                  </div>
                  <div v-if="askResponse.retrievedChunks.length === 0" class="empty-state compact">
                    {{
                      restoredFromLog
                        ? 'This answer was restored from an ask log. The log keeps the answer, prompt, tokens, and chunk ids, but not full chunk text.'
                        : 'Retrieval returned 0 chunks, so the backend skipped the LLM provider.'
                    }}
                  </div>
                </div>
              </details>

              <div class="feedback-box">
                <textarea v-model="feedbackForm.reason" rows="2"></textarea>
                <div class="feedback-actions">
                  <button class="secondary-button" :disabled="loading.feedback" @click="submitFeedback(true)">Helpful</button>
                  <button class="danger-button" :disabled="loading.feedback" @click="submitFeedback(false)">Save bad case</button>
                </div>
              </div>
            </div>
            <div v-else class="empty-answer">Ask a question to see answer, citations, token usage, and feedback controls.</div>
          </div>
        </section>

        <section class="panel evaluation-panel">
          <div class="panel-header">
            <div>
              <h2>Evaluation summary</h2>
              <p>Bad-case feedback loop for RAG improvement.</p>
            </div>
            <button class="icon-button" title="Reload evaluation" @click="loadEvaluation">
              <span v-html="icons.refresh"></span>
            </button>
          </div>
          <div class="badcase-list">
            <div v-for="badCase in evaluation?.recentBadCases || []" :key="badCase.feedbackId" class="badcase-row">
              <strong>{{ badCase.question || 'Unknown question' }}</strong>
              <span>{{ badCase.reason || 'No reason provided' }}</span>
              <small>{{ formatDate(badCase.createdAt) }}</small>
            </div>
            <div v-if="!evaluation?.recentBadCases?.length" class="empty-state">No bad cases yet.</div>
          </div>
        </section>

        <section class="panel logs-panel">
          <div class="panel-header">
            <div>
              <h2>Ask logs</h2>
              <p>Success, fallback, and failure records from the backend.</p>
            </div>
            <button class="icon-button" title="Reload ask logs" @click="loadAskLogs()">
              <span v-html="icons.refresh"></span>
            </button>
          </div>
          <div class="log-list">
            <div v-for="log in askLogs" :key="log.id" class="log-row">
              <div>
                <strong>{{ log.question }}</strong>
                <span>{{ log.retrievalKeyword }}</span>
              </div>
              <div class="log-meta">
                <span :class="['status-badge', log.status === 1 ? 'success' : 'failed']">
                  {{ log.status === 1 ? 'success' : 'failed' }}
                </span>
                <span>{{ log.modelProvider }}</span>
                <span>{{ log.retrievedChunkCount }} chunks</span>
                <span>{{ log.elapsedMs }}ms</span>
                <span>{{ formatDate(log.createdAt) }}</span>
                <button class="mini-button" type="button" @click="restoreAskFromLog(log)">Restore</button>
              </div>
            </div>
            <div v-if="!askLogs.length" class="empty-state">No ask logs yet.</div>
          </div>
        </section>
      </template>

      <div v-if="toast" class="toast">{{ toast }}</div>
      <div v-if="error" class="error-banner">{{ error }}</div>
    </main>
  </div>
</template>
