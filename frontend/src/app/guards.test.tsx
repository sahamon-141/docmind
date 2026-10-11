import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { tokenStore } from '../api/client';
import { AuthProvider } from '../providers/AuthProvider';
import { ToastProvider } from '../providers/ToastProvider';
import { PublicOnly, RequireAuth } from './guards';

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{JSON.stringify({ pathname: location.pathname, state: location.state })}</output>;
}

const currentLocation = () => JSON.parse(screen.getByTestId('location').textContent ?? '{}') as { pathname: string; state: unknown };

function renderAt(path: string) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ToastProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={[path]}>
            <LocationProbe />
            <Routes>
              <Route element={<RequireAuth />}>
                <Route path="/chat" element={<p>workspace</p>} />
              </Route>
              <Route element={<PublicOnly />}>
                <Route path="/login" element={<p>login page</p>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

const json = (body: unknown, status: number) => new Response(JSON.stringify(body), { status });

describe('route guards', () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    tokenStore.set(null);
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('redirects to /login when there is no session to restore', async () => {
    fetchMock.mockResolvedValue(json({ success: false, error: 'INVALID_REFRESH_TOKEN' }, 401));
    renderAt('/chat');
    expect(await screen.findByText('login page')).toBeInTheDocument();
    expect(currentLocation()).toEqual({ pathname: '/login', state: { from: '/chat' } });
  });

  it('restores the session from the refresh cookie and keeps signed-in users out of /login', async () => {
    fetchMock.mockResolvedValue(
      json(
        {
          success: true,
          data: { accessToken: 't', tokenType: 'Bearer', expiresIn: 900, user: { id: 1, username: 'ada', email: 'a@b.c', role: 'USER', createdAt: '' } },
        },
        200,
      ),
    );
    renderAt('/login');
    expect(await screen.findByText('workspace')).toBeInTheDocument();
    expect(currentLocation().pathname).toBe('/chat');
    expect(tokenStore.get()).toBe('t');
  });
});
