<script setup lang="ts">
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
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
const apiTargetLabel = import.meta.env.VITE_API_TARGET || 'http://localhost:8081';
type AppView = 'documents' | 'ask' | 'evaluation' | 'logs';
const activeView = ref<AppView>('ask');
const documents = ref<DocumentItem[]>([]);
const archivedDocuments = ref<DocumentItem[]>([]);
const documentTotal = ref(0);
const selectedDocumentId = ref<number | null>(null);
const editingDocumentId = ref<number | null>(null);
const askResponse = ref<AskResponse | null>(null);
const restoredFromLog = ref(false);
const restoredAskLogStatus = ref<number | null>(null);
const askLogs = ref<AskLogItem[]>([]);
const askLogPage = ref(1);
const askLogTotal = ref(0);
const askLogPageSize = 10;
const backendStatus = ref<'checking' | 'up' | 'down'>('checking');
const selectedLogDetail = ref<{
  log: AskLogItem;
  chunks: AskResponse['retrievedChunks'];
  feedback: AskFeedbackItem[];
} | null>(null);
const evaluation = ref<EvaluationSummary | null>(null);
const evaluationDataset = ref<RagEvaluationDataset | null>(null);
const retrievalEvaluation = ref<RagRetrievalEvaluation | null>(null);
const lastEvaluationRunAt = ref<string | null>(null);
const askElapsedSeconds = ref(0);
let askTimer: number | null = null;
let sessionGeneration = 0;
let sessionAbortController = new AbortController();
const loading = reactive({
  auth: false,
  initialData: false,
  documents: false,
  createDocument: false,
  updateDocument: false,
  archiveDocument: false,
  restoreDocument: false,
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

const documentEditForm = reactive({
  title: '',
  sourceType: '',
  tags: '',
  summary: '',
  content: ''
});

const importForm = reactive({
  title: '',
  sourceType: 'learning_note',
  tags: '',
  summary: ''
});
const sourceTypeOptions = [
  { value: 'learning_note', label: '学习笔记' },
  { value: 'bug_review', label: '故障复盘' },
  { value: 'architecture_note', label: '架构笔记' },
  { value: 'interview_note', label: '面试笔记' },
  { value: 'evaluation_note', label: '评估资料' },
  { value: 'imported_note', label: '旧版导入笔记', legacy: true }
];
const tagSuggestions = ['java', 'spring', 'mysql', 'redis', 'security', 'rag', 'evaluation', 'interview'];
const documentComposerMode = ref<'import' | 'manual'>('import');
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

const renderedAnswer = computed(() => renderMarkdown(askResponse.value?.answer || ''));
const renderedLogAnswer = computed(() => renderMarkdown(selectedLogDetail.value?.log.answer || ''));
const askProgressText = computed(() => {
  if (!loading.ask) {
    return '';
  }
  if (askElapsedSeconds.value < 2) {
    return `正在检索知识库并准备回答 · ${askElapsedSeconds.value}s`;
  }
  return `正在等待模型生成并整理引用 · ${askElapsedSeconds.value}s`;
});

function renderMarkdown(value: string) {
  if (!value) {
    return '';
  }
  const html = marked.parse(value, {
    async: false,
    breaks: true,
    gfm: true
  }) as string;
  return DOMPurify.sanitize(html);
}

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

function friendlyError(err: unknown, fallback: string) {
  const raw = err instanceof Error ? err.message.trim() : '';
  const normalized = raw.toLowerCase();

  if (normalized.includes('invalid username or password')) {
    return '用户名或密码错误，请检查后重试。';
  }
  if (normalized.includes('username already exists')) {
    return '该用户名已经存在，请直接登录或更换用户名。';
  }
  if (normalized.includes('account is disabled')) {
    return '该账号已被停用。';
  }
  if (normalized.includes('only .txt, .md, and .markdown')) {
    return '仅支持 TXT、MD 或 Markdown 文件。';
  }
  if (normalized.includes('file is required')) {
    return '请先选择要导入的文件。';
  }
  if (normalized.includes('file is too large') || normalized.includes('文件过大')) {
    return '文件过大，请选择不超过 256KB 的 TXT 或 Markdown 文件。';
  }
  if (normalized.includes('request failed: 500') || normalized.includes('failed to fetch')) {
    return '无法连接 DevMind 后端，请确认 8081 端口的后端服务已经启动。';
  }
  if (normalized.includes('external embedding request failed')) {
    return '远程向量服务暂时不可用；本地问答仍可继续，稍后再运行完整评估。';
  }
  if (normalized.includes('deepseek request failed')) {
    return 'DeepSeek 暂时没有成功返回，请稍后重试并查看后端日志。';
  }
  return raw || fallback;
}

function normalizeTags(value: string) {
  return Array.from(new Set(
    value
      .split(/[,，]/)
      .map((tag) => tag.trim())
      .filter(Boolean)
  )).join(',');
}

function sourceTypeLabel(value: string) {
  return sourceTypeOptions.find((option) => option.value === value)?.label || value;
}

function addSuggestedTag(target: 'create' | 'import' | 'edit', tag: string) {
  const form = target === 'create' ? documentForm : target === 'import' ? importForm : documentEditForm;
  form.tags = normalizeTags(`${form.tags},${tag}`);
}

function setActiveView(view: AppView) {
  activeView.value = view;
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function checkBackendHealth() {
  backendStatus.value = 'checking';
  try {
    const health = await apiRequest<{ status: string }>('/api/v1/health');
    backendStatus.value = health.status === 'UP' ? 'up' : 'down';
  } catch {
    backendStatus.value = 'down';
  }
}

function startAskTimer() {
  stopAskTimer();
  askElapsedSeconds.value = 0;
  const startedAt = Date.now();
  askTimer = window.setInterval(() => {
    askElapsedSeconds.value = Math.floor((Date.now() - startedAt) / 1000);
  }, 250);
}

function stopAskTimer() {
  if (askTimer !== null) {
    window.clearInterval(askTimer);
    askTimer = null;
  }
}

function resetSessionRequests() {
  sessionAbortController.abort();
  sessionAbortController = new AbortController();
  sessionGeneration += 1;
}

function currentSession() {
  return {
    generation: sessionGeneration,
    signal: sessionAbortController.signal
  };
}

function isCurrentSession(generation: number) {
  return generation === sessionGeneration && isAuthed.value;
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
    resetSessionRequests();
    await loadCurrentUser(sessionAbortController.signal);
    showToast('登录成功');
    void loadInitialData();
  } catch (err) {
    clearLocalSession();
    setError(friendlyError(err, '认证失败，请稍后重试。'));
  } finally {
    loading.auth = false;
  }
}

async function loadCurrentUser(signal?: AbortSignal) {
  user.value = await apiRequest<UserProfile>('/api/v1/auth/me', { signal });
}

function clearLocalSession() {
  resetSessionRequests();
  clearToken();
  error.value = '';
  token.value = '';
  user.value = null;
  documents.value = [];
  archivedDocuments.value = [];
  documentTotal.value = 0;
  selectedDocumentId.value = null;
  editingDocumentId.value = null;
  askResponse.value = null;
  restoredFromLog.value = false;
  restoredAskLogStatus.value = null;
  askLogs.value = [];
  askLogPage.value = 1;
  askLogTotal.value = 0;
  selectedLogDetail.value = null;
  evaluation.value = null;
  evaluationDataset.value = null;
  retrievalEvaluation.value = null;
  lastEvaluationRunAt.value = null;
  loading.auth = false;
  loading.initialData = false;
  loading.documents = false;
  loading.createDocument = false;
  loading.updateDocument = false;
  loading.archiveDocument = false;
  loading.restoreDocument = false;
  loading.importDocument = false;
  loading.askLogs = false;
  loading.evaluation = false;
  loading.ask = false;
  loading.feedback = false;
  loading.logDetail = false;
  stopAskTimer();
}

function logout() {
  const logoutRequest = token.value
    ? apiRequest('/api/v1/auth/logout', { method: 'POST' }).catch(() => undefined)
    : Promise.resolve();

  clearLocalSession();
  authForm.mode = 'login';
  showToast('已退出登录');
  void logoutRequest;
}

async function loadInitialData() {
  if (!isAuthed.value) {
    return;
  }
  const session = currentSession();
  loading.initialData = true;
  try {
    await Promise.all([
      loadDocuments(session.generation, session.signal),
      loadArchivedDocuments(session.generation, session.signal),
      loadEvaluationOverview(session.generation, session.signal),
      loadAskLogs(true, session.generation, session.signal)
    ]);
  } finally {
    if (isCurrentSession(session.generation)) {
      loading.initialData = false;
    }
  }
}

async function loadArchivedDocuments(
  expectedGeneration = sessionGeneration,
  signal: AbortSignal = sessionAbortController.signal
) {
  try {
    const page = await apiRequest<PageResult<DocumentItem>>('/api/v1/documents/archived?pageNo=1&pageSize=20', { signal });
    if (isCurrentSession(expectedGeneration)) {
      archivedDocuments.value = page.records;
    }
  } catch (err) {
    if (!signal.aborted && isCurrentSession(expectedGeneration)) {
      setError(friendlyError(err, '加载归档文档失败。'));
    }
  }
}

async function loadDocuments(
  expectedGeneration = sessionGeneration,
  signal: AbortSignal = sessionAbortController.signal
) {
  loading.documents = true;
  setError('');
  const previousSelection = selectedDocumentId.value;
  try {
    const page = await apiRequest<PageResult<DocumentItem>>('/api/v1/documents?pageNo=1&pageSize=50', { signal });
    if (!isCurrentSession(expectedGeneration)) {
      return;
    }
    documents.value = page.records;
    documentTotal.value = page.total;
    selectedDocumentId.value = page.records.some((document) => document.id === previousSelection)
      ? previousSelection
      : page.records[0]?.id ?? null;
  } catch (err) {
    if (!signal.aborted && isCurrentSession(expectedGeneration)) {
      setError(friendlyError(err, '加载知识文档失败。'));
    }
  } finally {
    if (isCurrentSession(expectedGeneration)) {
      loading.documents = false;
    }
  }
}

function beginEditDocument(document: DocumentItem | null = selectedDocument.value) {
  if (!document) {
    return;
  }
  editingDocumentId.value = document.id;
  Object.assign(documentEditForm, {
    title: document.title,
    sourceType: document.sourceType,
    tags: document.tags || '',
    summary: document.summary || '',
    content: document.content
  });
}

function cancelEditDocument() {
  editingDocumentId.value = null;
}

async function updateDocument() {
  if (!editingDocumentId.value) {
    return;
  }
  loading.updateDocument = true;
  setError('');
  try {
    await apiRequest<DocumentItem>(`/api/v1/documents/${editingDocumentId.value}`, {
      method: 'PUT',
      body: JSON.stringify({
        ...documentEditForm,
        tags: normalizeTags(documentEditForm.tags)
      })
    });
    await loadDocuments();
    editingDocumentId.value = null;
    showToast('文档已更新，检索分块已重新生成');
  } catch (err) {
    setError(friendlyError(err, '更新文档失败。'));
  } finally {
    loading.updateDocument = false;
  }
}

async function archiveDocument(document: DocumentItem | null = selectedDocument.value) {
  if (!document || !window.confirm(`确定归档“${document.title}”吗？归档后它不会再参与检索。`)) {
    return;
  }
  loading.archiveDocument = true;
  setError('');
  try {
    await apiRequest(`/api/v1/documents/${document.id}`, { method: 'DELETE' });
    editingDocumentId.value = null;
    selectedDocumentId.value = null;
    await Promise.all([loadDocuments(), loadArchivedDocuments()]);
    showToast('文档已归档');
  } catch (err) {
    setError(friendlyError(err, '归档文档失败。'));
  } finally {
    loading.archiveDocument = false;
  }
}

async function restoreDocument(document: DocumentItem) {
  if (!window.confirm(`确定恢复“${document.title}”吗？系统会重新生成检索分块；若当前启用了远程 embedding，可能产生一次外部调用。`)) {
    return;
  }
  loading.restoreDocument = true;
  setError('');
  try {
    const restored = await apiRequest<DocumentItem>(`/api/v1/documents/${document.id}/restore`, { method: 'POST' });
    selectedDocumentId.value = restored.id;
    await Promise.all([loadDocuments(), loadArchivedDocuments()]);
    showToast('文档已恢复并重新加入检索');
  } catch (err) {
    setError(friendlyError(err, '恢复文档失败。'));
  } finally {
    loading.restoreDocument = false;
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
    setError(friendlyError(err, '创建文档失败。'));
  } finally {
    loading.createDocument = false;
  }
}

function onImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  selectedImportFile.value = input.files?.[0] ?? null;

  if (selectedImportFile.value && selectedImportFile.value.size > 256 * 1024) {
    selectedImportFile.value = null;
    importFileInputKey.value += 1;
    setError('文件过大，请选择不超过 256KB 的 TXT 或 Markdown 文件。');
    return;
  }

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
      formData.append('tags', normalizeTags(importForm.tags));
    }
    if (importForm.summary.trim()) {
      formData.append('summary', importForm.summary.trim());
    }

    const document = await uploadDocument(formData);
    selectedDocumentId.value = document.id;
    selectedImportFile.value = null;
    importFileInputKey.value += 1;
    importForm.title = '';
    importForm.sourceType = 'learning_note';
    importForm.tags = '';
    importForm.summary = '';
    await loadDocuments();
    showToast('文件已导入并完成分块');
  } catch (err) {
    setError(friendlyError(err, '导入文件失败。'));
  } finally {
    loading.importDocument = false;
  }
}

