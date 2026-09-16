import { useEffect } from 'react';

/**
 * Toast variants. The colour and icon of the notification come from this.
 */
export type ToastVariant = 'success' | 'error' | 'warning' | 'info';

export interface ToastPayload {
  id: number;
  title: string;
  message: string;
  variant: ToastVariant;
}

type ToastHandler = (toast: ToastPayload) => void;

let handler: ToastHandler | null = null;
let nextId = 1;

/**
 * The ToastHost registers itself here so that plain functions (outside React
 * components) can raise a notification without needing a hook.
 */
export function setToastHandler(fn: ToastHandler | null): void {
  handler = fn;
}

export function isToastHostMounted(): boolean {
  return handler !== null;
}

/**
 * Infers the variant from the title used by the existing call sites
 * ("Erro", "Sucesso", "Atenção"...), so the 140+ existing calls keep working unchanged.
 */
export function inferVariant(title: string): ToastVariant {
  const t = title.toLowerCase();
  if (t.includes('sucesso') || t.includes('enviad') || t.includes('aprovad') || t.includes('atualizad')) {
    return 'success';
  }
  if (t.includes('erro') || t.includes('falha') || t.includes('inválid') || t.includes('invalid')
      || t.includes('negado') || t.includes('divergente') || t.includes('fraca') || t.includes('não ')) {
    return 'error';
  }
  if (t.includes('atenção') || t.includes('atencao') || t.includes('aviso') || t.includes('confirm')) {
    return 'warning';
  }
  return 'info';
}

export function showToast(title: string, message: string, variant?: ToastVariant): boolean {
  if (!handler) return false;
  handler({ id: nextId++, title, message, variant: variant || inferVariant(title) });
  return true;
}

/** Clears the handler when the host unmounts. */
export function useToastCleanup(): void {
  useEffect(() => () => setToastHandler(null), []);
}
