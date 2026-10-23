import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { File } from 'expo-file-system';
import { useAuth } from './AuthContext';
import { useBiometric } from './BiometricContext';
import { API_BASE_URL } from '../config/api';
import { parseProfilePhoto, type ProfilePhotoData } from '../utils/profilePhotoData';
import { pickProfilePhoto, releaseDraftPhoto, PhotoSelectionError, type DraftPhoto, type PhotoSource } from '../utils/profilePhotoPicker';
import { showAlert } from '../utils/alerts';

interface Scope { token: string; version: number; userId: number; role: string | null; api: string; }
interface Operation { scope: Scope; kind: 'load' | 'select' | 'save' | 'remove'; }
interface PhotoState { scope: Scope | null; photo: ProfilePhotoData | null; draft: DraftPhoto | null; busy: Operation['kind'] | null; error: string; loaded: boolean; }
interface ProfilePhotoContextType extends Omit<PhotoState, 'scope'> {
  refresh: () => Promise<void>;
  select: (source: PhotoSource) => Promise<void>;
  save: (options?: { quiet?: boolean }) => Promise<boolean>;
  remove: () => Promise<void>;
  discard: () => void;
  cancelRequest: () => void;
}
const EMPTY: PhotoState = { scope: null, photo: null, draft: null, busy: null, error: '', loaded: false };
const Context = createContext<ProfilePhotoContextType | null>(null);

function saveErrorMessage(error: unknown) {
  const reason = error instanceof Error ? error.message : '';
  if (reason === 'size') return 'A foto passou do limite de 1 MB. Escolha outra imagem.';
  if (reason === 'invalid') return 'O servidor não aceitou esta imagem. Escolha outra foto.';
  return 'Não foi possível salvar a foto. Verifique a conexão e tente novamente.';
}

