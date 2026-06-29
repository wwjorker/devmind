<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  apiRequest,
  clearToken,
  getToken,
  setToken,
  uploadDocument,
  type AskFeedbackItem,
  type AskLogItem,
  type AskResponse,
  type DocumentItem,
  type EvaluationSummary,
  type LoginResponse,
  type PageResult,
  type RagEvaluationDataset,
  type RagRetrievalEvaluation,
  type UserProfile
} from './api';
import { icons } from './icons';

const token = ref(getToken() || '');
const user = ref<UserProfile | null>(null);
const activeView = ref<'documents' | 'ask' | 'evaluation'>('ask');
const documentsSection = ref<HTMLElement | null>(null);
const askSection = ref<HTMLElement | null>(null);
const evaluationSection = ref<HTMLElement | null>(null);
const documents = ref<DocumentItem[]>([]);
const selectedDocumentId = ref<number | null>(null);
const askResponse = ref<AskResponse | null>(null);
const restoredFromLog = ref(false);
const restoredAskLogStatus = ref<number | null>(null);
const askLogs = ref<AskLogItem[]>([]);
const selectedLogDetail = ref<{
  log: AskLogItem;
  chunks: AskResponse['retrievedChunks'];
  feedback: AskFeedbackItem[];
} | null>(null);
const evaluation = ref<EvaluationSummary | null>(null);
const evaluationDataset = ref<RagEvaluationDataset | null>(null);
const retrievalEvaluation = ref<RagRetrievalEvaluation | null>(null);
const loading = reactive({
  auth: false,
  documents: false,
  createDocument: false,
  importDocument: false,
  ask: false,
  askLogs: false,
  feedback: false,
  evaluation: false,
  logDetail: false
});
const toast = ref('');
const error = ref('');

const authForm = reactive({
  mode: 'login' as 'login' | 'register',
  username: 'testuser',
  password: '123456',
  nickname: '测试用户',
  email: 'testuser@example.com'
});

const documentForm = reactive({
  title: 'Redis 缓存穿透复盘',
  sourceType: 'bug_review',
  tags: 'redis,cache,backend',
  summary: '一篇关于 Redis 缓存穿透的复盘笔记。',
  content: `问题：
当大量请求访问一个不存在的 key 时，请求可能反复绕过 Redis，直接打到 MySQL。

根因：
系统只缓存真实存在的数据，不存在的数据没有缓存，所以每次请求都会成为缓存未命中。

解决方案：
对空值设置较短 TTL 的缓存，提前校验非法参数，并对异常流量增加限流。

面试表达：
缓存穿透和缓存击穿、缓存雪崩不同，核心目标是保护数据库，避免不存在的数据被反复查询。`
});

const importForm = reactive({
  title: '',
  sourceType: 'imported_note',
  tags: 'imported,learning',
  summary: ''
});
const selectedImportFile = ref<File | null>(null);
const importFileInputKey = ref(0);

const askForm = reactive({
  question: '面试中应该如何解释 Redis 缓存穿透？'
});

const feedbackForm = reactive({
  helpful: false,
  reason: '',
  expectedAnswer: '回答应该提到缓存空值、参数校验、限流，以及监控异常缓存未命中率。'
});

const selectedDocument = computed(() =>
  documents.value.find((document) => document.id === selectedDocumentId.value) || documents.value[0] || null
);

const isAuthed = computed(() => Boolean(token.value));
const currentAskState = computed(() => {
  if (!askResponse.value) {
    return '待提问';
  }
  if (restoredAskLogStatus.value === 0) {
    return '失败';
  }
  if (askResponse.value.modelProvider === 'knowledge-base-fallback') {
    return '兜底';
  }
  return '成功';
});

const currentAskStateClass = computed(() => {
  if (restoredAskLogStatus.value === 0) {
    return 'failed';
  }
  if (askResponse.value?.modelProvider === 'knowledge-base-fallback') {
    return 'fallback';
  }
  return askResponse.value ? 'success' : 'ready';
});

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

