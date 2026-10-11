import { apiUrl, CSRF_HEADER } from './config';
import { ApiError } from './errors';
import type { ApiEnvelope, AuthResponse } from './types';

/*
 * Central HTTP client.
 *
 * - The short-lived access token lives only in this module's memory (never in Web Storage).
 * - The refresh token is an HttpOnly cookie the browser sends to /api/v1/auth/* only.
 * - On a 401 the client performs at most one refresh and retries the original request once.
 * - Concurrent 401s share a single in-flight refresh, so the refresh token is rotated only once.
 */

type AuthEvent = { type: 'refreshed'; auth: AuthResponse } | { type: 'expired' };
type AuthListener = (event: AuthEvent) => void;

let accessToken: string | null = null;
let refreshInFlight: Promise<AuthResponse> | null = null;
const listeners = new Set<AuthListener>();

export const tokenStore = {
  get: () => accessToken,
  set: (token: string | null) => {
    accessToken = token;
  },
};

export function onAuthEvent(listener: AuthListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

const emit = (event: AuthEvent) => listeners.forEach((l) => l(event));

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const DEFAULT_MESSAGES: Record<number, string> = {
  400: 'The request was invalid.',
  401: 'Please sign in to continue.',
  403: 'You do not have permission to do that.',
  404: 'Not found.',
  409: 'That conflicts with existing data.',
  413: 'The file is too large.',
  415: 'That file type is not supported.',
  422: 'The request could not be processed.',
  429: 'Too many requests. Please slow down.',
  502: 'The AI service is unavailable right now.',
  503: 'The service is temporarily unavailable.',
};

/** Converts a response body (ApiResponse envelope) into data or an ApiError. */
export function parseBody<T>(status: number, text: string): T {
  let body: ApiEnvelope<T> | null = null;
  if (text) {
    try {
      body = JSON.parse(text) as ApiEnvelope<T>;
    } catch {
      body = null;
    }
  }
  const ok = status >= 200 && status < 300;
  if (!ok || !body || body.success === false) {
    throw new ApiError(
      status,
      body?.error ?? `HTTP_${status}`,
      body?.message ?? DEFAULT_MESSAGES[status] ?? 'Unexpected server response.',
      body?.data,
    );
  }
  return body.data as T;
}

async function parseResponse<T>(res: Response): Promise<T> {
  return parseBody<T>(res.status, await res.text());
}

const isAuthPath = (path: string) => path.startsWith('/auth/');

async function send(path: string, init: RequestInit, withAuth: boolean): Promise<Response> {
  const headers = new Headers(init.headers);
  if (withAuth && accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  try {
    return await fetch(apiUrl(path), {
      ...init,
      headers,
      // the refresh cookie is only needed (and only scoped) for /auth/* endpoints
      credentials: isAuthPath(path) ? 'include' : 'same-origin',
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e;
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the DocMind server.');
  }
}

async function performRefresh(): Promise<AuthResponse> {
  for (let attempt = 0; attempt < 2; attempt++) {
    const res = await send('/auth/refresh', { method: 'POST', headers: { [CSRF_HEADER]: '1' } }, false);
    // 409: another tab rotated the same session a moment ago; its new cookie is already stored, so retry once
    if (res.status === 409 && attempt === 0) {
      await sleep(300);
      continue;
    }
    try {
      const auth = await parseResponse<AuthResponse>(res);
      accessToken = auth.accessToken;
      emit({ type: 'refreshed', auth });
      return auth;
    } catch (e) {
      if (e instanceof ApiError && (e.status === 401 || e.status === 403 || e.status === 409)) {
        accessToken = null;
        emit({ type: 'expired' });
      }
      throw e;
    }
  }
  throw new ApiError(401, 'INVALID_REFRESH_TOKEN', 'Your session has expired. Please sign in again.');
}

/** Single-flight refresh: concurrent callers await the same rotation. */
export function refreshSession(): Promise<AuthResponse> {
  refreshInFlight ??= performRefresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function sendWithRefresh(path: string, init: RequestInit, withAuth: boolean): Promise<Response> {
  const res = await send(path, init, withAuth);
  if (res.status !== 401 || !withAuth) return res;
  try {
    await refreshSession();
  } catch {
    throw new ApiError(401, 'SESSION_EXPIRED', 'Your session has expired. Please sign in again.');
  }
  return send(path, init, withAuth); // retried exactly once
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
  auth?: boolean;
  headers?: Record<string, string>;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, signal, auth = true, headers = {} } = options;
  const init: RequestInit = { method, signal, headers: { Accept: 'application/json', ...headers } };
  if (body instanceof FormData) {
    init.body = body;
  } else if (body !== undefined) {
    init.body = JSON.stringify(body);
    (init.headers as Record<string, string>)['Content-Type'] = 'application/json';
  }
  return parseResponse<T>(await sendWithRefresh(path, init, auth));
}

/** Opens a streaming POST (used for Server-Sent Events). Error statuses are thrown as ApiError. */
export async function openStream(path: string, body: unknown, signal: AbortSignal): Promise<Response> {
  const init: RequestInit = {
    method: 'POST',
    signal,
    // JSON must be acceptable too: errors raised before the stream starts are ordinary ApiResponse bodies
    headers: { Accept: 'text/event-stream, application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
  const res = await sendWithRefresh(path, init, true);
  if (!res.ok) await parseResponse<never>(res);
  return res;
}

export interface UploadOptions {
  onProgress?: (fraction: number) => void;
  signal?: AbortSignal;
}

/** Multipart upload via XHR, because fetch cannot report upload progress. */
export async function uploadRequest<T>(path: string, form: FormData, options: UploadOptions = {}): Promise<T> {
  const attempt = () =>
    new Promise<{ status: number; text: string }>((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open('POST', apiUrl(path));
      xhr.withCredentials = false;
      if (accessToken) xhr.setRequestHeader('Authorization', `Bearer ${accessToken}`);
      xhr.setRequestHeader('Accept', 'application/json');
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) options.onProgress?.(e.loaded / e.total);
      };
      xhr.onload = () => resolve({ status: xhr.status, text: xhr.responseText });
      xhr.onerror = () => reject(new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the DocMind server.'));
      xhr.onabort = () => reject(new DOMException('Upload cancelled', 'AbortError'));
      const abort = () => xhr.abort();
      options.signal?.addEventListener('abort', abort, { once: true });
      xhr.onloadend = () => options.signal?.removeEventListener('abort', abort);
      xhr.send(form);
    });

  let result = await attempt();
  if (result.status === 401) {
    try {
      await refreshSession();
    } catch {
      throw new ApiError(401, 'SESSION_EXPIRED', 'Your session has expired. Please sign in again.');
    }
    result = await attempt();
  }
  return parseBody<T>(result.status, result.text);
}
