import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { onAuthEvent, tokenStore } from '../api/client';
import { authApi } from '../api/endpoints';
import type { LoginRequest, RegisterRequest, User } from '../api/types';
import { AuthContext, type AuthState } from './auth-context';

const CHANNEL = 'docmind-auth';

/**
 * Holds the signed-in user. On load it tries to restore the session from the HttpOnly refresh
 * cookie; the access token itself stays inside the API client's memory.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<AuthState['status']>('loading');

  const clearSession = useCallback(() => {
    tokenStore.set(null);
    setUser(null);
    setStatus('anonymous');
    queryClient.clear();
  }, [queryClient]);

  // restore session from the refresh cookie on first load
  useEffect(() => {
    let cancelled = false;
    authApi
      .restore()
      .then((auth) => {
        if (cancelled) return;
        setUser(auth.user);
        setStatus('authenticated');
      })
      .catch(() => {
        if (!cancelled) setStatus('anonymous');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // keep state in sync with refreshes/expiry performed by the API client
  useEffect(
    () =>
      onAuthEvent((event) => {
        if (event.type === 'refreshed') {
          setUser(event.auth.user);
          setStatus('authenticated');
        } else {
          clearSession();
        }
      }),
    [clearSession],
  );

  // sign out other tabs when this one signs out
  useEffect(() => {
    if (typeof BroadcastChannel === 'undefined') return;
    const channel = new BroadcastChannel(CHANNEL);
    channel.onmessage = (e) => {
      if (e.data === 'logout') clearSession();
    };
    return () => channel.close();
  }, [clearSession]);

  const login = useCallback(async (body: LoginRequest) => {
    const auth = await authApi.login(body);
    setUser(auth.user);
    setStatus('authenticated');
  }, []);

  const register = useCallback(async (body: RegisterRequest) => {
    const auth = await authApi.register(body);
    setUser(auth.user);
    setStatus('authenticated');
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      clearSession();
      if (typeof BroadcastChannel !== 'undefined') {
        const channel = new BroadcastChannel(CHANNEL);
        channel.postMessage('logout');
        channel.close();
      }
    }
  }, [clearSession]);

  const value = useMemo<AuthState>(
    () => ({ user, status, login, register, logout, setUser, signOutLocally: clearSession }),
    [user, status, login, register, logout, clearSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
