import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import { AppState, Platform } from 'react-native';
import { useAuth, AuthState, AuthSessionEvent, SessionSnapshot } from './AuthContext';
import { API_BASE_URL } from '../config/api';
import { showAlert } from '../utils/alerts';
import {
  authenticateBiometricDevice, BiometricConfig, clearBiometricSession, getBiometricAvailability,
  isBiometricDelay, needsBiometricLock, readBiometricConfig, readBiometricSession,
  saveBiometricRearm, saveBiometricSession, StoredBiometricSession, takeBiometricRearm,
} from '../utils/biometricSession';

interface BiometricState {
  ready: boolean;
  enabled: boolean;
  locked: boolean;
  covered: boolean;
  offered: boolean;
  busy: boolean;
  delay: number;
  error: string;
}
interface BiometricContextType extends BiometricState {
  enable: () => Promise<void>;
  disable: () => Promise<void>;
  unlock: () => Promise<void>;
  dismissOffer: () => void;
  setDelay: (delay: number) => Promise<void>;
  returnToLogin: () => Promise<void>;
  allowExternalScreen: () => () => void;
  keepForPasswordChange: () => Promise<void>;
  confirmIdentity: () => Promise<boolean>;
}
interface Operation { epoch: number; session: SessionSnapshot; apiBaseUrl: string; }

// Gallery, camera and crop screens send the app to the background; leaving through them only locks after this long.
const EXTERNAL_SCREEN_GRACE_MS = 300000;

const INITIAL_STATE: BiometricState = {
  ready: false, enabled: false, locked: false, covered: false,
  offered: false, busy: false, delay: 0, error: '',
};
const BiometricContext = createContext<BiometricContextType | null>(null);

