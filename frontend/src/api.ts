export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
}

export interface UserProfile {
  id: number;
  username: string;
  nickname: string;
  email: string;
}

export interface DocumentItem {
  id: number;
  title: string;
  content: string;
  sourceType: string;
  tags: string;
  summary: string;
  status: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface PageResult<T> {
  pageNo: number;
  pageSize: number;
  total: number;
  records: T[];
}

export interface Citation {
  chunkId: number;
  documentId: number;
  documentTitle: string;
  chunkIndex: number;
  score: number;
}

export interface RetrievedChunk {
  chunkId: number;
  documentId: number;
  documentTitle: string;
  sourceType: string;
  tags: string;
  chunkIndex: number;
  content: string;
  tokenCount: number;
  score: number;
}

export interface AskResponse {
  logId: number;
  question: string;
  retrievalKeyword: string;
  answer: string;
  modelProvider: string;
  mock: boolean;
  promptPreview: string;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  retrievedChunks: RetrievedChunk[];
  citations: Citation[];
}

export interface AskLogItem {
  id: number;
  question: string;
  retrievalKeyword: string;
  promptPreview: string | null;
  answer: string;
  modelProvider: string;
  mock: boolean;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  retrievedChunkCount: number;
  retrievedChunkIds: string | null;
  elapsedMs: number;
  status: number;
  createdAt: string;
}

export interface AskFeedbackItem {
  id: number;
  askLogId: number;
  helpful: boolean;
  reason: string | null;
  expectedAnswer: string | null;
  status: number;
  createdAt: string;
}

export interface EvaluationSummary {
  totalFeedbackCount: number;
  helpfulCount: number;
  badCaseCount: number;
  badCaseRate: number;
  recentBadCases: Array<{
    feedbackId: number;
    askLogId: number;
    question: string | null;
    reason: string | null;
    expectedAnswer: string | null;
    createdAt: string;
  }>;
}

export interface RagEvaluationCase {
  caseId: string;
  category: string;
  question: string;
  expectedKeywords: string[];
  expectedAnswer: string;
  expectedEvidence: string;
  riskType: string;
  covered: boolean;
  lastAskLogId: number | null;
  lastStatus: number | null;
  lastRetrievedChunkCount: number | null;
  lastRetrievedChunkIds: string | null;
  lastAskedAt: string | null;
}

export interface RagEvaluationDataset {
  totalCaseCount: number;
  coveredCaseCount: number;
  coverageRate: number;
  cases: RagEvaluationCase[];
}

export interface RagRetrievalEvaluationCase {
  caseId: string;
  category: string;
  question: string;
  expectedKeywords: string[];
  relevantDocumentTitles: string[];
  queryKeywords: string[];
  matchedExpectedKeywords: string[];
  missingExpectedKeywords: string[];
  expectedEvidence: string;
  riskType: string;
  passed: boolean;
  expectedNoContext: boolean;
  retrievedChunkCount: number;
  firstRelevantRank: number | null;
  hitAtK: boolean;
  reciprocalRank: number | null;
  topChunkIds: number[];
  topDocumentTitles: string[];
  note: string;
}

export interface RagRetrievalEvaluation {
  totalCaseCount: number;
  passedCaseCount: number;
  passRate: number;
  positiveCaseCount: number;
  evaluationK: number;
  hitAtK: number;
  mrr: number;
  cases: RagRetrievalEvaluationCase[];
}

const TOKEN_KEY = 'devmind_token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const token = getToken();

  if (!headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json');
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(path, {
    ...options,
    headers
  });

  const responseText = await response.text();
  let result: ApiResult<T> | null = null;

  if (responseText) {
    try {
      result = JSON.parse(responseText) as ApiResult<T>;
    } catch {
      throw new Error(`Request failed: ${response.status}`);
    }
  }

  if (!result) {
    throw new Error(`Request failed: ${response.status}`);
  }

  if (!response.ok || result.code !== 0) {
    throw new Error(result.message || `Request failed: ${response.status}`);
  }
  return result.data;
}

export async function uploadDocument(formData: FormData): Promise<DocumentItem> {
  const headers = new Headers();
  const token = getToken();

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch('/api/v1/documents/import', {
    method: 'POST',
    headers,
    body: formData
  });

  const responseText = await response.text();
  let result: ApiResult<DocumentItem> | null = null;

  if (responseText) {
    try {
      result = JSON.parse(responseText) as ApiResult<DocumentItem>;
    } catch {
      throw new Error(`Request failed: ${response.status}`);
    }
  }

  if (!result) {
    throw new Error(`Request failed: ${response.status}`);
  }

  if (!response.ok || result.code !== 0) {
    throw new Error(result.message || `Request failed: ${response.status}`);
  }

  return result.data;
}
