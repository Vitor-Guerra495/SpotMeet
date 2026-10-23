import { useEffect, useLayoutEffect, useRef } from 'react';
import { AppState } from 'react-native';
import type { NavigationContainerRefWithCurrent, ParamListBase } from '@react-navigation/native';
import { useAuth } from '../../context/AuthContext';
import { API_BASE_URL } from '../../config/api';
import { showAlert } from '../../utils/alerts';
import { installSessionFetch } from '../../utils/sessionFetch';

type Props = { navigationRef: NavigationContainerRefWithCurrent<ParamListBase> };

export default function SessionGuard({ navigationRef }: Props) {
  const { token, getSession, signOut } = useAuth();
  const pendingReset = useRef<number | null>(null);

  useLayoutEffect(() => {
    let notification: ReturnType<typeof setTimeout> | undefined;
    const resetToLogin = () => {
      if (pendingReset.current === null || !navigationRef.isReady()) return;
      const session = getSession();
      if (session.token === null && session.version === pendingReset.current) {
        navigationRef.resetRoot({ index: 0, routes: [{ name: 'Auth', params: { mode: 'login' } }] });
      }
      pendingReset.current = null;
    };
    const removeReadyListener = navigationRef.addListener('ready', resetToLogin);
    const restoreFetch = installSessionFetch(() => API_BASE_URL, getSession, expected => {
      const current = getSession();
      if (current.token !== expected.token || current.version !== expected.version) return;
      signOut();
      const endedVersion = getSession().version;
      pendingReset.current = endedVersion;
      resetToLogin();
      notification = setTimeout(() => {
        const session = getSession();
        if (session.token === null && session.version === endedVersion) {
          showAlert('Sessão encerrada', 'Sua sessão foi encerrada. Entre novamente.');
        }
      }, 0);
    });
    return () => {
      restoreFetch();
      removeReadyListener();
      if (notification !== undefined) clearTimeout(notification);
    };
  }, [getSession, signOut, navigationRef]);

  useEffect(() => {
    if (!token) return;
    let state = AppState.currentState ?? 'active';
    let interval: ReturnType<typeof setInterval> | undefined;
    let pending: AbortController | undefined;
    const checkSession = async () => {
      if (state !== 'active' || pending || getSession().token !== token) return;
      const controller = new AbortController();
      pending = controller;
      const timeout = setTimeout(() => controller.abort(), 10000);
      try {
        await fetch(`${API_BASE_URL}/users/me`, {
          method: 'GET', headers: { Authorization: `Bearer ${token}` }, signal: controller.signal,
        });
      } catch {
        // Connection failures do not prove that the session has expired.
      } finally {
        clearTimeout(timeout);
        if (pending === controller) pending = undefined;
      }
    };
    const stopChecking = () => {
      if (interval !== undefined) clearInterval(interval);
      interval = undefined;
      pending?.abort();
      pending = undefined;
    };
    const startChecking = () => {
      if (state === 'active') interval = setInterval(checkSession, 15000);
    };
    startChecking();
    const subscription = AppState.addEventListener('change', next => {
      const wasActive = state === 'active';
      state = next;
      stopChecking();
      startChecking();
      if (next === 'active' && !wasActive) void checkSession();
    });
    return () => {
      stopChecking();
      subscription.remove();
    };
  }, [token, getSession]);

  return null;
}