export function ProfilePhotoProvider({ children }: { children: React.ReactNode }) {
  const auth = useAuth(), biometric = useBiometric();
  const [state, setState] = useState<PhotoState>(EMPTY);
  const stateRef = useRef(state), authRef = useRef(auth), bioRef = useRef(biometric);
  const mounted = useRef(false), visible = useRef(AppState.currentState === 'active');
  const pending = useRef<Operation | null>(null), request = useRef<AbortController | null>(null);
  const nativeBusy = useRef(false);
  const refreshQueued = useRef(false);
  authRef.current = auth; bioRef.current = biometric;

  const scope = (): Scope | null => {
    const current = authRef.current, session = current.getSession();
    return session.token && Number.isSafeInteger(current.userId) && current.userId! > 0
      ? { token: session.token, version: session.version, userId: current.userId!, role: current.role, api: API_BASE_URL } : null;
  };
  const same = (expected: Scope | null) => {
    const current = scope();
    return !!expected && !!current && expected.token === current.token && expected.version === current.version
      && expected.userId === current.userId && expected.role === current.role && expected.api === current.api;
  };
  const unlocked = () => visible.current && bioRef.current.ready && !bioRef.current.locked && !bioRef.current.covered && !bioRef.current.offered;
  const current = (operation: Operation, native = false) => mounted.current && pending.current === operation && same(operation.scope) && (native || unlocked());
  const publish = (partial: Partial<PhotoState>) => {
    if (mounted.current) { stateRef.current = { ...stateRef.current, ...partial }; setState(stateRef.current); }
  };
  const cancelRequest = useCallback(() => { request.current?.abort(); request.current = null; }, []);
  const begin = (kind: Operation['kind']) => {
    const snapshot = scope();
    if (!mounted.current || !snapshot || !unlocked() || (kind === 'select' && nativeBusy.current)) return null;
    if (pending.current && !same(pending.current.scope)) { cancelRequest(); pending.current = null; }
    if (pending.current) return null;
    if (!same(stateRef.current.scope)) {
      releaseDraftPhoto(stateRef.current.draft); publish(EMPTY);
    }
    const operation = { scope: snapshot, kind }; pending.current = operation;
    publish({ scope: snapshot, busy: kind, error: '' }); return operation;
  };
  const finish = (operation: Operation) => {
    if (pending.current === operation) {
      pending.current = null; publish({ busy: null });
      if (refreshQueued.current && unlocked()) { refreshQueued.current = false; void refresh(); }
    }
  };
  const call = async (operation: Operation, method: 'GET' | 'PUT' | 'DELETE', body?: FormData) => {
    const controller = new AbortController(); request.current = controller;
    const timer = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch(`${operation.scope.api}/users/me/photo`, {
        method, body, signal: controller.signal, headers: { Authorization: `Bearer ${operation.scope.token}` },
      });
      if (!current(operation) || controller.signal.aborted) return null;
      if (!response.ok) throw new Error(response.status === 413 ? 'size' : response.status === 400 ? 'invalid' : 'unavailable');
      if (method === 'DELETE') {
        if (response.status !== 204) throw new Error('invalid-response');
        return { userId: operation.scope.userId, imageBase64: null, version: null } as ProfilePhotoData;
      }
      const data = parseProfilePhoto(await response.json(), operation.scope.userId);
      if (!current(operation) || controller.signal.aborted) return null;
      if (method === 'PUT' && !data.imageBase64) throw new Error('invalid-response');
      return data;
    } finally { clearTimeout(timer); if (request.current === controller) request.current = null; }
  };
  const refresh = useCallback(async () => {
    if (!unlocked()) return;
    if (pending.current && same(pending.current.scope)) { refreshQueued.current = true; return; }
    const operation = begin('load'); if (!operation) return;
    refreshQueued.current = false;
    try { const photo = await call(operation, 'GET'); if (photo && current(operation)) publish({ photo, loaded: true }); }
    catch { if (current(operation)) publish({ error: 'Não foi possível carregar a foto. Tente novamente.' }); }
    finally { finish(operation); }
  }, []);
  const select = useCallback(async (source: PhotoSource) => {
    const operation = begin('select'); if (!operation) return;
    nativeBusy.current = true;
    const endExternalScreen = bioRef.current.allowExternalScreen();
    try {
      const draft = await pickProfilePhoto(source, () => current(operation, true));
      if (draft && current(operation, true)) { releaseDraftPhoto(stateRef.current.draft); publish({ draft }); }
      else releaseDraftPhoto(draft);
    } catch (error) {
      if (current(operation, true)) publish({ error: error instanceof PhotoSelectionError ? error.message : 'Não foi possível abrir ou preparar a foto. Tente novamente.' });
    } finally { endExternalScreen(); nativeBusy.current = false; finish(operation); }
  }, []);
  // quiet: the caller reports the result (profile and photo saved together).
  const save = useCallback(async (options?: { quiet?: boolean }) => {
    const draft = stateRef.current.draft;
    if (!draft || !same(stateRef.current.scope)) return false;
    const operation = begin('save'); if (!operation) return false;
    try {
      // The Expo fetch only uploads parts that expose bytes(), such as File; { uri } objects are rejected.
      const form = new FormData();
      form.append('photo', new File(draft.uri));
      const photo = await call(operation, 'PUT', form);
      if (photo && current(operation)) {
        publish({ photo, draft: null, loaded: true }); releaseDraftPhoto(draft);
        if (!options?.quiet) showAlert('Sucesso', 'Foto de perfil salva com sucesso.');
        return true;
      }
    } catch (error) {
      if (current(operation)) publish({ error: saveErrorMessage(error) });
    } finally { finish(operation); }
    return false;
  }, []);
  const remove = useCallback(async () => {
    const operation = begin('remove'); if (!operation) return;
    try {
      const photo = await call(operation, 'DELETE');
      if (photo && current(operation)) {
        releaseDraftPhoto(stateRef.current.draft); publish({ photo, draft: null, loaded: true });
        showAlert('Sucesso', 'Foto de perfil removida.');
      }
    } catch { if (current(operation)) publish({ error: 'Não foi possível remover a foto. Tente novamente.' }); }
    finally { finish(operation); }
  }, []);
  const discard = useCallback(() => {
    if (!same(stateRef.current.scope) || !unlocked() || pending.current) return;
    releaseDraftPhoto(stateRef.current.draft); publish({ draft: null, error: '' });
  }, []);

  useEffect(() => {
    mounted.current = true;
    const clear = () => { cancelRequest(); pending.current = null; refreshQueued.current = false; releaseDraftPhoto(stateRef.current.draft); publish(EMPTY); };
    const unsubscribe = auth.subscribeSession(() => { if (!same(stateRef.current.scope)) clear(); });
    const subscription = AppState.addEventListener('change', next => {
      visible.current = next === 'active';
      if (!visible.current) cancelRequest();
      publish({});
    });
    return () => { mounted.current = false; cancelRequest(); releaseDraftPhoto(stateRef.current.draft); unsubscribe(); subscription.remove(); };
  }, [auth.subscribeSession, cancelRequest]);
  useEffect(() => { if (biometric.locked || biometric.covered || biometric.offered) cancelRequest(); }, [biometric.locked, biometric.covered, biometric.offered, cancelRequest]);

  const allowed = same(state.scope) && unlocked();
  const display = allowed ? state : EMPTY;
  return <Context.Provider value={{ photo: display.photo, draft: display.draft, busy: display.busy, error: display.error, loaded: display.loaded,
    refresh, select, save, remove, discard, cancelRequest }}>
    {children}
  </Context.Provider>;
}

export function useProfilePhoto() {
  const context = useContext(Context);
  if (!context) throw new Error('useProfilePhoto deve ser usado dentro de ProfilePhotoProvider.');
  return context;
}
