export interface Confirmation {
  title: string;
  message: string;
  confirmText: string;
  destructive: boolean;
  onConfirm: () => void | Promise<void>;
  onClosed: () => void;
  cancelText?: string;
  // Optional middle button that closes the dialog before running.
  extraText?: string;
  onExtra?: () => void;
}

const hosts: ((request: Confirmation) => void)[] = [];
let pending = false;

export function registerConfirmationHost(handler: (request: Confirmation) => void): () => void {
  hosts.push(handler);
  return () => { const index = hosts.indexOf(handler); if (index >= 0) hosts.splice(index, 1); };
}

export function requestConfirmation(request: Omit<Confirmation, 'onClosed'> & { onClosed?: () => void }): void {
  const host = hosts.at(-1);
  if (!host || pending) { request.onClosed?.(); return; }
  pending = true;
  let closed = false;
  const onClosed = () => {
    if (closed) return;
    closed = true; pending = false; request.onClosed?.();
  };
  try { host({ ...request, onClosed }); } catch { onClosed(); }
}
