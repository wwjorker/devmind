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

  const result = (await response.json()) as ApiResult<T>;
  if (!response.ok || result.code !== 0) {
    throw new Error(result.message || `Request failed: ${response.status}`);
  }
  return result.data;
}
