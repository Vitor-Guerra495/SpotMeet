import AsyncStorage from '@react-native-async-storage/async-storage';
import Constants from 'expo-constants';
import { Platform } from 'react-native';

/**
 * Centralized SpotMeet API URL configuration.
 *
 * Host resolution order:
 * 1. Host saved by the user on the login screen ("Servidor"), persisted on the device.
 * 2. EXPO_PUBLIC_API_HOST from .env (build-time override: tunnel, remote server, fixed IP).
 * 3. The host that served the JS bundle (Metro / Expo Go). On a physical device the
 *    bundle comes from the developer machine, which is also where the backend runs.
 * 4. Host found by scanning the local network (see discovery.ts). Used by standalone
 *    builds (APK/IPA), where there is no Expo dev server.
 * 5. Platform defaults: 10.0.2.2 for the Android emulator, localhost otherwise.
 *
 * Port and scheme can be overridden through EXPO_PUBLIC_API_PORT and EXPO_PUBLIC_API_SCHEME.
 */
const DEFAULT_PORT = '8080';
const STORAGE_KEY = '@spotmeet/api-host';

const API_SCHEME = process.env.EXPO_PUBLIC_API_SCHEME?.trim() || 'http';
const API_PORT = process.env.EXPO_PUBLIC_API_PORT?.trim() || DEFAULT_PORT;

function resolveHostFromBundle(): string | undefined {
  const hostUri = Constants.expoConfig?.hostUri ?? Constants.expoGoConfig?.debuggerHost;
  if (!hostUri) return undefined;
  const host = hostUri.split(':')[0];
  if (!host || host === 'localhost' || host === '127.0.0.1') return undefined;
  return host;
}

/** Host detected without any user override (env, bundle, platform default). */
export function getAutoDetectedHost(): string {
  const fromEnv = process.env.EXPO_PUBLIC_API_HOST?.trim();
  if (fromEnv) return fromEnv;

  const fromBundle = resolveHostFromBundle();
  if (fromBundle) return fromBundle;

  return Platform.OS === 'android' ? '10.0.2.2' : 'localhost';
}

/** True when running from Expo Go / Metro, where auto-detection is reliable. */
export function isServedByDevServer(): boolean {
  return resolveHostFromBundle() !== undefined;
}

let userHost: string | null = null;
let discoveredHost: string | null = null;

function buildBaseUrl(host: string): string {
  return `${API_SCHEME}://${host}:${API_PORT}/api`;
}

/** Live binding: importers always read the current value. */
export let API_BASE_URL = buildBaseUrl(getAutoDetectedHost());

/** Host currently in use (user override, auto-detected, or found on the network). */
export function getApiHost(): string {
  if (userHost) return userHost;
  const fromEnv = process.env.EXPO_PUBLIC_API_HOST?.trim();
  if (fromEnv) return fromEnv;
  const fromBundle = resolveHostFromBundle();
  if (fromBundle) return fromBundle;
  return discoveredHost ?? getAutoDetectedHost();
}

/** Host found by the local network scan, if any. */
export function getDiscoveredHost(): string | null {
  return discoveredHost;
}

/** Registers the host found by the network scan (null clears it). */
export function setDiscoveredHost(host: string | null): void {
  discoveredHost = host;
  API_BASE_URL = buildBaseUrl(getApiHost());
}

/** True when the app needs a network scan to know where the backend is. */
export function needsDiscovery(): boolean {
  return userHost === null && !process.env.EXPO_PUBLIC_API_HOST?.trim() && !isServedByDevServer();
}

/** Host saved by the user, or null when auto-detection is in use. */
export function getUserApiHost(): string | null {
  return userHost;
}

export function getApiPort(): string {
  return API_PORT;
}

/** Loads the persisted host once at startup. Safe to call multiple times. */
export async function loadPersistedApiHost(): Promise<void> {
  try {
    const saved = await AsyncStorage.getItem(STORAGE_KEY);
    userHost = saved?.trim() || null;
  } catch {
    userHost = null;
  }
  API_BASE_URL = buildBaseUrl(getApiHost());
}

/**
 * Sets the API host chosen by the user. Pass null/empty to go back to auto-detection.
 * Accepts "192.168.1.10", "192.168.1.10:8080" or "http://192.168.1.10:8080".
 */
export async function setApiHost(input: string | null): Promise<void> {
  const cleaned = (input ?? '')
    .trim()
    .replace(/^https?:\/\//i, '')
    .replace(/\/.*$/, '')
    .replace(/:\d+$/, '');

  userHost = cleaned || null;
  API_BASE_URL = buildBaseUrl(getApiHost());

  try {
    if (userHost) {
      await AsyncStorage.setItem(STORAGE_KEY, userHost);
    } else {
      await AsyncStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Persistence failure only affects the next launch; current session keeps the host.
  }
}
