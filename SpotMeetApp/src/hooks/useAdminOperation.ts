import { useEffect, useRef, useState } from 'react';
import { useIsFocused } from '@react-navigation/native';
import { useAuth } from '../context/AuthContext';
import { API_BASE_URL } from '../config/api';
import { confirmAction } from '../utils/alerts';
import { isAppVisible, subscribeVisibility } from '../utils/syncBus';
import { apiError, isAdminRole } from '../utils/adminData';

interface Operation { path: string; method: string; body?: unknown; accept?: (data: unknown) => boolean; onSuccess?: (data: unknown) => void | Promise<void>; onDenied?: () => void; }

export function useAdminOperation(contextKey: string, cancelReads?: () => void) {
  const { token, role, getSession } = useAuth();
  const version = getSession().version, renderedApi = API_BASE_URL;
  const focused = useIsFocused();
  const mounted = useRef(false);
  const scope = useRef({ token, role, contextKey, focused });
  scope.current = { token, role, contextKey, focused };
  const write = useRef<object | null>(null), dialog = useRef<object | null>(null), executingDialog = useRef<object | null>(null);
  const generation = useRef(0);
  const [busy, setBusy] = useState(false), [confirming, setConfirming] = useState(false), [error, setError] = useState('');
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; write.current = null; dialog.current = null; }; }, []);
  useEffect(() => { write.current = null; dialog.current = null; executingDialog.current = null; setBusy(false); setConfirming(false); setError(''); }, [token, role, version]);
  useEffect(() => {
    generation.current += 1;
    const unsubscribe = subscribeVisibility(visible => {
      if (!visible) generation.current += 1;
    });
    return unsubscribe;
  }, [token, role, version, contextKey, focused, renderedApi]);
  const isCurrent = () => mounted.current && !!token && isAdminRole(role) && focused && scope.current.focused && isAppVisible()
    && scope.current.token === token && scope.current.role === role && scope.current.contextKey === contextKey
    && getSession().token === token && getSession().version === version && API_BASE_URL === renderedApi;
  const blocked = () => !!write.current || !!dialog.current;

  const execute = async (operation: Operation) => {
    if (!isCurrent() || write.current || (dialog.current && executingDialog.current !== dialog.current)) return;
    const lock = {}, started = generation.current; write.current = lock; cancelReads?.(); setBusy(true); setError('');
    const current = () => isCurrent() && write.current === lock && generation.current === started;
    try {
      const res = await fetch(`${API_BASE_URL}${operation.path}`, { method: operation.method, headers: { Authorization: `Bearer ${token}`,
        ...(operation.body === undefined ? {} : { 'Content-Type': 'application/json' }) },
        ...(operation.body === undefined ? {} : { body: JSON.stringify(operation.body) }) });
      const text = await res.text();
      if (!current()) return;
      if (!res.ok) { setError(apiError(res, text)); if (res.status === 403) operation.onDenied?.(); return; }
      let data: unknown; try { data = JSON.parse(text); } catch { data = null; }
      if (operation.accept && !operation.accept(data)) { setError('O servidor retornou uma resposta inválida. Atualize os dados antes de tentar novamente.'); return; }
      await operation.onSuccess?.(data);
    } catch { if (current()) setError('Não foi possível conectar ao servidor. Tente novamente.'); }
    finally { if (write.current === lock) { write.current = null; if (mounted.current) setBusy(false); } }
  };
  const confirm = (title: string, message: string, onConfirm: () => void | Promise<void>, label = 'Confirmar', destructive = false) => {
    if (!isCurrent() || blocked()) return;
    const lock = {}, started = generation.current; dialog.current = lock; cancelReads?.(); setConfirming(true); setError('');
    const closed = () => { if (dialog.current === lock) { dialog.current = null; executingDialog.current = null; if (mounted.current) setConfirming(false); } };
    confirmAction(title, message, async () => {
      if (dialog.current !== lock || !isCurrent() || generation.current !== started || executingDialog.current === lock) return;
      executingDialog.current = lock;
      try { await onConfirm(); } finally { if (executingDialog.current === lock) executingDialog.current = null; }
    }, label, destructive, closed);
  };
  return { busy, confirming, error, execute, confirm, isCurrent, blocked, clearError: () => { if (isCurrent()) setError(''); } };
}
