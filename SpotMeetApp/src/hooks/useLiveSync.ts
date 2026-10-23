import { useEffect, useRef } from 'react';
import { useIsFocused } from '@react-navigation/native';
import { subscribeSync, subscribeVisibility, SyncEventType, SyncEvent, isAppVisible } from '../utils/syncBus';

export interface LiveSyncOptions {
  /**
   * Silent refresh interval in milliseconds while the screen is focused.
   * Default: 4000 (4 seconds). Set to 0 to disable periodic polling and rely on events only.
   */
  intervalMs?: number;
  /**
   * Event types this hook should react to instantly.
   * Default: ['*'] (all events and visibility restores).
   */
  eventTypes?: (SyncEventType | '*')[];
  /**
   * Enables or disables active synchronization dynamically.
   */
  enabled?: boolean;
}

// Periodic and event-driven refreshes share one in-flight callback.
export function useLiveSync(
  onSync: (event?: SyncEvent) => void | Promise<void>,
  options: LiveSyncOptions = {}
) {
  const {
    intervalMs = 4000,
    eventTypes = ['*'],
    enabled = true,
  } = options;

  const isFocused = useIsFocused();
  const onSyncRef = useRef(onSync);
  onSyncRef.current = onSync;

  const isSyncingRef = useRef(false);
  const pendingRef = useRef<SyncEvent | true | null>(null);
  const runnerRef = useRef<((event?: SyncEvent, force?: boolean) => Promise<void>) | null>(null);
  const wasEnabledRef = useRef(enabled);

  // Serializes the event list for a stable useEffect dependency
  const eventTypesKey = eventTypes.join(',');

  useEffect(() => {
    const resumed = enabled && !wasEnabledRef.current;
    wasEnabledRef.current = enabled;
    if (!enabled || !isFocused) return;
    let active = true;

    const runSync = async (event?: SyncEvent, force = false) => {
      if (!active || !isAppVisible()) return;
      if (isSyncingRef.current) {
        if (event || force) pendingRef.current = event || true;
        return;
      }
      try {
        isSyncingRef.current = true;
        await onSyncRef.current(event);
      } catch {
        // Background failures must stay silent to avoid interrupting the UX
      } finally {
        isSyncingRef.current = false;
        const pending = pendingRef.current;
        pendingRef.current = null;
        if (pending && runnerRef.current) void runnerRef.current(pending === true ? undefined : pending, true);
      }
    };
    runnerRef.current = runSync;

    const subscribedTypes = eventTypes.includes('*') ? ['*' as const]
      : [...new Set([...eventTypes, 'VISIBILITY_RESTORED' as const])];
    const unsubscribes = subscribedTypes.map((type) =>
      subscribeSync(type, (event) => {
        void runSync(event);
      })
    );

    let timer: ReturnType<typeof setInterval> | null = null;
    const stopTimer = () => { if (timer !== null) clearInterval(timer); timer = null; };
    const startTimer = () => {
      if (active && isAppVisible() && intervalMs > 0 && timer === null) {
        timer = setInterval(() => { void runSync(); }, intervalMs);
      }
    };
    const unsubscribeVisibility = subscribeVisibility(visible => { if (visible) startTimer(); else stopTimer(); });
    startTimer();
    if (resumed) void runSync(undefined, true);

    return () => {
      active = false;
      stopTimer();
      unsubscribeVisibility();
      if (runnerRef.current === runSync) runnerRef.current = null;
      pendingRef.current = null;
      unsubscribes.forEach((unsub) => unsub());
    };
  }, [enabled, isFocused, intervalMs, eventTypesKey]);
}