async function ask() {
  if (!askForm.question.trim()) {
    setError('请输入问题后再询问 DevMind。');
    return;
  }
  const session = currentSession();
  loading.ask = true;
  startAskTimer();
  setError('');
  try {
    askResponse.value = await apiRequest<AskResponse>('/api/v1/ai/ask', {
      method: 'POST',
      body: JSON.stringify({ question: askForm.question.trim() }),
      signal: session.signal
    });
    if (!isCurrentSession(session.generation)) {
      return;
    }
    restoredFromLog.value = false;
    restoredAskLogStatus.value = null;
    activeView.value = 'ask';
    showToast('AI 回答已生成');
    void loadAskLogs();
  } catch (err) {
    if (!session.signal.aborted && isCurrentSession(session.generation)) {
      setError(friendlyError(err, 'AI 问答失败。'));
    }
  } finally {
    if (isCurrentSession(session.generation)) {
      loading.ask = false;
      stopAskTimer();
    }
  }
}

async function submitFeedback(helpful: boolean) {
  if (!askResponse.value?.logId) {
    setError('请先完成一次 AI 问答，再提交反馈。');
    return;
  }
  const session = currentSession();
  loading.feedback = true;
  setError('');
  try {
    await apiRequest(`/api/v1/ai/ask-logs/${askResponse.value.logId}/feedback`, {
      method: 'POST',
      body: JSON.stringify({
        helpful,
        reason: feedbackForm.reason,
        expectedAnswer: feedbackForm.expectedAnswer
      }),
      signal: session.signal
    });
    if (!isCurrentSession(session.generation)) {
      return;
    }
    showToast(helpful ? '已标记为有帮助' : 'Bad case 已保存');
    feedbackForm.reason = '';
    void loadEvaluationOverview();
  } catch (err) {
    if (!session.signal.aborted && isCurrentSession(session.generation)) {
      setError(friendlyError(err, '提交反馈失败。'));
    }
  } finally {
    if (isCurrentSession(session.generation)) {
      loading.feedback = false;
    }
  }
}

