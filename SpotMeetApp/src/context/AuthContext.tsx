import React, { createContext, useCallback, useContext, useRef, useState } from 'react';

// Types

export interface AuthState {
  token: string | null;
  role: string | null;
  name: string | null;
  email: string | null;
  userId: number | null;
  emailVerified: boolean | null;
}

export interface AuthSession {
  token: string;
  role: string;
  name: string;
  email?: string | null;
  userId?: number | null;
  emailVerified?: boolean | null;
}

export interface SessionSnapshot {
  token: string | null;
  version: number;
}

export interface AuthSessionEvent {
  state: AuthState;
  version: number;
  reason: 'signIn' | 'signOut' | 'update';
}

type SessionListener = (event: AuthSessionEvent) => void | Promise<void>;

interface AuthContextType extends AuthState {
  signIn: (session: AuthSession) => void;
  signOut: () => Promise<void>;
  updateSession: (partial: Partial<AuthState>) => void;
  isAuthenticated: boolean;
  getSession: () => SessionSnapshot;
  subscribeSession: (listener: SessionListener) => () => void;
}

const EMPTY_STATE: AuthState = {
  token: null,
  role: null,
  name: null,
  email: null,
  userId: null,
  emailVerified: null,
};

// Context

const AuthContext = createContext<AuthContextType | null>(null);

// Provider

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [auth, setAuthState] = useState<AuthState>(EMPTY_STATE);
  const authRef = useRef<AuthState>(EMPTY_STATE);
  const sessionVersion = useRef(0);
  const listeners = useRef(new Set<SessionListener>());
  const renderedVersion = sessionVersion.current;

  const notifySession = useCallback(async (reason: AuthSessionEvent['reason']) => {
    const event = { state: authRef.current, version: sessionVersion.current, reason };
    await Promise.allSettled([...listeners.current].map(listener => {
      try { return listener(event); } catch { return undefined; }
    }));
  }, []);

  const subscribeSession = useCallback((listener: SessionListener) => {
    listeners.current.add(listener);
    return () => { listeners.current.delete(listener); };
  }, []);

  const signIn = useCallback((session: AuthSession) => {
    sessionVersion.current += 1;
    authRef.current = {
      token: session.token,
      role: session.role,
      name: session.name,
      email: session.email || null,
      userId: session.userId || null,
      emailVerified: session.emailVerified ?? null,
    };
    setAuthState(authRef.current);
    void notifySession('signIn');
  }, [notifySession]);

  const signOut = useCallback(() => {
    sessionVersion.current += 1;
    authRef.current = EMPTY_STATE;
    setAuthState(EMPTY_STATE);
    return notifySession('signOut');
  }, [notifySession]);

  const getSession = useCallback((): SessionSnapshot => ({
    token: authRef.current.token, version: sessionVersion.current,
  }), []);

  // Merges partial changes (e.g. new name, new token/email) into the current session
  const updateSession = (partial: Partial<AuthState>) => {
    if (!authRef.current.token || renderedVersion !== sessionVersion.current) return;
    const next = { ...authRef.current, ...partial };
    if (next.token !== authRef.current.token) sessionVersion.current += 1;
    authRef.current = next.token === null ? EMPTY_STATE : next;
    setAuthState(authRef.current);
    void notifySession(next.token === null ? 'signOut' : 'update');
  };

  return (
    <AuthContext.Provider
      value={{
        ...auth,
        signIn,
        signOut,
        updateSession,
        isAuthenticated: auth.token !== null,
        getSession,
        subscribeSession,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

// Hook

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth deve ser usado dentro de um AuthProvider.');
  }
  return ctx;
}
