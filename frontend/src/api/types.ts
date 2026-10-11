// Mirrors the Spring Boot DTOs (com.example.docmind.dto). Timestamps are ISO-8601 strings.

export interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  data?: T;
  error?: string;
  timestamp: string;
}

export type Role = 'USER' | 'ADMIN';

export interface User {
  id: number;
  username: string;
  email: string;
  role: Role;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: User;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export type DocumentStatus = 'UPLOADING' | 'PROCESSING' | 'INDEXED' | 'FAILED';

export interface DocumentMetadata {
  id: string;
  filename: string;
  contentType: string;
  fileSize: number | null;
  totalPages: number | null;
  totalChunks: number | null;
  status: DocumentStatus;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface DocumentUploadResult {
  id: string | null;
  filename: string;
  fileSize: number;
  status: DocumentStatus;
  chunksCreated: number | null;
  message: string;
}

export interface Citation {
  position: number;
  documentId: string | null;
  fileName: string;
  chunkIndex: number | null;
  pageNumber: number | null;
  snippet: string;
  similarityScore: number | null;
}

export interface SearchRequest {
  query: string;
  documentId?: string;
  topK?: number;
  minSimilarity?: number;
}

export interface SearchResult {
  query: string;
  totalMatches: number;
  matches: Citation[];
}

export type MessageRole = 'USER' | 'ASSISTANT';
export type MessageStatus = 'COMPLETE' | 'FAILED';

export interface Message {
  id: string;
  conversationId: string;
  role: MessageRole;
  status: MessageStatus;
  content: string;
  documentId: string | null;
  replyToId: string | null;
  responseTimeMs: number | null;
  createdAt: string;
  citations: Citation[];
}

export interface ConversationSummary {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationDetail extends ConversationSummary {
  messages: Message[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface ChatRequest {
  question?: string;
  conversationId?: string;
  documentId?: string;
  topK?: number;
  clientMessageId?: string;
  retryOfMessageId?: string;
}

export interface ChatExchange {
  conversation: ConversationSummary;
  userMessage: Message;
  assistantMessage: Message;
}