function setActiveView(view: 'documents' | 'ask' | 'evaluation') {
  activeView.value = view;
  window.requestAnimationFrame(() => {
    const target = {
      documents: documentsSection.value,
      ask: askSection.value,
      evaluation: evaluationSection.value
    }[view];

    target?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
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
      showToast('账号已创建，正在登录...');
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
    showToast('登录成功');
  } catch (err) {
    setError(err instanceof Error ? err.message : '认证失败');
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
  selectedLogDetail.value = null;
  evaluation.value = null;
  evaluationDataset.value = null;
  retrievalEvaluation.value = null;
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
    showToast('已退出登录');
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
    setError(err instanceof Error ? err.message : '加载知识文档失败');
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
    showToast('文档已创建并完成分块');
  } catch (err) {
    setError(err instanceof Error ? err.message : '创建文档失败');
  } finally {
    loading.createDocument = false;
  }
}

function onImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  selectedImportFile.value = input.files?.[0] ?? null;

  if (selectedImportFile.value && !importForm.title) {
    importForm.title = selectedImportFile.value.name.replace(/\.(txt|md|markdown)$/i, '');
  }
}

async function importDocument() {
  if (!selectedImportFile.value) {
    setError('请先选择 .txt 或 .md 文件。');
    return;
  }

  loading.importDocument = true;
  setError('');
  try {
    const formData = new FormData();
    formData.append('file', selectedImportFile.value);
    if (importForm.title.trim()) {
      formData.append('title', importForm.title.trim());
    }
    if (importForm.sourceType.trim()) {
      formData.append('sourceType', importForm.sourceType.trim());
    }
    if (importForm.tags.trim()) {
      formData.append('tags', importForm.tags.trim());
    }
    if (importForm.summary.trim()) {
      formData.append('summary', importForm.summary.trim());
    }

    const document = await uploadDocument(formData);
    selectedDocumentId.value = document.id;
    selectedImportFile.value = null;
    importFileInputKey.value += 1;
    importForm.title = '';
    await loadDocuments();
    showToast('文件已导入并完成分块');
  } catch (err) {
    setError(err instanceof Error ? err.message : '导入文件失败');
  } finally {
    loading.importDocument = false;
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
    showToast('AI 回答已生成');
  } catch (err) {
    setError(err instanceof Error ? err.message : 'AI 问答失败');
  } finally {
    loading.ask = false;
  }
}

