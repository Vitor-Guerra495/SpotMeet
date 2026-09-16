import React, { createContext, useContext, useState } from 'react';

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

interface AuthContextType extends AuthState {
  signIn: (session: AuthSession) => void;
  signOut: () => void;
  updateSession: (partial: Partial<AuthState>) => void;
  isAuthenticated: boolean;
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

  const signIn = (session: AuthSession) => {
    setAuthState({
      token: session.token,
      role: session.role,
      name: session.name,
      email: session.email || null,
      userId: session.userId || null,
      emailVerified: session.emailVerified ?? null,
    });
  };

  const signOut = () => {
    setAuthState(EMPTY_STATE);
  };

  // Merges partial changes (e.g. new name, new token/email) into the current session
  const updateSession = (partial: Partial<AuthState>) => {
    setAuthState((prev) => ({ ...prev, ...partial }));
  };

  return (
    <AuthContext.Provider
      value={{
        ...auth,
        signIn,
        signOut,
        updateSession,
        isAuthenticated: auth.token !== null,
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
