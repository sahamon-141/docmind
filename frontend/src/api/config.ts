// Empty means "same origin" (Vite proxy in development, nginx in the Docker image).
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '');

export const apiUrl = (path: string) => `${API_BASE_URL}/api/v1${path}`;

// Must match CookieCsrfGuard.HEADER on the backend.
export const CSRF_HEADER = 'X-DocMind-CSRF';
