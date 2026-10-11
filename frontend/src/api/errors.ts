export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly data: unknown;

  constructor(status: number, code: string, message: string, data?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.data = data;
  }

  /** Field -> message map for VALIDATION_FAILED responses. */
  get fieldErrors(): Record<string, string> {
    if (this.code !== 'VALIDATION_FAILED' || typeof this.data !== 'object' || this.data === null) return {};
    return this.data as Record<string, string>;
  }
}

export const isApiError = (e: unknown): e is ApiError => e instanceof ApiError;

export function errorMessage(e: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (isApiError(e)) {
    if (e.status === 0) return 'Cannot reach the DocMind server. Check your connection.';
    return e.message || fallback;
  }
  if (e instanceof DOMException && e.name === 'AbortError') return 'Request cancelled.';
  return fallback;
}
