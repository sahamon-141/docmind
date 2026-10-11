import { apiRequest, refreshSession, tokenStore, uploadRequest, type UploadOptions } from './client';
import { CSRF_HEADER } from './config';
import type {
  AuthResponse,
  ConversationDetail,
  ConversationSummary,
  DocumentMetadata,
  DocumentUploadResult,
  LoginRequest,
  Page,
  RegisterRequest,
  SearchRequest,
  SearchResult,
  User,
} from './types';

// ------------------------------------------------------------------ auth

export const authApi = {
  async login(body: LoginRequest): Promise<AuthResponse> {
    const auth = await apiRequest<AuthResponse>('/auth/login', { method: 'POST', body, auth: false });
    tokenStore.set(auth.accessToken);
    return auth;
  },
  async register(body: RegisterRequest): Promise<AuthResponse> {
    const auth = await apiRequest<AuthResponse>('/auth/register', { method: 'POST', body, auth: false });
    tokenStore.set(auth.accessToken);
    return auth;
  },
  /** Restores a session from the refresh cookie (used on page load). */
  restore: () => refreshSession(),
  async logout(): Promise<void> {
    try {
      await apiRequest<null>('/auth/logout', { method: 'POST', auth: false, headers: { [CSRF_HEADER]: '1' } });
    } finally {
      tokenStore.set(null);
    }
  },
  me: () => apiRequest<User>('/auth/me'),
};

export const accountApi = {
  updateProfile: (email: string) => apiRequest<User>('/users/me', { method: 'PATCH', body: { email } }),
  changePassword: async (currentPassword: string, newPassword: string): Promise<void> => {
    await apiRequest<null>('/users/me/password', { method: 'POST', body: { currentPassword, newPassword } });
  },
};

// ------------------------------------------------------------------ documents

export const documentsApi = {
  list: (signal?: AbortSignal) => apiRequest<DocumentMetadata[]>('/documents', { signal }),
  get: (id: string, signal?: AbortSignal) => apiRequest<DocumentMetadata>(`/documents/${id}`, { signal }),
  remove: async (id: string): Promise<void> => {
    await apiRequest<null>(`/documents/${id}`, { method: 'DELETE' });
  },
  upload(file: File, options?: UploadOptions) {
    const form = new FormData();
    form.append('file', file);
    return uploadRequest<DocumentUploadResult>('/documents/upload', form, options);
  },
  search: (body: SearchRequest, signal?: AbortSignal) =>
    apiRequest<SearchResult>('/chat/search/similarity', { method: 'POST', body, signal }),
};

// ------------------------------------------------------------------ conversations
// Messages are sent through the streaming endpoint (api/stream.ts); a new conversation is
// created by the server together with its first message.

export const conversationsApi = {
  list: (query: string, signal?: AbortSignal) => {
    const params = new URLSearchParams({ size: '100' });
    if (query.trim()) params.set('q', query.trim());
    return apiRequest<Page<ConversationSummary>>(`/conversations?${params}`, { signal });
  },
  get: (id: string, signal?: AbortSignal) => apiRequest<ConversationDetail>(`/conversations/${id}`, { signal }),
  rename: (id: string, title: string) =>
    apiRequest<ConversationSummary>(`/conversations/${id}`, { method: 'PATCH', body: { title } }),
  remove: async (id: string): Promise<void> => {
    await apiRequest<null>(`/conversations/${id}`, { method: 'DELETE' });
  },
};
