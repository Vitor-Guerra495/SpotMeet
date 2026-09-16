import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Network from 'expo-network';
import { getApiPort } from './api';

/**
 * Automatic backend discovery on the local network.
 *
 * Used by standalone builds (APK/IPA), where there is no Expo dev server to tell
 * the app which machine runs the backend. The app reads its own Wi-Fi address,
 * probes every host of the /24 subnet on the API port and accepts the first one
 * whose GET /actuator/info answers with the SpotMeet name.
 *
 * The last host found is cached and tried first on the next launch.
 */
const CACHE_KEY = '@spotmeet/discovered-host';
const PROBE_TIMEOUT_MS = 1200;
const BATCH_SIZE = 64;
const SERVICE_NAME = 'SpotMeet';

const scheme = process.env.EXPO_PUBLIC_API_SCHEME?.trim() || 'http';

async function fetchWithTimeout(url: string, timeoutMs: number): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

/** True when `host` answers as the SpotMeet backend. */
export async function isSpotMeetBackend(host: string, timeoutMs = PROBE_TIMEOUT_MS): Promise<boolean> {
  try {
    const res = await fetchWithTimeout(`${scheme}://${host}:${getApiPort()}/actuator/info`, timeoutMs);
    if (!res.ok) return false;
    const body = await res.json();
    return body?.app?.name === SERVICE_NAME;
  } catch {
    return false;
  }
}

async function getOwnIpv4(): Promise<string | null> {
  try {
    const ip = await Network.getIpAddressAsync();
    return ip && /^\d+\.\d+\.\d+\.\d+$/.test(ip) && ip !== '0.0.0.0' ? ip : null;
  } catch {
    return null;
  }
}

/** Candidate hosts of the /24 network, most likely ones first. */
function buildCandidates(ownIp: string, preferred: string[]): string[] {
  const prefix = ownIp.split('.').slice(0, 3).join('.');
  const ordered: string[] = [];
  const seen = new Set<string>();
  const push = (h: string) => {
    if (h && h !== ownIp && !seen.has(h)) {
      seen.add(h);
      ordered.push(h);
    }
  };

  preferred.forEach(push);
  // Gateway and low addresses are the usual spot for the developer machine on hotspots
  for (let i = 1; i <= 20; i++) push(`${prefix}.${i}`);
  for (let i = 21; i <= 254; i++) push(`${prefix}.${i}`);
  return ordered;
}

export interface DiscoveryResult {
  host: string | null;
  ownIp: string | null;
  fromCache: boolean;
}

/**
 * Finds the backend on the current network.
 * Order: cached host, then a parallel sweep of the /24 subnet (batches of 64).
 */
export async function discoverBackendHost(
  onProgress?: (scanned: number, total: number) => void,
): Promise<DiscoveryResult> {
  let cached: string | null = null;
  try {
    cached = await AsyncStorage.getItem(CACHE_KEY);
  } catch {
    cached = null;
  }

  if (cached && (await isSpotMeetBackend(cached))) {
    return { host: cached, ownIp: null, fromCache: true };
  }

  const ownIp = await getOwnIpv4();
  if (!ownIp) return { host: null, ownIp: null, fromCache: false };

  const candidates = buildCandidates(ownIp, cached ? [cached] : []);
  let scanned = 0;

  for (let start = 0; start < candidates.length; start += BATCH_SIZE) {
    const batch = candidates.slice(start, start + BATCH_SIZE);
    const results = await Promise.all(
      batch.map(async (host) => ((await isSpotMeetBackend(host)) ? host : null)),
    );
    scanned += batch.length;
    onProgress?.(scanned, candidates.length);

    const found = results.find((h): h is string => h !== null);
    if (found) {
      try {
        await AsyncStorage.setItem(CACHE_KEY, found);
      } catch {
        // Cache is only an optimization for the next launch
      }
      return { host: found, ownIp, fromCache: false };
    }
  }

  return { host: null, ownIp, fromCache: false };
}

export async function clearDiscoveryCache(): Promise<void> {
  try {
    await AsyncStorage.removeItem(CACHE_KEY);
  } catch {
    // ignore
  }
}
