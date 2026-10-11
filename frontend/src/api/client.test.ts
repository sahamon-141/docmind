import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiRequest, onAuthEvent, refreshSession, tokenStore } from './client';
import { ApiError } from './errors';

const envelope = (data: unknown, status = 200, error?: string) =>
  new Response(JSON.stringify({ success: status < 400, data, error, message: error, timestamp: '' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });

const authBody = (token: string) => ({
  accessToken: token,
  tokenType: 'Bearer',
  expiresIn: 900,
  user: { id: 1, username: 'ada', email: 'ada@example.com', role: 'USER', createdAt: '' },
});

describe('apiRequest', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    tokenStore.set('expired-token');
  });
  afterEach(() => vi.unstubAllGlobals());

  const authHeader = (call: number) => new Headers(fetchMock.mock.calls[call]?.[1]?.headers).get('Authorization');
  const url = (call: number) => String(fetchMock.mock.calls[call]?.[0]);

  it('refreshes once on 401 and retries the original request with the new token', async () => {
    fetchMock
      .mockResolvedValueOnce(envelope(null, 401, 'TOKEN_EXPIRED'))
      .mockResolvedValueOnce(envelope(authBody('fresh-token')))
      .mockResolvedValueOnce(envelope(['doc']));

    await expect(apiRequest<string[]>('/documents')).resolves.toEqual(['doc']);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(url(1)).toContain('/api/v1/auth/refresh');
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('X-DocMind-CSRF')).toBe('1');
    expect(fetchMock.mock.calls[1]?.[1]?.credentials).toBe('include');
    expect(authHeader(0)).toBe('Bearer expired-token');
    expect(authHeader(2)).toBe('Bearer fresh-token');
  });

  it('shares one refresh between concurrent 401 responses', async () => {
    let refreshCalls = 0;
    fetchMock.mockImplementation(async (input, init) => {
      if (String(input).includes('/auth/refresh')) {
        refreshCalls++;
        await new Promise((r) => setTimeout(r, 10));
        return envelope(authBody('fresh-token'));
      }
      const token = new Headers(init?.headers).get('Authorization');
      return token === 'Bearer fresh-token' ? envelope('ok') : envelope(null, 401, 'TOKEN_EXPIRED');
    });

    const results = await Promise.all([apiRequest('/a'), apiRequest('/b'), apiRequest('/c')]);
    expect(results).toEqual(['ok', 'ok', 'ok']);
    expect(refreshCalls).toBe(1);
  });

  it('does not loop: a 401 after a successful refresh is returned as an error', async () => {
    fetchMock
      .mockResolvedValueOnce(envelope(null, 401, 'TOKEN_EXPIRED'))
      .mockResolvedValueOnce(envelope(authBody('fresh-token')))
      .mockResolvedValueOnce(envelope(null, 401, 'INVALID_TOKEN'));

    await expect(apiRequest('/documents')).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('signals session expiry when the refresh token is rejected', async () => {
    const events: string[] = [];
    const unsubscribe = onAuthEvent((e) => events.push(e.type));
    fetchMock
      .mockResolvedValueOnce(envelope(null, 401, 'TOKEN_EXPIRED'))
      .mockResolvedValueOnce(envelope(null, 401, 'INVALID_REFRESH_TOKEN'));

    const error = await apiRequest('/documents').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe('SESSION_EXPIRED');
    expect(events).toEqual(['expired']);
    expect(tokenStore.get()).toBeNull();
    unsubscribe();
  });

  it('retries a refresh once after a concurrent-rotation conflict', async () => {
    fetchMock
      .mockResolvedValueOnce(envelope(null, 409, 'REFRESH_CONFLICT'))
      .mockResolvedValueOnce(envelope(authBody('fresh-token')));
    await expect(refreshSession()).resolves.toMatchObject({ accessToken: 'fresh-token' });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('surfaces validation errors with field messages', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ success: false, error: 'VALIDATION_FAILED', message: 'Validation failed', data: { email: 'Email must be valid' } }), {
        status: 400,
      }),
    );
    const error = (await apiRequest('/auth/register', { method: 'POST', body: {}, auth: false }).catch((e: unknown) => e)) as ApiError;
    expect(error.status).toBe(400);
    expect(error.fieldErrors).toEqual({ email: 'Email must be valid' });
  });

  it('maps network failures to a NETWORK_ERROR ApiError', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await expect(apiRequest('/documents')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });
});
