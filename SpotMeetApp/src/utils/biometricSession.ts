import { Platform } from 'react-native';
import Constants from 'expo-constants';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import * as LocalAuthentication from 'expo-local-authentication';

export const BIOMETRIC_DELAYS = [
  { value: 0, label: 'Imediatamente' },
  { value: 60000, label: '1 minuto' },
  { value: 300000, label: '5 minutos' },
  { value: 3600000, label: '1 hora' },
  { value: 10800000, label: '3 horas' },
  { value: 86400000, label: '24 horas' },
] as const;

export interface BiometricConfig { apiBaseUrl: string; delay: number; }
export interface StoredBiometricSession { token: string; userId: number; apiBaseUrl: string; }
export interface BiometricAvailability { available: boolean; message: string; }

const CONFIG_KEY = '@spotmeet/biometric-config';
const SESSION_KEY = 'spotmeet.biometric-session';
const REARM_KEY = '@spotmeet/biometric-rearm';
const STORE_OPTIONS: SecureStore.SecureStoreOptions = {
  keychainService: 'com.spotmeet.biometric-session',
  keychainAccessible: SecureStore.WHEN_PASSCODE_SET_THIS_DEVICE_ONLY,
};
let storageQueue: Promise<unknown> = Promise.resolve();
let storageVersion = 0;

function enqueue<T>(operation: () => Promise<T>): Promise<T> {
  const pending = storageQueue.then(operation);
  storageQueue = pending.catch(() => undefined);
  return pending;
}

export function isBiometricDelay(value: unknown): value is number {
  return typeof value === 'number' && BIOMETRIC_DELAYS.some(option => option.value === value);
}

export function needsBiometricLock(delay: number, backgroundAt: number, now: number): boolean {
  return !isBiometricDelay(delay) || now < backgroundAt || now - backgroundAt >= delay;
}

export function parseBiometricConfig(raw: string | null): BiometricConfig | null {
  try {
    const value = raw ? JSON.parse(raw) : null;
    if (value?.schema !== 1 || typeof value.apiBaseUrl !== 'string' || !isBiometricDelay(value.delay)) return null;
    const url = new URL(value.apiBaseUrl);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password
        || url.search || url.hash || !url.pathname.endsWith('/api')) return null;
    return { apiBaseUrl: value.apiBaseUrl, delay: value.delay };
  } catch { return null; }
}

export function parseBiometricSession(raw: string | null, config: BiometricConfig): StoredBiometricSession | null {
  try {
    const value = raw ? JSON.parse(raw) : null;
    if (value?.schema !== 1 || value.apiBaseUrl !== config.apiBaseUrl
        || typeof value.token !== 'string' || !value.token || value.token.length > 8192
        || !Number.isSafeInteger(value.userId) || value.userId <= 0) return null;
    return { token: value.token, userId: value.userId, apiBaseUrl: value.apiBaseUrl };
  } catch { return null; }
}

export async function getBiometricAvailability(): Promise<BiometricAvailability> {
  const unavailable = (message: string) => ({ available: false, message });
  if (Platform.OS !== 'android' && Platform.OS !== 'ios') return unavailable('Disponível no aplicativo Android ou iOS.');
  try {
    if (!await SecureStore.isAvailableAsync()) return unavailable('Armazenamento seguro indisponível neste aparelho.');
    if (!await LocalAuthentication.hasHardwareAsync()) return unavailable('Este aparelho não possui biometria compatível.');
    if (!await LocalAuthentication.isEnrolledAsync()) return unavailable('Cadastre uma biometria nas configurações do aparelho antes de habilitar.');
    if (Platform.OS === 'android'
        && await LocalAuthentication.getEnrolledLevelAsync() !== LocalAuthentication.SecurityLevel.BIOMETRIC_STRONG) {
      return unavailable('Use uma impressão digital ou biometria forte compatível com o aparelho.');
    }
    if (Platform.OS === 'ios' && Constants.executionEnvironment === 'storeClient') {
      const types = await LocalAuthentication.supportedAuthenticationTypesAsync();
      if (types.includes(LocalAuthentication.AuthenticationType.FACIAL_RECOGNITION)) {
        return unavailable('O Face ID precisa de uma build própria do SpotMeet; não está disponível no Expo Go.');
      }
    }
    return { available: true, message: '' };
  } catch { return unavailable('Não foi possível consultar a biometria do aparelho.'); }
}