async function submitFeedback(helpful: boolean) {
  if (!askResponse.value?.logId) {
    setError('请先完成一次 AI 问答，再提交反馈。');
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
    showToast(helpful ? '已标记为有帮助' : 'Bad case 已保存');
  } catch (err) {
    setError(err instanceof Error ? err.message : '提交反馈失败');
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
    const [summary, dataset, retrieval] = await Promise.all([
      apiRequest<EvaluationSummary>('/api/v1/ai/evaluation/summary?recentLimit=5'),
      apiRequest<RagEvaluationDataset>('/api/v1/ai/evaluation/dataset'),
      apiRequest<RagRetrievalEvaluation>('/api/v1/ai/evaluation/retrieval')
    ]);
    evaluation.value = summary;
    evaluationDataset.value = dataset;
    retrievalEvaluation.value = retrieval;
  } catch {
    evaluation.value = null;
    evaluationDataset.value = null;
    retrievalEvaluation.value = null;
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

function toCitations(chunks: AskResponse['retrievedChunks'], chunkIds: number[]) {
  if (chunks.length > 0) {
    return chunks.map((chunk) => ({
      chunkId: chunk.chunkId,
      documentId: chunk.documentId,
      documentTitle: chunk.documentTitle,
      chunkIndex: chunk.chunkIndex,
      score: chunk.score
    }));
  }

  return chunkIds.map((chunkId) => ({
    chunkId,
    documentId: 0,
    documentTitle: '从问答日志恢复的 chunk id',
    chunkIndex: 0,
    score: 0
  }));
}

async function loadChunksByIds(chunkIds: number[], retrievalKeyword = '') {
  if (chunkIds.length === 0) {
    return [];
  }

  const ids = encodeURIComponent(chunkIds.join(','));
  const keywords = retrievalKeyword ? `&keywords=${encodeURIComponent(retrievalKeyword)}` : '';
  return apiRequest<AskResponse['retrievedChunks']>(`/api/v1/search/chunks/by-ids?ids=${ids}${keywords}`);
}

async function loadFeedbackForLog(logId: number) {
  const page = await apiRequest<PageResult<AskFeedbackItem>>(`/api/v1/ai/ask-feedback?askLogId=${logId}&pageNo=1&pageSize=20`);
  return page.records;
}

async function openLogDetail(log: AskLogItem) {
  loading.logDetail = true;
  setError('');
  const chunkIds = parseChunkIds(log.retrievedChunkIds);

  try {
    const [chunks, feedback] = await Promise.all([
      loadChunksByIds(chunkIds, log.retrievalKeyword).catch(() => []),
      loadFeedbackForLog(log.id).catch(() => [])
    ]);

    selectedLogDetail.value = {
      log,
      chunks,
      feedback
    };
  } catch (err) {
    setError(err instanceof Error ? err.message : '加载问答日志详情失败');
  } finally {
    loading.logDetail = false;
  }
}

async function restoreAskFromLog(log: AskLogItem, notify = true) {
  const chunkIds = parseChunkIds(log.retrievedChunkIds);
  let restoredChunks: AskResponse['retrievedChunks'] = [];

  try {
    restoredChunks = await loadChunksByIds(chunkIds, log.retrievalKeyword);
  } catch {
    restoredChunks = [];
  }

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
    retrievedChunks: restoredChunks,
    citations: toCitations(restoredChunks, chunkIds)
  };
  askForm.question = log.question;
  activeView.value = 'ask';
  restoredFromLog.value = true;
  restoredAskLogStatus.value = log.status;

  if (notify) {
    showToast('已从问答日志恢复回答');
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
      await restoreAskFromLog(page.records[0], false);
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
    return '刚刚';
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
          <span>AI 知识库</span>
        </div>
      </div>

      <nav class="nav-list" aria-label="Primary">
        <button :class="{ active: activeView === 'documents' }" @click="setActiveView('documents')">
          <span v-html="icons.documents"></span>
          知识文档
        </button>
        <button :class="{ active: activeView === 'ask' }" @click="setActiveView('ask')">
          <span v-html="icons.ask"></span>
          AI 问答
        </button>
        <button :class="{ active: activeView === 'evaluation' }" @click="setActiveView('evaluation')">
          <span v-html="icons.chart"></span>
          评估看板
        </button>
      </nav>

      <div class="sidebar-note">
        <span>后端服务</span>
        <strong>localhost:8081</strong>
      </div>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">开发者学习工作台</p>
          <h1>开发学习知识库、AI 问答与反馈评估</h1>
        </div>
        <div class="topbar-actions">
          <div v-if="user" class="user-chip">
            <span>{{ user.nickname?.slice(0, 1) || user.username.slice(0, 1) }}</span>
            <div>
              <strong>{{ user.nickname || user.username }}</strong>
              <small>{{ user.email }}</small>
            </div>
          </div>
          <button v-if="isAuthed" class="icon-button" title="刷新全部" @click="refreshAll">
            <span v-html="icons.refresh"></span>
          </button>
          <button v-if="isAuthed" class="icon-button" title="退出登录" @click="logout">
            <span v-html="icons.logout"></span>
          </button>
        </div>
      </header>

      <section v-if="!isAuthed" class="auth-panel">
        <div class="auth-copy">
          <h2>连接 DevMind 后端</h2>
          <p>使用本地账号体验知识文档管理、AI 问答日志和 bad case 反馈闭环。</p>
        </div>
        <form class="auth-form" @submit.prevent="login">
          <div class="segmented">
            <button type="button" :class="{ active: authForm.mode === 'login' }" @click="authForm.mode = 'login'">登录</button>
            <button type="button" :class="{ active: authForm.mode === 'register' }" @click="authForm.mode = 'register'">注册</button>
          </div>
          <label>
            用户名
            <input v-model="authForm.username" autocomplete="username" />
          </label>
          <label>
            密码
            <input v-model="authForm.password" type="password" autocomplete="current-password" />
          </label>
          <template v-if="authForm.mode === 'register'">
            <label>
              昵称
              <input v-model="authForm.nickname" />
            </label>
            <label>
              邮箱
              <input v-model="authForm.email" type="email" />
            </label>
          </template>
          <button class="primary-button" type="submit" :disabled="loading.auth">
            {{ loading.auth ? '处理中...' : authForm.mode === 'login' ? '登录' : '创建账号' }}
          </button>
        </form>
      </section>

      <template v-else>
        <section class="status-grid">
          <div class="metric">
            <span>知识文档</span>
            <strong>{{ documents.length }}</strong>
          </div>
          <div class="metric">
            <span>问答状态</span>
            <strong>{{ currentAskState }}</strong>
          </div>
          <div class="metric">
            <span>召回片段</span>
            <strong>{{ askResponse?.retrievedChunks?.length ?? 0 }}</strong>
          </div>
          <div class="metric">
            <span>问答日志</span>
            <strong>{{ askLogs.length }}</strong>
          </div>
        </section>

        <section class="main-grid">
          <div ref="documentsSection" class="panel document-panel">
            <div class="panel-header">
              <div>
                <h2>知识文档</h2>
                <p>用于检索召回和答案引用的学习材料。</p>
              </div>
              <button class="icon-button" title="刷新知识文档" @click="loadDocuments">
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
                <div v-if="!loading.documents && documents.length === 0" class="empty-state">还没有知识文档。先创建或导入一篇笔记来测试检索。</div>
            </div>

            <form class="import-form" @submit.prevent="importDocument">
              <div class="import-header">
                <div>
                  <h3>导入笔记文件</h3>
                  <p>上传 Markdown 或 TXT 笔记，后端会创建知识文档并自动生成 chunks。</p>
                </div>
              </div>
              <div class="field-group">
                <span>文件</span>
                <label class="import-file-picker">
                  <input :key="importFileInputKey" type="file" accept=".md,.markdown,.txt" @change="onImportFileChange" />
                  <span class="file-button">选择文件</span>
                  <span class="file-name">{{ selectedImportFile?.name || '未选择文件' }}</span>
                </label>
              </div>
              <div class="form-row">
                <label>
                  标题
                  <input v-model="importForm.title" placeholder="默认使用文件名" />
                </label>
                <label>
                  类型
                  <input v-model="importForm.sourceType" />
                </label>
              </div>
              <label>
                标签
                <input v-model="importForm.tags" />
              </label>
              <label>
                摘要
                <input v-model="importForm.summary" placeholder="可选：导入笔记摘要" />
              </label>
              <button class="secondary-button" type="submit" :disabled="loading.importDocument">
                <span v-html="icons.plus"></span>
                {{ loading.importDocument ? '导入中...' : '导入文件' }}
              </button>
            </form>

            <form class="document-form" @submit.prevent="createDocument">
              <div class="form-row">
                <label>
                  标题
                  <input v-model="documentForm.title" />
                </label>
                <label>
                  类型
                  <input v-model="documentForm.sourceType" />
                </label>
              </div>
              <label>
                标签
                <input v-model="documentForm.tags" />
              </label>
              <label>
                内容
                <textarea v-model="documentForm.content" rows="7"></textarea>
              </label>
              <button class="secondary-button" type="submit" :disabled="loading.createDocument">
                <span v-html="icons.plus"></span>
                {{ loading.createDocument ? '创建中...' : '创建文档' }}
              </button>
            </form>
          </div>

          <div ref="askSection" class="panel ask-panel">
            <div class="panel-header">
              <div>
                <h2>AI 问答</h2>
                <p>基于检索召回的知识片段回答问题。</p>
              </div>
            </div>

            <form class="ask-form" @submit.prevent="ask">
              <textarea v-model="askForm.question" rows="4"></textarea>
              <button class="primary-button" type="submit" :disabled="loading.ask">
                <span v-html="icons.send"></span>
                {{ loading.ask ? '生成中...' : '询问 DevMind' }}
              </button>
            </form>

            <div v-if="askResponse" class="answer-card">
              <div class="answer-meta">
                <span :class="['state-pill', currentAskStateClass]">{{ currentAskState }}</span>
                <span>{{ askResponse.mock ? 'Mock/本地' : '真实模型' }}</span>
                <span>{{ askResponse.modelProvider }}</span>
                <span>检索词: {{ askResponse.retrievalKeyword }}</span>
                <span>logId: {{ askResponse.logId }}</span>
                <span v-if="restoredFromLog" class="restored-pill">从日志恢复</span>
              </div>
              <pre>{{ askResponse.answer }}</pre>

              <div class="citation-list">
                <h3>引用来源</h3>
                <div v-for="citation in askResponse.citations" :key="citation.chunkId" class="citation">
                  <strong>#{{ citation.chunkId }}</strong>
                  <span>{{ citation.documentTitle }}</span>
                  <small>分数 {{ citation.score }}</small>
                </div>
                <div v-if="askResponse.citations.length === 0" class="empty-state compact">
                  {{ restoredFromLog ? '这条历史日志没有保存引用 id。' : '没有引用来源。无上下文兜底时这是正常情况。' }}
                </div>
              </div>

              <div class="token-strip">
                <span>提示词 token {{ askResponse.promptTokens ?? '-' }}</span>
                <span>回答 token {{ askResponse.completionTokens ?? '-' }}</span>
                <span>总 token {{ askResponse.totalTokens ?? '-' }}</span>
                <span>召回片段 {{ askResponse.retrievedChunks?.length ?? 0 }}</span>
              </div>

              <details class="debug-details">
                <summary>提示词预览与召回片段</summary>
                <pre>{{ askResponse.promptPreview }}</pre>
                <div class="chunk-list">
                  <div v-for="chunk in askResponse.retrievedChunks" :key="chunk.chunkId" class="chunk-row">
                    <strong>#{{ chunk.chunkId }} - {{ chunk.documentTitle }}</strong>
                    <span>{{ chunk.content }}</span>
                    <small>分数 {{ chunk.score }} - token {{ chunk.tokenCount }}</small>
                  </div>
                  <div v-if="askResponse.retrievedChunks.length === 0" class="empty-state compact">
                    {{
                      restoredFromLog
                        ? '这个回答来自历史问答日志。只有保存的 chunk ids 仍然指向 active chunks 时，前端才能重新加载片段内容。'
                        : '检索返回 0 个召回片段，所以后端跳过了模型调用。'
                    }}
                  </div>
                </div>
              </details>

              <div class="feedback-box">
                <textarea
                  v-model="feedbackForm.reason"
                  rows="2"
                  placeholder="如果回答不理想，可以记录原因；保存 bad case 后会进入评估闭环。"
                ></textarea>
                <div class="feedback-actions">
                  <button class="secondary-button" :disabled="loading.feedback" @click="submitFeedback(true)">有帮助</button>
                  <button class="danger-button" :disabled="loading.feedback" @click="submitFeedback(false)">保存 bad case</button>
                </div>
              </div>
            </div>
            <div v-else class="empty-answer">提出一个问题后，这里会展示回答、引用来源、token 用量和反馈控件。</div>
          </div>
        </section>

        <section ref="evaluationSection" class="panel evaluation-panel">
          <div class="panel-header">
            <div>
              <h2>评估看板</h2>
              <p>用于持续改进 RAG 效果的 bad case 反馈闭环。</p>
            </div>
            <button class="icon-button" title="刷新评估数据" @click="loadEvaluation">
              <span v-html="icons.refresh"></span>
            </button>
          </div>
          <div class="badcase-list">
            <div v-for="badCase in evaluation?.recentBadCases || []" :key="badCase.feedbackId" class="badcase-row">
              <strong>{{ badCase.question || '未知问题' }}</strong>
              <span>{{ badCase.reason || '未填写原因' }}</span>
              <small>{{ formatDate(badCase.createdAt) }}</small>
            </div>
            <div v-if="!evaluation?.recentBadCases?.length" class="empty-state">暂无 bad case。</div>
          </div>

          <div class="evaluation-dataset">
            <div class="dataset-header">
              <div>
                <h3>检索评估</h3>
                <p>用标准问题直接跑检索，检查召回是否命中人工标注的相关文档。</p>
              </div>
              <div class="dataset-score">
                <strong>{{ retrievalEvaluation?.passedCaseCount ?? 0 }}/{{ retrievalEvaluation?.totalCaseCount ?? 0 }}</strong>
                <span>通过率 {{ Math.round((retrievalEvaluation?.passRate ?? 0) * 100) }}%</span>
                <span>Hit@{{ retrievalEvaluation?.evaluationK ?? 3 }} {{ Math.round((retrievalEvaluation?.hitAtK ?? 0) * 100) }}%</span>
                <span>MRR {{ (retrievalEvaluation?.mrr ?? 0).toFixed(3) }}</span>
                <span>候选池 Top {{ retrievalEvaluation?.retrievalLimit ?? 5 }}</span>
                <span>策略 {{ retrievalEvaluation?.retrievalStrategy || 'keyword-baseline' }}</span>
                <span>相关性 {{ retrievalEvaluation?.relevanceMode || 'gold-document-title' }}</span>
              </div>
            </div>

            <div class="evaluation-case-list">
              <div
                v-for="testCase in retrievalEvaluation?.cases || []"
                :key="`retrieval-${testCase.caseId}`"
                class="evaluation-case-row retrieval-case-row"
              >
                <div>
                  <strong>{{ testCase.question }}</strong>
                  <span>{{ testCase.caseId }} - {{ testCase.category }} - {{ testCase.riskType }}</span>
                </div>
                <p v-if="testCase.relevantDocumentTitles.length">
                  Gold 文档：{{ testCase.relevantDocumentTitles.join('、') }}
                </p>
                <div class="case-keywords">
                  <span v-for="keyword in testCase.queryKeywords" :key="`${testCase.caseId}-query-${keyword}`">
                    检索词: {{ keyword }}
                  </span>
                  <span
                    v-for="keyword in testCase.matchedExpectedKeywords"
                    :key="`${testCase.caseId}-matched-${keyword}`"
                    class="matched-keyword"
                  >
                    命中: {{ keyword }}
                  </span>
                </div>
                <div class="case-status">
                  <span :class="['status-badge', testCase.passed ? 'success' : 'failed']">
                    {{ testCase.passed ? '通过' : '需复查' }}
                  </span>
                  <small>{{ testCase.retrievedChunkCount }} 个片段</small>
                  <small v-if="!testCase.expectedNoContext">
                    {{ testCase.hitAtK ? `Hit@${retrievalEvaluation?.evaluationK ?? 3}` : `未进 Top ${retrievalEvaluation?.evaluationK ?? 3}` }}
                  </small>
                  <small v-if="testCase.firstRelevantRank">首个相关排名 #{{ testCase.firstRelevantRank }}</small>
                  <small v-if="testCase.reciprocalRank !== null">RR {{ testCase.reciprocalRank.toFixed(3) }}</small>
                </div>
                <p>{{ testCase.note }}</p>
                <p v-if="testCase.topDocumentTitles.length">Top 文档：{{ testCase.topDocumentTitles.join('、') }}</p>
                <p v-if="testCase.missingExpectedKeywords.length">
                  缺失关键词：{{ testCase.missingExpectedKeywords.join('、') }}
                </p>
              </div>
              <div v-if="!retrievalEvaluation?.cases?.length" class="empty-state">检索评估暂未加载。</div>
            </div>
          </div>

          <div class="evaluation-dataset">
            <div class="dataset-header">
              <div>
                <h3>RAG 评估集</h3>
                <p>用标准问题检查检索覆盖率、Hit@3、MRR 和无上下文兜底效果。</p>
              </div>
              <div class="dataset-score">
                <strong>{{ evaluationDataset?.coveredCaseCount ?? 0 }}/{{ evaluationDataset?.totalCaseCount ?? 0 }}</strong>
                <span>覆盖率 {{ Math.round((evaluationDataset?.coverageRate ?? 0) * 100) }}%</span>
              </div>
            </div>

            <div class="evaluation-case-list">
              <div v-for="testCase in evaluationDataset?.cases || []" :key="testCase.caseId" class="evaluation-case-row">
                <div>
                  <strong>{{ testCase.question }}</strong>
                  <span>{{ testCase.caseId }} - {{ testCase.category }} - {{ testCase.riskType }}</span>
                </div>
                <div class="case-keywords">
                  <span v-for="keyword in testCase.expectedKeywords" :key="`${testCase.caseId}-${keyword}`">{{ keyword }}</span>
                </div>
                <div class="case-status">
                  <span :class="['status-badge', testCase.covered ? 'success' : 'failed']">
                    {{ testCase.covered ? '已覆盖' : '未运行' }}
                  </span>
                  <small v-if="testCase.covered">
                    log #{{ testCase.lastAskLogId }} - {{ testCase.lastRetrievedChunkCount }} 个片段
                  </small>
                  <small v-else>询问这个问题即可覆盖该 case</small>
                </div>
                <p>{{ testCase.expectedAnswer }}</p>
              </div>
              <div v-if="!evaluationDataset?.cases?.length" class="empty-state">评估 case 暂未加载。</div>
            </div>
          </div>
        </section>

        <section class="panel logs-panel">
          <div class="panel-header">
            <div>
              <h2>问答日志</h2>
              <p>后端记录的成功、兜底和失败问答请求。</p>
            </div>
            <button class="icon-button" title="刷新问答日志" @click="loadAskLogs()">
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
                  {{ log.status === 1 ? '成功' : '失败' }}
                </span>
                <span>{{ log.modelProvider }}</span>
                <span>{{ log.retrievedChunkCount }} 个片段</span>
                <span>{{ log.elapsedMs }}ms</span>
                <span>{{ formatDate(log.createdAt) }}</span>
                <button class="mini-button" type="button" :disabled="loading.logDetail" @click="openLogDetail(log)">详情</button>
                <button class="mini-button" type="button" @click="restoreAskFromLog(log)">恢复</button>
              </div>
            </div>
            <div v-if="!askLogs.length" class="empty-state">暂无问答日志。</div>
          </div>

          <div v-if="selectedLogDetail" class="log-detail-panel">
            <div class="log-detail-header">
              <div>
                <span>Ask log #{{ selectedLogDetail.log.id }}</span>
                <strong>{{ selectedLogDetail.log.question }}</strong>
              </div>
              <button class="mini-button" type="button" @click="selectedLogDetail = null">关闭</button>
            </div>

            <div class="answer-meta">
              <span :class="['state-pill', selectedLogDetail.log.status === 1 ? 'success' : 'failed']">
                {{ selectedLogDetail.log.status === 1 ? '成功' : '失败' }}
              </span>
              <span>{{ selectedLogDetail.log.mock ? 'Mock/本地' : '真实模型' }}</span>
              <span>{{ selectedLogDetail.log.modelProvider }}</span>
              <span>检索词: {{ selectedLogDetail.log.retrievalKeyword }}</span>
              <span>召回片段 {{ selectedLogDetail.log.retrievedChunkCount }}</span>
              <span>{{ selectedLogDetail.log.elapsedMs }}ms</span>
            </div>

            <div class="log-detail-grid">
              <section>
                <h3>回答</h3>
                <pre>{{ selectedLogDetail.log.answer }}</pre>
              </section>
              <section>
                <h3>提示词预览</h3>
                <pre>{{ selectedLogDetail.log.promptPreview || '没有保存 prompt preview。' }}</pre>
              </section>
            </div>

            <div class="token-strip">
              <span>提示词 token {{ selectedLogDetail.log.promptTokens ?? '-' }}</span>
              <span>回答 token {{ selectedLogDetail.log.completionTokens ?? '-' }}</span>
              <span>总 token {{ selectedLogDetail.log.totalTokens ?? '-' }}</span>
              <span>片段 ids {{ selectedLogDetail.log.retrievedChunkIds || '-' }}</span>
            </div>

            <div class="chunk-list">
              <h3>召回片段</h3>
              <div v-for="chunk in selectedLogDetail.chunks" :key="chunk.chunkId" class="chunk-row">
                <strong>#{{ chunk.chunkId }} - {{ chunk.documentTitle }}</strong>
                <span>{{ chunk.content }}</span>
                <small>分数 {{ chunk.score }} - token {{ chunk.tokenCount }}</small>
              </div>
              <div v-if="selectedLogDetail.chunks.length === 0" class="empty-state compact">
                没有找到这些片段 id 对应的 active chunk 文本。
              </div>
            </div>

            <div class="feedback-list">
              <h3>反馈</h3>
              <div v-for="feedback in selectedLogDetail.feedback" :key="feedback.id" class="feedback-row">
                <strong>{{ feedback.helpful ? '有帮助' : 'Bad case' }}</strong>
                <span>{{ feedback.reason || '未填写原因' }}</span>
                <small>{{ formatDate(feedback.createdAt) }}</small>
              </div>
              <div v-if="selectedLogDetail.feedback.length === 0" class="empty-state compact">
                这条问答日志暂无反馈。
              </div>
            </div>
          </div>
        </section>
      </template>

      <div v-if="toast" class="toast">{{ toast }}</div>
      <div v-if="error" class="error-banner">{{ error }}</div>
    </main>
  </div>
</template>
