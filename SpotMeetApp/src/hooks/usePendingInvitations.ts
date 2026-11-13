import { useCallback, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { API_BASE_URL } from '../config/api';
import { useLiveSync } from './useLiveSync';

/** Number of meeting invitations without an answer, shown on the Reuniões tab. Refreshes every 30 seconds and after any meeting change. */
export function usePendingInvitations(): number {
  const { token } = useAuth();
  const [count, setCount] = useState(0);
  const load = useCallback(async () => {
    if (!token) return;
    try {
      const res = await fetch(`${API_BASE_URL}/meetings/invitations/pending`, { headers: { Authorization: `Bearer ${token}` } });
      if (!res.ok) return;
      const data: unknown = await res.json();
      const value = data && typeof data === 'object' ? (data as { count?: unknown }).count : null;
      if (typeof value === 'number' && Number.isSafeInteger(value) && value >= 0) setCount(value);
    } catch { /* The next refresh tries again. */ }
  }, [token]);
  useLiveSync(load, { intervalMs: 30000, enabled: !!token, eventTypes: ['MEETINGS_MUTATED', 'VISIBILITY_RESTORED'] });
  return count;
}
