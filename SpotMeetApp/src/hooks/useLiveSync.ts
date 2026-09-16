import { useEffect, useRef } from 'react';
import { useIsFocused } from '@react-navigation/native';
import { subscribeSync, SyncEventType, SyncEvent, isAppVisible } from '../utils/syncBus';

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

/**
 * Real-time refresh hook for active screens on Web and Mobile.
 *
 * Features:
 * - Runs silent, non-intrusive polling while the screen is focused.
 * - Pauses automatically when the user leaves the screen, minimizes the app or switches browser tabs.
 * - Reacts instantly to local bus events and to BroadcastChannel events from other Web tabs.
 * - Avoids concurrent calls when a previous request is still in flight.
 */
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

  // Serializes the event list for a stable useEffect dependency
  const eventTypesKey = eventTypes.join(',');

  useEffect(() => {
    if (!enabled || !isFocused) return;

    const runSync = async (event?: SyncEvent) => {
      if (!isAppVisible()) return;
      if (isSyncingRef.current) return;
      try {
        isSyncingRef.current = true;
        await onSyncRef.current(event);
      } catch {
        // Background failures must stay silent to avoid interrupting the UX
      } finally {
        isSyncingRef.current = false;
      }
    };

    // 1. Subscribe to local and multi-tab events
    const unsubscribes = eventTypes.map((type) =>
      subscribeSync(type, (event) => {
        runSync(event);
      })
    );

    // 2. Silent periodic heartbeat while focused
    let timer: any = null;
    if (intervalMs > 0) {
      timer = setInterval(() => {
        runSync();
      }, intervalMs);
    }

    return () => {
      if (timer) clearInterval(timer);
      unsubscribes.forEach((unsub) => unsub());
    };
  }, [enabled, isFocused, intervalMs, eventTypesKey]);
}
