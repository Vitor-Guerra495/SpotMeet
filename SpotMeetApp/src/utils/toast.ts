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

const hosts = new Map<ToastHandler, (id: number) => void>();
let nextId = 1;

/**
 * The ToastHost registers itself here so that plain functions (outside React
 * components) can raise a notification without needing a hook.
 */
export function registerToastHost(show: ToastHandler, dismiss: (id: number) => void): () => void {
  hosts.set(show, dismiss);
  return () => { hosts.delete(show); };
}

export function isToastHostMounted(): boolean {
  return hosts.size > 0;
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
  if (!hosts.size) return false;
  const toast = { id: nextId++, title, message, variant: variant || inferVariant(title) };
  [...hosts.keys()].forEach(show => show(toast));
  return true;
}

export function dismissToast(id: number): void {
  [...hosts.values()].forEach(dismiss => dismiss(id));
}