export async function authenticateBiometricDevice(enabling: boolean): Promise<boolean> {
  const result = await LocalAuthentication.authenticateAsync({
    promptMessage: enabling ? 'Habilitar biometria no SpotMeet' : 'Desbloquear SpotMeet',
    promptDescription: enabling ? 'Confirme sua biometria para proteger este acesso.' : 'Use sua biometria ou o PIN do aparelho.',
    cancelLabel: 'Cancelar',
    disableDeviceFallback: enabling,
    fallbackLabel: enabling ? '' : 'Usar código do aparelho',
    biometricsSecurityLevel: 'strong',
  });
  return result.success;
}

export function readBiometricConfig(): Promise<BiometricConfig | null> {
  return enqueue(async () => parseBiometricConfig(await AsyncStorage.getItem(CONFIG_KEY)));
}

// Called only after device authentication. The token never goes to AsyncStorage.
export function readBiometricSession(config: BiometricConfig): Promise<StoredBiometricSession | null> {
  return enqueue(async () => parseBiometricSession(await SecureStore.getItemAsync(SESSION_KEY, STORE_OPTIONS), config));
}

export function saveBiometricSession(
  session: StoredBiometricSession, config: BiometricConfig, isCurrent: () => boolean,
): Promise<boolean> {
  const version = storageVersion;
  return enqueue(async () => {
    const current = () => version === storageVersion && isCurrent();
    if (!current()) return false;
    await AsyncStorage.removeItem(CONFIG_KEY);
    if (!current()) return false;
    await SecureStore.setItemAsync(SESSION_KEY, JSON.stringify({ schema: 1,
      token: session.token, userId: session.userId, apiBaseUrl: session.apiBaseUrl,
    }), STORE_OPTIONS);
    if (!current()) return false;
    await AsyncStorage.setItem(CONFIG_KEY, JSON.stringify({ schema: 1, apiBaseUrl: config.apiBaseUrl, delay: config.delay }));
    if (current()) return true;
    await AsyncStorage.removeItem(CONFIG_KEY);
    return false;
  });
}

// After a password change the saved token is revoked, but the preference survives (no token) until the next login.
export interface BiometricRearm { userId: number; apiBaseUrl: string; delay: number; }

export function saveBiometricRearm(rearm: BiometricRearm): Promise<void> {
  return enqueue(() => AsyncStorage.setItem(REARM_KEY, JSON.stringify({ schema: 1, ...rearm })));
}

export function takeBiometricRearm(): Promise<BiometricRearm | null> {
  return enqueue(async () => {
    const raw = await AsyncStorage.getItem(REARM_KEY);
    await AsyncStorage.removeItem(REARM_KEY);
    try {
      const value = raw ? JSON.parse(raw) : null;
      if (value?.schema !== 1 || typeof value.apiBaseUrl !== 'string' || !isBiometricDelay(value.delay)
          || !Number.isSafeInteger(value.userId) || value.userId <= 0) return null;
      return { userId: value.userId, apiBaseUrl: value.apiBaseUrl, delay: value.delay };
    } catch { return null; }
  });
}

export function clearBiometricSession(): Promise<boolean> {
  storageVersion += 1;
  return enqueue(async () => {
    const results = await Promise.allSettled([
      AsyncStorage.removeItem(CONFIG_KEY),
      ...(Platform.OS === 'android' || Platform.OS === 'ios' ? [SecureStore.deleteItemAsync(SESSION_KEY, STORE_OPTIONS)] : []),
    ]);
    return results.every(result => result.status === 'fulfilled');
  });
}