async function loadEvaluationOverview(
  expectedGeneration = sessionGeneration,
  signal: AbortSignal = sessionAbortController.signal
) {
  if (!isAuthed.value) {
    return;
  }
  try {
    const [summary, dataset] = await Promise.all([
      apiRequest<EvaluationSummary>('/api/v1/ai/evaluation/summary?recentLimit=5', { signal }),
      apiRequest<RagEvaluationDataset>('/api/v1/ai/evaluation/dataset', { signal })
    ]);
    if (!isCurrentSession(expectedGeneration)) {
      return;
    }
    evaluation.value = summary;
    evaluationDataset.value = dataset;
  } catch (err) {
    if (!signal.aborted && isCurrentSession(expectedGeneration)) {
      setError(friendlyError(err, '加载评估摘要失败。'));
    }
  }
}

async function runRetrievalEvaluation() {
  if (!isAuthed.value || loading.evaluation) {
    return;
  }
  if (!window.confirm('完整检索评估会执行 40 条标准问题和多种检索策略。启用远程 embedding 或 rerank 时可能产生外部请求、等待时间和费用。确定继续吗？')) {
    return;
  }
  const session = currentSession();
  loading.evaluation = true;
  setError('');
  try {
    const retrieval = await apiRequest<RagRetrievalEvaluation>(
      '/api/v1/ai/evaluation/retrieval',
      { signal: session.signal }
    );
    if (!isCurrentSession(session.generation)) {
      return;
    }
    retrievalEvaluation.value = retrieval;
    lastEvaluationRunAt.value = new Date().toISOString();
    showToast('完整检索评估已完成');
  } catch (err) {
    if (!session.signal.aborted && isCurrentSession(session.generation)) {
      setError(friendlyError(err, '完整检索评估失败，请检查后端日志。'));
    }
  } finally {
    if (isCurrentSession(session.generation)) {
      loading.evaluation = false;
    }
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
    setError(friendlyError(err, '加载问答日志详情失败。'));
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

async function loadAskLogs(
  restoreLatest = false,
  expectedGeneration = sessionGeneration,
  signal: AbortSignal = sessionAbortController.signal,
  requestedPage = askLogPage.value
) {
  if (!isAuthed.value) {
    return;
  }
  loading.askLogs = true;
  try {
    const page = await apiRequest<PageResult<AskLogItem>>(
      `/api/v1/ai/ask-logs?pageNo=${requestedPage}&pageSize=${askLogPageSize}`,
      { signal }
    );
    if (!isCurrentSession(expectedGeneration)) {
      return;
    }
    askLogs.value = page.records;
    askLogPage.value = page.pageNo;
    askLogTotal.value = page.total;
    if (restoreLatest && !askResponse.value && page.records.length > 0) {
      await restoreAskFromLog(page.records[0], false);
    }
  } catch (err) {
    if (!signal.aborted && isCurrentSession(expectedGeneration)) {
      setError(friendlyError(err, '加载问答日志失败。'));
    }
  } finally {
    if (isCurrentSession(expectedGeneration)) {
      loading.askLogs = false;
    }
  }
}

function changeAskLogPage(nextPage: number) {
  const totalPages = Math.max(1, Math.ceil(askLogTotal.value / askLogPageSize));
  if (nextPage < 1 || nextPage > totalPages || loading.askLogs) {
    return;
  }
  selectedLogDetail.value = null;
  askLogPage.value = nextPage;
  void loadAskLogs(false, sessionGeneration, sessionAbortController.signal, nextPage);
}

async function refreshAll() {
  void checkBackendHealth();
  await Promise.all([
    loadDocuments(),
    loadArchivedDocuments(),
    loadEvaluationOverview(),
    loadAskLogs(!askResponse.value)
  ]);
  showToast('当前页面数据已刷新');
}

function formatDate(value: string | null) {
  if (!value) {
    return '刚刚';
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value.replace('T', ' ').slice(0, 16);
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })
    .format(parsed)
    .replaceAll('/', '-');
}

function formatSignedPercent(value: number | null | undefined) {
  const numberValue = value ?? 0;
  return `${numberValue >= 0 ? '+' : ''}${Math.round(numberValue * 100)}%`;
}

function formatSignedNumber(value: number | null | undefined) {
  const numberValue = value ?? 0;
  return `${numberValue >= 0 ? '+' : ''}${numberValue.toFixed(3)}`;
}

onMounted(async () => {
  void checkBackendHealth();
  if (!token.value) {
    return;
  }
  try {
    await loadCurrentUser(sessionAbortController.signal);
    void loadInitialData();
  } catch (err) {
    clearLocalSession();
    setError(friendlyError(err, '登录状态已失效，请重新登录。'));
  }
});

onUnmounted(() => {
  stopAskTimer();
  sessionAbortController.abort();
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

      <nav v-if="isAuthed" class="nav-list" aria-label="主导航">
        <button aria-label="知识文档" title="知识文档" :class="{ active: activeView === 'documents' }" @click="setActiveView('documents')">
          <span v-html="icons.documents"></span>
          知识文档
        </button>
        <button aria-label="AI 问答" title="AI 问答" :class="{ active: activeView === 'ask' }" @click="setActiveView('ask')">
          <span v-html="icons.ask"></span>
          AI 问答
        </button>
        <button aria-label="评估看板" title="评估看板" :class="{ active: activeView === 'evaluation' }" @click="setActiveView('evaluation')">
          <span v-html="icons.chart"></span>
          评估看板
        </button>
        <button aria-label="问答日志" title="问答日志" :class="{ active: activeView === 'logs' }" @click="setActiveView('logs')">
          <span v-html="icons.ask"></span>
          问答日志
        </button>
      </nav>

      <div class="sidebar-note">
        <span class="backend-status-line">
          <i :class="['backend-dot', backendStatus]"></i>
          {{ backendStatus === 'up' ? '后端在线' : backendStatus === 'down' ? '后端离线' : '正在检测' }}
        </span>
        <strong>{{ apiTargetLabel.replace(/^https?:\/\//, '') }}</strong>
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
            {{ loading.auth ? (authForm.mode === 'login' ? '登录中...' : '创建中...') : authForm.mode === 'login' ? '登录' : '创建账号' }}
          </button>
        </form>
      </section>

      <template v-else>
        <div v-if="loading.initialData" class="status-message" role="status" aria-live="polite">
          正在加载文档、评估摘要和最近日志，不影响页面操作。
        </div>
        <section class="status-grid">
          <div class="metric">
            <span>知识文档</span>
            <strong>{{ documentTotal }}</strong>
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
            <strong>{{ askLogTotal }}</strong>
          </div>
        </section>

        <section class="main-grid single-view-grid">
          <div v-show="activeView === 'documents'" class="panel document-panel">
            <div class="panel-header">
              <div>
                <h2>知识文档</h2>
                <p>用于检索召回和答案引用的学习材料。</p>
              </div>
              <button class="icon-button" title="刷新知识文档" @click="loadDocuments()">
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
                <span>{{ sourceTypeLabel(document.sourceType) }} · {{ document.tags || '无标签' }}</span>
                <small>{{ formatDate(document.updatedAt || document.createdAt) }}</small>
              </button>
              <div v-if="!loading.documents && documents.length === 0" class="empty-state">还没有知识文档。先创建或导入一篇笔记来测试检索。</div>
            </div>

            <section v-if="selectedDocument" class="document-inspector">
              <div class="document-inspector-header">
                <div>
                  <span>当前文档</span>
                  <strong>{{ selectedDocument.title }}</strong>
                </div>
                <div class="panel-actions">
                  <button class="mini-button" type="button" @click="beginEditDocument()">编辑</button>
                  <button
                    class="mini-button danger-outline"
                    type="button"
                    :disabled="loading.archiveDocument"
                    @click="archiveDocument()"
                  >
                    {{ loading.archiveDocument ? '归档中...' : '归档' }}
                  </button>
                </div>
              </div>
              <p>{{ selectedDocument.summary || '暂无摘要' }}</p>
              <div class="document-meta-strip">
                <span>{{ sourceTypeLabel(selectedDocument.sourceType) }}</span>
                <span>{{ selectedDocument.tags || '无标签' }}</span>
                <span>更新于 {{ formatDate(selectedDocument.updatedAt || selectedDocument.createdAt) }}</span>
              </div>
            </section>

            <details class="archived-documents">
              <summary>
                <span>归档记录</span>
                <small>{{ archivedDocuments.length ? `${archivedDocuments.length} 篇，可恢复` : '暂无归档' }}</small>
              </summary>
              <div class="archived-document-list">
                <div v-for="document in archivedDocuments" :key="document.id">
                  <div>
                    <strong>{{ document.title }}</strong>
                    <small>{{ sourceTypeLabel(document.sourceType) }} · {{ formatDate(document.updatedAt || document.createdAt) }}</small>
                  </div>
                  <button
                    class="mini-button"
                    type="button"
                    :disabled="loading.restoreDocument"
                    @click="restoreDocument(document)"
                  >
                    {{ loading.restoreDocument ? '恢复中...' : '恢复' }}
                  </button>
                </div>
                <p v-if="!archivedDocuments.length" class="empty-state">
                  归档相当于软删除：文档不会参与检索，但仍保留在数据库中，之后可以恢复。
                </p>
              </div>
            </details>

            <form v-if="editingDocumentId" class="document-form edit-document-form" @submit.prevent="updateDocument">
              <div class="form-section-heading">
                <div>
                  <h3>编辑文档</h3>
                  <p>保存后会重新生成检索分块。</p>
                </div>
                <button class="mini-button" type="button" @click="cancelEditDocument">取消</button>
              </div>
              <div class="form-row">
                <label>
                  标题
                  <input v-model="documentEditForm.title" maxlength="120" required />
                </label>
                <label>
                  文档类别
                  <select v-model="documentEditForm.sourceType">
                    <option v-for="option in sourceTypeOptions" :key="option.value" :value="option.value" :disabled="option.legacy">
                      {{ option.label }}（{{ option.value }}）
                    </option>
                  </select>
                </label>
              </div>
              <label>
                标签
                <input v-model="documentEditForm.tags" maxlength="255" />
                <small class="field-hint">标签是可搜索的主题词，可选多个；类别只表达文档用途。</small>
                <span class="tag-suggestions">
                  <button v-for="tag in tagSuggestions" :key="tag" type="button" @click="addSuggestedTag('edit', tag)">+ {{ tag }}</button>
                </span>
              </label>
              <label>
                摘要
                <input v-model="documentEditForm.summary" maxlength="500" />
              </label>
              <label>
                内容
                <textarea v-model="documentEditForm.content" rows="9" maxlength="20000" required></textarea>
              </label>
              <button class="primary-button" type="submit" :disabled="loading.updateDocument">
                {{ loading.updateDocument ? '保存中...' : '保存修改' }}
              </button>
            </form>

            <div class="document-composer-heading">
              <div>
                <h3>添加知识文档</h3>
                <p>已有 Markdown/TXT 选择“导入文件”；临时记录内容选择“手动创建”。两种方式最终都会生成同一种知识文档和检索分块。</p>
              </div>
              <div class="segmented compact-segmented" aria-label="添加文档方式">
                <button type="button" :class="{ active: documentComposerMode === 'import' }" @click="documentComposerMode = 'import'">导入文件</button>
                <button type="button" :class="{ active: documentComposerMode === 'manual' }" @click="documentComposerMode = 'manual'">手动创建</button>
              </div>
            </div>

            <form v-if="documentComposerMode === 'import'" class="import-form" @submit.prevent="importDocument">
              <div class="import-header">
                <div>
                  <h3>导入笔记文件</h3>
                  <p>上传 Markdown 或 TXT 笔记，后端会创建知识文档并自动生成 chunks。</p>
                </div>
              </div>
              <div class="field-group">
                <span>文件 <em class="required-mark">必选</em></span>
                <label class="import-file-picker">
                  <input :key="importFileInputKey" type="file" accept=".md,.markdown,.txt" @change="onImportFileChange" />
                  <span class="file-button">选择文件</span>
                  <span class="file-name">{{ selectedImportFile?.name || '未选择文件' }}</span>
                </label>
              </div>
              <div class="form-row">
                <label>
                  标题
                  <input v-model="importForm.title" placeholder="自动使用文件名，可按需修改" />
                  <small class="field-hint">运行内置评估时请保留样例文件名，避免 gold 标题无法匹配。</small>
                </label>
                <label>
                  文档类别 <em class="required-mark">必选</em>
                  <select v-model="importForm.sourceType">
                    <option v-for="option in sourceTypeOptions" :key="option.value" :value="option.value" :disabled="option.legacy">
                      {{ option.label }}（{{ option.value }}）
                    </option>
                  </select>
                  <small class="field-hint">类型用于区分笔记用途，不要填写多个主题词。</small>
                </label>
              </div>
              <label>
                标签 <span class="optional-mark">可选</span>
                <input v-model="importForm.tags" placeholder="例如：redis,cache,backend" />
                <small class="field-hint">标签是用于搜索和聚合的主题词，使用逗号分隔；系统会自动去重。</small>
                <span class="tag-suggestions">
                  <button v-for="tag in tagSuggestions" :key="tag" type="button" @click="addSuggestedTag('import', tag)">+ {{ tag }}</button>
                </span>
              </label>
              <label>
                摘要 <span class="optional-mark">可选</span>
                <input v-model="importForm.summary" placeholder="可选：导入笔记摘要" />
              </label>
              <button class="secondary-button" type="submit" :disabled="loading.importDocument">
                <span v-html="icons.plus"></span>
                {{ loading.importDocument ? '导入中...' : '导入文件' }}
              </button>
            </form>

            <form v-else class="document-form manual-document-form" @submit.prevent="createDocument">
              <div class="import-header">
                <h3>手动创建</h3>
                <p>适合直接粘贴复盘、架构说明或面试笔记；保存后会自动切分并加入检索。</p>
              </div>
              <div class="form-row">
                <label>
                  标题
                  <input v-model="documentForm.title" maxlength="120" required />
                </label>
                <label>
                  文档类别
                  <select v-model="documentForm.sourceType">
                    <option v-for="option in sourceTypeOptions" :key="option.value" :value="option.value" :disabled="option.legacy">
                      {{ option.label }}（{{ option.value }}）
                    </option>
                  </select>
                </label>
              </div>
              <label>
                标签 <span class="optional-mark">可选</span>
                <input v-model="documentForm.tags" maxlength="255" />
                <small class="field-hint">选择常用主题，或继续手动输入更具体的技术词。</small>
                <span class="tag-suggestions">
                  <button v-for="tag in tagSuggestions" :key="tag" type="button" @click="addSuggestedTag('create', tag)">+ {{ tag }}</button>
                </span>
              </label>
              <label>
                摘要 <span class="optional-mark">可选</span>
                <input v-model="documentForm.summary" maxlength="500" />
              </label>
              <label>
                内容
                <textarea v-model="documentForm.content" rows="9" maxlength="20000" required></textarea>
              </label>
              <button class="secondary-button" type="submit" :disabled="loading.createDocument">
                <span v-html="icons.plus"></span>
                {{ loading.createDocument ? '创建中...' : '创建文档' }}
              </button>
            </form>
          </div>

          <div v-show="activeView === 'ask'" class="panel ask-panel">
            <div class="panel-header">
              <div>
                <h2>AI 问答</h2>
                <p>基于检索召回的知识片段回答问题。</p>
              </div>
            </div>

            <form class="ask-form" @submit.prevent="ask">
              <textarea
                v-model="askForm.question"
                rows="4"
                placeholder="输入一个能从当前知识文档中回答的问题"
              ></textarea>
              <button class="primary-button" type="submit" :disabled="loading.ask">
                <span v-html="icons.send"></span>
                {{ loading.ask ? `生成中 ${askElapsedSeconds}s` : '询问 DevMind' }}
              </button>
            </form>
            <div
              v-if="loading.ask"
              class="ask-progress"
              role="status"
              aria-live="polite"
              aria-atomic="true"
            >
              <span class="progress-dot"></span>
              <div>
                <strong>{{ askProgressText }}</strong>
                <small>外部模型通常需要数秒；完整离线评估不会在这里自动运行。</small>
              </div>
            </div>

            <div v-if="askResponse" class="answer-card">
              <div class="answer-meta">
                <span :class="['state-pill', currentAskStateClass]">{{ currentAskState }}</span>
                <span>{{ askResponse.mock ? 'Mock/本地' : '真实模型' }}</span>
                <span>{{ askResponse.modelProvider }}</span>
                <span>检索词: {{ askResponse.retrievalKeyword }}</span>
                <span>logId: {{ askResponse.logId }}</span>
                <span v-if="restoredFromLog" class="restored-pill">从日志恢复</span>
              </div>
              <div class="markdown-content" v-html="renderedAnswer"></div>

              <div class="citation-list">
                <h3>召回来源（检索上下文）</h3>
                <div v-for="citation in askResponse.citations" :key="citation.chunkId" class="citation">
                  <strong>#{{ citation.chunkId }}</strong>
                  <span>{{ citation.documentTitle }}</span>
                  <small>分数 {{ citation.score }}</small>
                </div>
                <div v-if="askResponse.citations.length === 0" class="empty-state compact">
                  {{ restoredFromLog ? '这条历史日志没有保存召回片段 id。' : '没有召回来源。无上下文兜底时这是正常情况。' }}
                </div>
              </div>

              <div class="token-strip">
                <span>提示词 token {{ askResponse.promptTokens ?? '-' }}</span>
                <span>回答 token {{ askResponse.completionTokens ?? '-' }}</span>
                <span>总 token {{ askResponse.totalTokens ?? '-' }}</span>
                <span>召回片段 {{ askResponse.retrievedChunks?.length ?? 0 }}</span>
              </div>

              <details class="debug-details">
                <summary>开发者详情：原始系统提示词、token 与召回片段</summary>
                <p class="debug-explanation">
                  系统提示词当前使用英文编写，但明确要求按问题语言回答；这里保留原文便于调试，不是面向普通用户的正文。
                </p>
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
                  <button class="secondary-button" :disabled="loading.feedback" @click="submitFeedback(true)">
                    {{ loading.feedback ? '保存中...' : '有帮助' }}
                  </button>
                  <button class="danger-button" :disabled="loading.feedback" @click="submitFeedback(false)">
                    {{ loading.feedback ? '保存中...' : '保存 bad case' }}
                  </button>
                </div>
              </div>
            </div>
            <div v-else class="empty-answer">提出一个问题后，这里会展示回答、召回来源、token 用量和反馈控件。</div>
          </div>
        </section>

        <section v-show="activeView === 'evaluation'" class="panel evaluation-panel">
          <div class="panel-header">
            <div>
              <h2>评估看板</h2>
              <p>查看 bad case、评估集覆盖情况，并按需运行完整检索评估。</p>
            </div>
            <div class="panel-actions">
              <button class="secondary-button compact-button" type="button" @click="loadEvaluationOverview()">
                刷新摘要
              </button>
              <button
                class="primary-button compact-button"
                type="button"
                :disabled="loading.evaluation"
                @click="runRetrievalEvaluation"
              >
                {{ loading.evaluation ? '评估运行中...' : '运行完整检索评估' }}
              </button>
            </div>
          </div>
          <div class="evaluation-warning" role="note">
            <strong>完整评估不会自动运行。</strong>
            <span>它会执行 40 条用例和多种检索策略；若配置了远程 embedding 或 rerank，可能产生外部请求、等待时间和费用。</span>
            <span>指标基于当前账号实际导入的文档。未导入标准评估语料时出现 0%，不代表检索策略本身不可用，也不能直接与 README 基准比较。</span>
            <small v-if="lastEvaluationRunAt">本页最近完成：{{ formatDate(lastEvaluationRunAt) }}</small>
          </div>
          <div class="badcase-list">
            <div v-for="badCase in evaluation?.recentBadCases || []" :key="badCase.feedbackId" class="badcase-row">
              <strong>{{ badCase.question || '未知问题' }}</strong>
              <span>{{ badCase.reason || '未填写原因' }}</span>
              <small>{{ formatDate(badCase.createdAt) }}</small>
            </div>
            <div v-if="!evaluation?.recentBadCases?.length" class="empty-state">暂无 bad case。</div>
          </div>

          <details v-if="retrievalEvaluation" class="evaluation-dataset evaluation-catalog">
            <summary class="dataset-header">
              <div>
                <h3>检索评估</h3>
                <p>用标准问题直接跑检索，检查召回是否命中人工标注的相关文档。</p>
              </div>
              <div class="dataset-score">
                <strong>{{ retrievalEvaluation?.passedCaseCount ?? 0 }}/{{ retrievalEvaluation?.totalCaseCount ?? 0 }}</strong>
                <span>通过率 {{ Math.round((retrievalEvaluation?.passRate ?? 0) * 100) }}%</span>
                <span>Hit@{{ retrievalEvaluation?.evaluationK ?? 3 }} {{ Math.round((retrievalEvaluation?.hitAtK ?? 0) * 100) }}%</span>
                <span>MRR {{ (retrievalEvaluation?.mrr ?? 0).toFixed(3) }}</span>
                <span>基线 Hit@{{ retrievalEvaluation?.evaluationK ?? 3 }} {{ Math.round((retrievalEvaluation?.baselineHitAtK ?? 0) * 100) }}%</span>
                <span>ΔHit {{ formatSignedPercent(retrievalEvaluation?.hitAtKDelta) }}</span>
                <span>基线 MRR {{ (retrievalEvaluation?.baselineMrr ?? 0).toFixed(3) }}</span>
                <span>ΔMRR {{ formatSignedNumber(retrievalEvaluation?.mrrDelta) }}</span>
                <span>候选池 Top {{ retrievalEvaluation?.retrievalLimit ?? 5 }}</span>
                <span>策略 {{ retrievalEvaluation?.retrievalStrategy || 'keyword-baseline' }}</span>
                <span>基线 {{ retrievalEvaluation?.baselineRetrievalStrategy || 'mysql-fulltext-keyword-v1' }}</span>
                <span>相关性 {{ retrievalEvaluation?.relevanceMode || 'gold-document-title' }}</span>
              </div>
            </summary>

            <div
              v-if="(retrievalEvaluation?.strategyResults?.length ?? 0) > 0"
              class="strategy-comparison"
            >
              <h4>五路检索策略对比（Hit@{{ retrievalEvaluation?.evaluationK ?? 3 }} / MRR，相对 keyword baseline）</h4>
              <div class="table-scroll" role="region" aria-label="检索策略对比表" tabindex="0">
              <table class="strategy-table">
                <thead>
                  <tr>
                    <th>策略</th>
                    <th>状态</th>
                    <th>Hit@{{ retrievalEvaluation?.evaluationK ?? 3 }}</th>
                    <th>MRR</th>
                    <th>ΔHit</th>
                    <th>ΔMRR</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="strategy in retrievalEvaluation?.strategyResults || []" :key="strategy.strategyKey">
                    <td>{{ strategy.strategyKey }}</td>
                    <td>
                      <span v-if="strategy.status === 'available'" class="strategy-ok">可用</span>
                      <span v-else class="strategy-na" :title="strategy.unavailableReason || ''">
                        不可用<template v-if="strategy.unavailableReason">（{{ strategy.unavailableReason }}）</template>
                      </span>
                    </td>
                    <td>{{ strategy.status === 'available' ? Math.round((strategy.hitAtK ?? 0) * 100) + '%' : '—' }}</td>
                    <td>{{ strategy.status === 'available' ? (strategy.mrr ?? 0).toFixed(3) : '—' }}</td>
                    <td>{{ strategy.status === 'available' ? formatSignedPercent(strategy.hitAtKDelta ?? 0) : '—' }}</td>
                    <td>{{ strategy.status === 'available' ? formatSignedNumber(strategy.mrrDelta ?? 0) : '—' }}</td>
                  </tr>
                </tbody>
              </table>
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
            </div>
          </details>
          <div v-else class="empty-state evaluation-empty">
            尚未运行本次完整检索评估。登录、问答和保存反馈不会再自动触发它；需要时请点击“运行完整检索评估”。
          </div>

          <details class="evaluation-dataset evaluation-catalog">
            <summary class="dataset-header">
              <div>
                <h3>RAG 评估集</h3>
                <p>40 条标准问题默认收起，需要检查明细时再展开。</p>
              </div>
              <div class="dataset-score">
                <strong>{{ evaluationDataset?.coveredCaseCount ?? 0 }}/{{ evaluationDataset?.totalCaseCount ?? 0 }}</strong>
                <span>覆盖率 {{ Math.round((evaluationDataset?.coverageRate ?? 0) * 100) }}%</span>
              </div>
            </summary>

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
          </details>
        </section>

        <section v-show="activeView === 'logs'" class="panel logs-panel">
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

          <nav v-if="askLogTotal > askLogPageSize" class="pagination" aria-label="问答日志分页">
            <button class="secondary-button compact-button" type="button" :disabled="askLogPage <= 1 || loading.askLogs" @click="changeAskLogPage(askLogPage - 1)">
              上一页
            </button>
            <span>第 {{ askLogPage }} / {{ Math.ceil(askLogTotal / askLogPageSize) }} 页 · 共 {{ askLogTotal }} 条</span>
            <button
              class="secondary-button compact-button"
              type="button"
              :disabled="askLogPage >= Math.ceil(askLogTotal / askLogPageSize) || loading.askLogs"
              @click="changeAskLogPage(askLogPage + 1)"
            >
              下一页
            </button>
          </nav>

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
                <div class="markdown-content compact-markdown" v-html="renderedLogAnswer"></div>
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

      <div v-if="toast" class="toast" role="status" aria-live="polite">{{ toast }}</div>
      <div v-if="error" class="error-banner" role="alert" aria-live="assertive">{{ error }}</div>
    </main>
  </div>
</template>
