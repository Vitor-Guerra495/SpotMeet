import { useCallback, useEffect, useMemo, useRef } from 'react';
import { useIsFocused } from '@react-navigation/native';
import { useAuth } from '../context/AuthContext';
import { API_BASE_URL } from '../config/api';
import { isAppVisible, subscribeVisibility } from '../utils/syncBus';

interface RefreshContext {
  signal: AbortSignal;
  isCurrent: () => boolean;
  isActive: () => boolean;
  isCurrentScope: () => boolean;
}

export function useRefreshRequest<T = void>(contextKey: string | number | null = null) {
  const { token, getSession } = useAuth();
  const isFocused = useIsFocused();
  const mounted = useRef(false);
  const scope = useRef({ token, contextKey, isFocused });
  scope.current = { token, contextKey, isFocused };
  const request = useRef<{ controller: AbortController; promise: Promise<T | undefined> } | null>(null);
  const cancel = useCallback(() => { request.current?.controller.abort(); }, []);
  const renderedVersion = getSession().version;
  const renderedApi = API_BASE_URL;
  const isCurrentScope = useCallback(() => mounted.current && !!token
    && scope.current.token === token && scope.current.contextKey === contextKey
    && API_BASE_URL === renderedApi && getSession().token === token && getSession().version === renderedVersion,
  [token, contextKey, getSession, renderedVersion, renderedApi]);
  const isActive = useCallback(() => isCurrentScope() && isFocused && scope.current.isFocused && isAppVisible(),
    [isCurrentScope, isFocused]);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; cancel(); };
  }, [cancel]);

  useEffect(() => {
    const unsubscribe = subscribeVisibility(visible => { if (!visible) cancel(); });
    return () => { cancel(); unsubscribe(); };
  }, [token, contextKey, isFocused, renderedVersion, renderedApi, cancel]);

  const run = useCallback(async function runRequest(task: (context: RefreshContext) => Promise<T | undefined>): Promise<T | undefined> {
    if (!isActive()) return;
    if (request.current) {
      if (!request.current.controller.signal.aborted) return request.current.promise;
      await request.current.promise.catch(() => undefined);
      return runRequest(task);
    }

    const controller = new AbortController();
    const isCurrent = () => isActive() && !controller.signal.aborted;
    const timeout = setTimeout(() => controller.abort(), 15000);
    const pending: { controller: AbortController; promise: Promise<T | undefined> } = {
      controller,
      promise: Promise.resolve().then(() => isCurrent() ? task({ signal: controller.signal, isCurrent, isActive, isCurrentScope }) : undefined)
        .finally(() => {
          controller.abort();
          clearTimeout(timeout);
          if (request.current === pending) request.current = null;
        }),
    };
    request.current = pending;
    return pending.promise;
  }, [isActive, isCurrentScope]);

  return useMemo(() => ({ run, cancel, isActive, isCurrentScope }), [run, cancel, isActive, isCurrentScope]);
}
