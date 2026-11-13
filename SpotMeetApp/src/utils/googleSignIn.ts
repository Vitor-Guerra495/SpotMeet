import { Platform } from 'react-native';
import { API_BASE_URL } from '../config/api';
import type { AuthSession } from '../context/AuthContext';

export type GoogleSignInResult =
  | { status: 'success'; session: AuthSession }
  | { status: 'cancelled' }
  | { status: 'unavailable'; message: string }
  | { status: 'error'; message: string };

type GoogleModule = typeof import('@react-native-google-signin/google-signin');

const FAILURE = 'Não foi possível entrar com o Google. Tente novamente.';

// The native module is missing in Expo Go, and importing it there throws.
function loadGoogleModule(): GoogleModule | null {
  try {
    return require('@react-native-google-signin/google-signin') as GoogleModule;
  } catch {
    return null;
  }
}

async function readServerStatus(apiBase: string): Promise<{ enabled: boolean; clientId: string }> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6000);
  try {
    const res = await fetch(`${apiBase}/auth/google/status`, { signal: controller.signal });
    if (!res.ok) return { enabled: false, clientId: '' };
    const data = await res.json();
    const clientId = typeof data?.clientId === 'string' ? data.clientId : '';
    return { enabled: data?.enabled === true && clientId !== '', clientId };
  } catch {
    return { enabled: false, clientId: '' };
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Signs in with the Google account chooser of the device. The app only obtains the ID token;
 * the server checks it and starts a normal session.
 */
export async function signInWithGoogle(): Promise<GoogleSignInResult> {
  if (Platform.OS !== 'android') {
    return { status: 'unavailable', message: 'O login com Google está disponível apenas no aplicativo Android.' };
  }
  const google = loadGoogleModule();
  if (!google) {
    return { status: 'unavailable', message: 'O login com Google funciona só no aplicativo instalado (APK), não no Expo Go.' };
  }

  const apiBase = API_BASE_URL;
  try {
    const server = await readServerStatus(apiBase);
    if (!server.enabled) {
      return { status: 'unavailable', message: 'O login com Google não está disponível neste servidor.' };
    }

    const { GoogleSignin } = google;
    GoogleSignin.configure({ webClientId: server.clientId });
    await GoogleSignin.hasPlayServices();
    // Forgets the last account so the chooser always appears.
    await GoogleSignin.signOut().catch(() => null);

    const result = await GoogleSignin.signIn();
    if (result.type !== 'success') return { status: 'cancelled' };
    const idToken = result.data.idToken;
    if (!idToken) return { status: 'error', message: FAILURE };

    const res = await fetch(`${apiBase}/auth/google`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken }),
    });
    if (!res.ok) return { status: 'error', message: FAILURE };

    const data = await res.json();
    if (!data || typeof data.token !== 'string' || !data.token || !['USER', 'ADMIN', 'SYSADMIN'].includes(data.role)) {
      return { status: 'error', message: FAILURE };
    }
    return {
      status: 'success',
      session: {
        token: data.token,
        role: data.role,
        name: data.name,
        email: data.email,
        userId: data.userId,
        emailVerified: data.emailVerified,
      },
    };
  } catch (error) {
    if (google.isErrorWithCode(error) && error.code === google.statusCodes.SIGN_IN_CANCELLED) {
      return { status: 'cancelled' };
    }
    return { status: 'error', message: FAILURE };
  }
}
