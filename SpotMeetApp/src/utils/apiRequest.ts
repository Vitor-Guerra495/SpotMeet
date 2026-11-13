import { API_BASE_URL } from '../config/api';
import { errorText } from './meetings';

export type ApiResult = { ok: true; data: unknown } | { ok: false; status: number; error: string };

const GENERIC_ERROR = 'Não foi possível concluir a operação. Tente novamente.';

/** Authenticated call to the server that answers with the parsed body or the message to show. */
export async function apiRequest(token: string | null, path: string, method: 'GET' | 'POST' | 'PUT' = 'GET', body?: unknown): Promise<ApiResult> {
  try {
    const res = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: { Authorization: `Bearer ${token}`, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    const text = await res.text();
    if (!res.ok) return { ok: false, status: res.status, error: errorText(res.status, text, GENERIC_ERROR) };
    try { return { ok: true, data: text ? JSON.parse(text) as unknown : null }; } catch { return { ok: true, data: null }; }
  } catch {
    return { ok: false, status: 0, error: 'Não foi possível conectar ao servidor. Tente novamente.' };
  }
}