export function BiometricProvider({ children }: { children: React.ReactNode }) {
  const auth = useAuth();
  const renderedSession = auth.getSession();
  const [state, setState] = useState<BiometricState>(INITIAL_STATE);
  const stateRef = useRef(state);
  const authRef = useRef<AuthState>(auth);
  const mounted = useRef(false);
  const epoch = useRef(0);
  const config = useRef<BiometricConfig | null>(null);
  const pending = useRef<Operation | null>(null);
  const profileRequest = useRef<AbortController | null>(null);
  const restoring = useRef(false);
  const backgroundAt = useRef<number | null>(null);
  const externalScreens = useRef(0);
  const awayInExternalScreen = useRef(false);
  const rearmPending = useRef(false);
  authRef.current = auth;

  const isRenderedSession = () => {
    const session = auth.getSession();
    return session.token === renderedSession.token && session.version === renderedSession.version;
  };

  const publish = (partial: Partial<BiometricState>) => {
    if (!mounted.current) return;
    stateRef.current = { ...stateRef.current, ...partial };
    setState(stateRef.current);
  };
  const invalidate = () => {
    epoch.current += 1;
    profileRequest.current?.abort();
    profileRequest.current = null;
  };
  const current = (operation: Operation) => {
    const session = auth.getSession();
    return mounted.current && operation.epoch === epoch.current && API_BASE_URL === operation.apiBaseUrl
      && session.token === operation.session.token && session.version === operation.session.version;
  };
  const begin = () => {
    if (!mounted.current || pending.current || AppState.currentState === 'background') return null;
    const operation = { epoch: epoch.current, session: auth.getSession(), apiBaseUrl: API_BASE_URL };
    pending.current = operation;
    publish({ busy: true, error: '' });
    return operation;
  };
  const finish = (operation: Operation) => {
    if (pending.current !== operation) return;
    pending.current = null;
    publish({ busy: false });
  };
  const savedSession = (apiBaseUrl: string): StoredBiometricSession | null => {
    const session = authRef.current;
    if (!session.token || !Number.isSafeInteger(session.userId) || session.userId! <= 0) return null;
    return { token: session.token, userId: session.userId!, apiBaseUrl };
  };
  const endSessionWithNotice = async (message: string) => {
    const removal = auth.signOut();
    const ended = auth.getSession();
    await removal;
    const session = auth.getSession();
    if (mounted.current && session.token === null && session.version === ended.version) {
      showAlert('Sessão encerrada', message);
    }
  };

  const confirmSession = async (session: StoredBiometricSession, operation: Operation) => {
    if (!current(operation) || session.apiBaseUrl !== operation.apiBaseUrl) return null;
    const controller = new AbortController();
    profileRequest.current = controller;
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
      const response = await fetch(`${operation.apiBaseUrl}/users/me`, {
        headers: { Authorization: `Bearer ${session.token}` }, signal: controller.signal,
      });
      if (!current(operation)) return null;
      if ([401, 403, 404].includes(response.status)) {
        await endSessionWithNotice('Sua sessão foi encerrada. Entre novamente.');
        return null;
      }
      if (!response.ok) throw new Error('session-unavailable');
      const profile = await response.json();
      if (!current(operation)) return null;
      if (!profile || profile.id !== session.userId || typeof profile.name !== 'string'
          || typeof profile.email !== 'string' || !['USER', 'ADMIN', 'SYSADMIN'].includes(profile.role)) {
        await auth.signOut();
        return null;
      }
      return { token: session.token, userId: profile.id as number, name: profile.name as string,
        email: profile.email as string, role: profile.role as string, emailVerified: true };
    } finally {
      clearTimeout(timeout);
      if (profileRequest.current === controller) profileRequest.current = null;
    }
  };

  useEffect(() => {
    mounted.current = true;
    const clearStored = async (version: number) => {
      const cleared = await clearBiometricSession();
      if (!cleared && mounted.current && auth.getSession().version === version) {
        showAlert('Armazenamento do aparelho', 'Não foi possível remover completamente a sessão guardada. Tente sair novamente antes de fechar o app.');
      }
    };
    const onSession = (event: AuthSessionEvent) => {
      const previous = authRef.current;
      authRef.current = event.state;
      if (restoring.current) return;
      if (event.reason === 'update' && event.state.token && event.state.userId === previous.userId
          && config.current && config.current.apiBaseUrl === API_BASE_URL) {
        if (event.state.token === previous.token) return;
        invalidate();
        const version = epoch.current;
        const expected = { token: event.state.token, version: event.version };
        const stored = savedSession(API_BASE_URL);
        const isCurrent = () => mounted.current && epoch.current === version && API_BASE_URL === config.current?.apiBaseUrl
          && auth.getSession().token === expected.token && auth.getSession().version === expected.version;
        if (stored) void saveBiometricSession(stored, config.current, isCurrent).catch(async () => {
          if (isCurrent()) await endSessionWithNotice('Entre novamente para confirmar a sessão neste aparelho.');
        });
        return;
      }
      if (event.reason === 'update' && event.state.token && !config.current) return;
      invalidate();
      config.current = null;
      backgroundAt.current = null;
      awayInExternalScreen.current = false;
      rearmPending.current = false;
      publish({ ...INITIAL_STATE, ready: true, busy: !!pending.current });
      const cleared = clearStored(event.version);
      if (event.reason === 'signIn' && Platform.OS !== 'web') {
        // A login right after a password change turns the biometrics back on (with one fingerprint check) instead of offering it again.
        const version = epoch.current;
        void takeBiometricRearm().catch(() => null).then(rearm => {
          if (!mounted.current || epoch.current !== version) return;
          if (rearm && rearm.userId === event.state.userId && rearm.apiBaseUrl === API_BASE_URL) {
            rearmPending.current = true;
            publish({ delay: rearm.delay });
          } else publish({ offered: true });
        });
      }
      return cleared;
    };
    const unsubscribe = auth.subscribeSession(onSession);
    const startupEpoch = epoch.current;
    void readBiometricConfig().then(async stored => {
      if (!mounted.current || epoch.current !== startupEpoch) return;
      if (auth.getSession().token) { publish({ ready: true }); return; }
      if (stored && stored.apiBaseUrl === API_BASE_URL) {
        config.current = stored;
        publish({ ready: true, enabled: true, locked: true, delay: stored.delay, covered: AppState.currentState !== 'active' });
      } else {
        await clearBiometricSession();
        if (mounted.current && epoch.current === startupEpoch) publish({ ready: true });
      }
    }).catch(() => {
      if (mounted.current && epoch.current === startupEpoch) publish({ ready: true,
        error: 'Não foi possível consultar a sessão guardada. Entre com e-mail e senha.' });
    });
    const subscription = AppState.addEventListener('change', next => {
      publish({ covered: stateRef.current.enabled && next !== 'active' });
      if (next === 'background') {
        if (backgroundAt.current === null) {
          backgroundAt.current = Date.now();
          awayInExternalScreen.current = externalScreens.current > 0;
        }
        if (pending.current) invalidate();
        if (stateRef.current.enabled && stateRef.current.delay === 0 && !awayInExternalScreen.current) publish({ locked: true });
      } else if (next === 'active' && backgroundAt.current !== null) {
        const since = backgroundAt.current;
        const delay = awayInExternalScreen.current ? Math.max(stateRef.current.delay, EXTERNAL_SCREEN_GRACE_MS) : stateRef.current.delay;
        backgroundAt.current = null;
        awayInExternalScreen.current = false;
        if (stateRef.current.enabled && needsBiometricLock(delay, since, Date.now())) {
          publish({ locked: true });
        }
      }
    });
    return () => {
      mounted.current = false;
      invalidate();
      unsubscribe();
      subscription.remove();
    };
  }, [auth.getSession, auth.signIn, auth.signOut, auth.subscribeSession]);

  const enable = async () => {
    if (!isRenderedSession() || !auth.getSession().token || stateRef.current.enabled || stateRef.current.locked) return;
    const operation = begin();
    if (!operation) return;
    let saved = false;
    try {
      const availability = await getBiometricAvailability();
      if (!current(operation)) return;
      if (!availability.available) { publish({ error: availability.message }); return; }
      if (!await authenticateBiometricDevice(true)) {
        if (current(operation)) publish({ error: 'Autenticação cancelada ou não confirmada. A biometria permanece desligada.' });
        return;
      }
      if (!current(operation)) return;
      const session = savedSession(operation.apiBaseUrl);
      if (!session) { publish({ error: 'Não foi possível confirmar a conta. Entre novamente antes de habilitar a biometria.' }); return; }
      if (!await confirmSession(session, operation) || !current(operation)) return;
      const next = { apiBaseUrl: operation.apiBaseUrl, delay: stateRef.current.delay };
      saved = await saveBiometricSession(session, next, () => current(operation));
      if (saved && current(operation)) {
        config.current = next;
        publish({ enabled: true, offered: false, error: '' });
      }
    } catch {
      if (current(operation)) publish({ error: 'Não foi possível habilitar a biometria. Tente novamente; seu login continua ativo.' });
    } finally {
      if (!saved) await clearBiometricSession();
      finish(operation);
    }
  };

  const unlock = async () => {
    if (!isRenderedSession() || !stateRef.current.locked || !config.current) return;
    const operation = begin();
    if (!operation) return;
    const storedConfig = config.current;
    try {
      if (storedConfig.apiBaseUrl !== operation.apiBaseUrl) { await auth.signOut(); return; }
      if (!await authenticateBiometricDevice(false)) {
        if (current(operation)) publish({ error: 'Autenticação cancelada ou não confirmada. O aplicativo continua bloqueado.' });
        return;
      }
      if (!current(operation)) return;
      const stored = savedSession(operation.apiBaseUrl) ?? await readBiometricSession(storedConfig);
      if (!current(operation)) return;
      if (!stored) { await auth.signOut(); return; }
      const session = await confirmSession(stored, operation);
      if (!session || !current(operation)) return;
      restoring.current = true;
      try {
        if (!operation.session.token) auth.signIn(session);
        else auth.updateSession(session);
        operation.session = auth.getSession();
      } finally { restoring.current = false; }
      if (current(operation)) publish({ locked: false, offered: false, error: '' });
    } catch {
      if (current(operation)) publish({ error: 'Não foi possível desbloquear e confirmar a sessão. Verifique a conexão e tente novamente, ou entre com e-mail e senha.' });
    } finally { finish(operation); }
  };

  const disable = async () => {
    if (!isRenderedSession() || !auth.getSession().token || stateRef.current.locked || !stateRef.current.enabled) return;
    const operation = begin();
    if (!operation) return;
    try {
      // Turning the protection off asks for the same check that turned it on.
      if (!await authenticateBiometricDevice(false)) {
        if (current(operation)) publish({ error: 'Autenticação cancelada ou não confirmada. A biometria continua habilitada.' });
        return;
      }
      if (!current(operation)) return;
      if (!await clearBiometricSession()) throw new Error('storage-unavailable');
      if (current(operation)) { config.current = null; publish({ enabled: false, error: '' }); }
    } catch {
      if (current(operation)) publish({ error: 'Não foi possível remover a sessão guardada. Tente desabilitar novamente.' });
    } finally { finish(operation); }
  };

  const setDelay = async (delay: number) => {
    if (!isRenderedSession() || !isBiometricDelay(delay) || stateRef.current.locked || !auth.getSession().token) return;
    const operation = begin();
    if (!operation) return;
    try {
      if (stateRef.current.enabled) {
        const session = savedSession(operation.apiBaseUrl);
        const next = { apiBaseUrl: operation.apiBaseUrl, delay };
        if (!session || !await saveBiometricSession(session, next, () => current(operation))) return;
        if (current(operation)) config.current = next;
      }
      if (current(operation)) publish({ delay, error: '' });
    } catch {
      if (current(operation)) publish({ error: 'Não foi possível salvar o prazo do bloqueio. Tente novamente.' });
    } finally { finish(operation); }
  };

  const returnToLogin = async () => {
    if (!isRenderedSession()) return;
    invalidate();
    await auth.signOut();
  };
  const dismissOffer = () => {
    if (isRenderedSession() && !stateRef.current.busy) publish({ offered: false, error: '' });
  };
  // Marks a system screen opened by the app (photo picker, camera); returns the function that ends it.
  const allowExternalScreen = () => {
    externalScreens.current += 1;
    let ended = false;
    return () => { if (!ended) { ended = true; externalScreens.current -= 1; } };
  };
  // Called just before the logout that follows a password change.
  const keepForPasswordChange = async () => {
    const session = authRef.current;
    if (!stateRef.current.enabled || !config.current || !Number.isSafeInteger(session.userId) || session.userId! <= 0) return;
    try {
      await saveBiometricRearm({ userId: session.userId!, apiBaseUrl: config.current.apiBaseUrl, delay: stateRef.current.delay });
    } catch { /* Without the marker the next login simply offers the biometrics again. */ }
  };
  // Password and e-mail changes ask for the device check when the biometric lock is on.
  const confirmIdentity = async () => {
    if (Platform.OS === 'web' || !stateRef.current.enabled) return true;
    if (stateRef.current.busy || stateRef.current.locked) return false;
    try { return await authenticateBiometricDevice(false); } catch { return false; }
  };

  useEffect(() => {
    if (!rearmPending.current || !state.ready || state.busy || state.enabled || state.locked
        || !isRenderedSession() || !auth.getSession().token) return;
    rearmPending.current = false;
    void enable().then(() => {
      if (mounted.current && isRenderedSession() && auth.getSession().token && !stateRef.current.enabled) publish({ offered: true });
    });
  }, [state]);

  return <BiometricContext.Provider value={{ ...state, enable, disable, unlock, setDelay, dismissOffer, returnToLogin,
    allowExternalScreen, keepForPasswordChange, confirmIdentity }}>
    {children}
  </BiometricContext.Provider>;
}

export function useBiometric(): BiometricContextType {
  const context = useContext(BiometricContext);
  if (!context) throw new Error('useBiometric deve ser usado dentro de BiometricProvider.');
  return context;
}
