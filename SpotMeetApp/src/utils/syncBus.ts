import { Platform, AppState, AppStateStatus } from 'react-native';

export type SyncEventType =
  | 'ORGANIZATIONS_MUTATED'
  | 'MEMBERS_MUTATED'
  | 'REQUESTS_MUTATED'
  | 'COMMITTEES_MUTATED'
  | 'ADMIN_ORGS_MUTATED'
  | 'MONITORING_REFRESH'
  | 'PROFILE_MUTATED'
  | 'VISIBILITY_RESTORED';

export interface SyncEvent {
  type: SyncEventType;
  payload?: any;
  timestamp: number;
  originId: string;
}

type SyncListener = (event: SyncEvent) => void;

// Unique session/tab id to avoid reprocessing echoed events
const CURRENT_TAB_ID = `tab_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;

// Local subscribers
const listeners: Map<SyncEventType | '*', Set<SyncListener>> = new Map();

// Multi-tab broadcast channel for Web
let webBroadcastChannel: any = null;

const win: any = typeof globalThis !== 'undefined' ? globalThis : null;

if (Platform.OS === 'web' && win) {
  try {
    if (typeof win.BroadcastChannel === 'function') {
      webBroadcastChannel = new win.BroadcastChannel('spotmeet_sync_channel');
      webBroadcastChannel.onmessage = (ev: any) => {
        const event = ev.data as SyncEvent;
        if (event && event.type && event.originId !== CURRENT_TAB_ID) {
          dispatchLocal(event);
        }
      };
    } else if (typeof win.addEventListener === 'function') {
      // Fallback via storage event
      win.addEventListener('storage', (ev: any) => {
        if (ev.key === 'spotmeet_sync_event' && ev.newValue) {
          try {
            const event: SyncEvent = JSON.parse(ev.newValue);
            if (event && event.originId !== CURRENT_TAB_ID) {
              dispatchLocal(event);
            }
          } catch {
            // Silent on malformed JSON
          }
        }
      });
    }

    // Listener for tab visibility restore in the browser
    const doc: any = win.document;
    if (doc && typeof doc.addEventListener === 'function') {
      doc.addEventListener('visibilitychange', () => {
        if (doc.visibilityState === 'visible') {
          notifySync('VISIBILITY_RESTORED');
        }
      });
      if (typeof win.addEventListener === 'function') {
        win.addEventListener('focus', () => {
          notifySync('VISIBILITY_RESTORED');
        });
      }
    }
  } catch (e) {
    console.warn('[SyncBus] Web channel initialization failed:', e);
  }
} else {
  // Mobile: listener for return to foreground
  AppState.addEventListener('change', (nextAppState: AppStateStatus) => {
    if (nextAppState === 'active') {
      notifySync('VISIBILITY_RESTORED');
    }
  });
}

/**
 * Dispatches the event to the subscribers of the local process.
 */
function dispatchLocal(event: SyncEvent) {
  const specific = listeners.get(event.type);
  if (specific) {
    specific.forEach((fn) => {
      try {
        fn(event);
      } catch (err) {
        console.error(`[SyncBus] Listener error for ${event.type}:`, err);
      }
    });
  }

  const wildcards = listeners.get('*');
  if (wildcards) {
    wildcards.forEach((fn) => {
      try {
        fn(event);
      } catch (err) {
        console.error('[SyncBus] Global listener error:', err);
      }
    });
  }
}

/**
 * Notifies a mutation to all local subscribers and to other tabs on Web.
 */
export function notifySync(type: SyncEventType, payload?: any) {
  const event: SyncEvent = {
    type,
    payload,
    timestamp: Date.now(),
    originId: CURRENT_TAB_ID,
  };

  // Local dispatch
  dispatchLocal(event);

  // Dispatch to other Web tabs (when applicable)
  if (Platform.OS === 'web' && win) {
    try {
      if (webBroadcastChannel) {
        webBroadcastChannel.postMessage(event);
      } else if (win.localStorage && typeof win.localStorage.setItem === 'function') {
        win.localStorage.setItem('spotmeet_sync_event', JSON.stringify(event));
      }
    } catch {
      // Ignore occasional errors when sending through the web channel
    }
  }
}

/**
 * Subscribes to sync events. Returns a function that cancels the subscription.
 */
export function subscribeSync(
  type: SyncEventType | '*',
  listener: SyncListener
): () => void {
  if (!listeners.has(type)) {
    listeners.set(type, new Set());
  }
  listeners.get(type)!.add(listener);

  return () => {
    const set = listeners.get(type);
    if (set) {
      set.delete(listener);
      if (set.size === 0) {
        listeners.delete(type);
      }
    }
  };
}

/**
 * Checks whether the app or the current tab is visible.
 */
export function isAppVisible(): boolean {
  if (Platform.OS === 'web' && win) {
    const doc: any = win.document;
    if (doc && typeof doc.visibilityState === 'string') {
      return doc.visibilityState === 'visible';
    }
    return true;
  }
  return AppState.currentState === 'active';
}
