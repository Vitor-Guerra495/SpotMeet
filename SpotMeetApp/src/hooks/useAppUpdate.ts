import { useEffect, useRef, useState } from 'react';
import { AppState, Linking, Platform } from 'react-native';
import Constants from 'expo-constants';
import * as Application from 'expo-application';
import { API_BASE_URL } from '../config/api';
import { availableAndroidUpdate, installedAndroidApp, isExpoBuildPage, type AndroidRelease } from '../utils/appUpdate';

interface Offer { api: string; release: AndroidRelease; }

export function useAppUpdate(paused: boolean) {
  const [installed] = useState(() => installedAndroidApp({ platform: Platform.OS, development: __DEV__,
    environment: Constants.executionEnvironment, applicationId: Application.applicationId,
    version: Application.nativeApplicationVersion, build: Application.nativeBuildVersion }));
  const [offer, setOffer] = useState<Offer | null>(null);
  const [foreground, setForeground] = useState(AppState.currentState === 'active');
  const [opening, setOpening] = useState(false);
  const [error, setError] = useState('');
  const checked = useRef(new Set<string>());
  const alive = useRef(false), pausedRef = useRef(paused);
  const offerRef = useRef(offer);
  const linkOperation = useRef<{ timer: ReturnType<typeof setTimeout> } | null>(null);
  const api = API_BASE_URL;
  pausedRef.current = paused;
  offerRef.current = offer;

  const cancelLink = (updateState = true) => {
    if (linkOperation.current) clearTimeout(linkOperation.current.timer);
    linkOperation.current = null;
    if (updateState) setOpening(false);
  };

  useEffect(() => {
    alive.current = true;
    let active = true, request: AbortController | null = null;
    let timer: ReturnType<typeof setTimeout> | undefined;
    offerRef.current = null; setOffer(null); setError(''); cancelLink();
    const cancel = () => { request?.abort(); if (timer) clearTimeout(timer); };
    const check = async () => {
      if (!installed || !active || api !== API_BASE_URL || AppState.currentState !== 'active' || checked.current.has(api)) return;
      checked.current.add(api);
      const controller = new AbortController(); request = controller;
      timer = setTimeout(() => controller.abort(), 6000);
      try {
        const response = await fetch(`${api}/app/android-release`, {
          headers: { Accept: 'application/json' }, credentials: 'omit', redirect: 'error', signal: controller.signal,
        });
        if (response.status !== 200 || response.headers.get('content-type')?.split(';')[0].trim().toLowerCase() !== 'application/json') return;
        const size = Number(response.headers.get('content-length'));
        if (size > 4096) return;
        const body = await response.text();
        if (body.length > 4096) return;
        const release = availableAndroidUpdate(JSON.parse(body), installed);
        if (release && active && !controller.signal.aborted && api === API_BASE_URL && AppState.currentState === 'active') {
          setOffer({ api, release });
        }
      } catch {
        // Version checks never prevent access to the app.
      } finally { if (timer) clearTimeout(timer); request = null; }
    };
    const subscription = AppState.addEventListener('change', state => {
      setForeground(state === 'active');
      if (state !== 'active') cancel(); else void check();
    });
    void check();
    return () => { active = false; alive.current = false; offerRef.current = null; cancel(); cancelLink(false); subscription.remove(); };
  }, [api, installed]);

  const dismiss = () => { cancelLink(); offerRef.current = null; setOffer(null); setError(''); };
  const openUpdate = async () => {
    if (!offer || offerRef.current !== offer || linkOperation.current || !alive.current || pausedRef.current
        || offer.api !== API_BASE_URL || AppState.currentState !== 'active' || !isExpoBuildPage(offer.release.downloadUrl)) return;
    setError(''); setOpening(true);
    const operation = { timer: setTimeout(() => {
      if (linkOperation.current === operation) {
        linkOperation.current = null; setOpening(false);
        setError('Não foi possível abrir a página. Tente novamente.');
      }
    }, 6000) };
    linkOperation.current = operation;
    try {
      await Linking.openURL(offer.release.downloadUrl);
      if (alive.current && linkOperation.current === operation && offer.api === API_BASE_URL) dismiss();
    } catch {
      if (alive.current && linkOperation.current === operation && offer.api === API_BASE_URL) {
        setError('Não foi possível abrir a página. Tente novamente.');
      }
    } finally {
      if (linkOperation.current === operation) cancelLink();
    }
  };

  return { release: foreground && !paused && offer?.api === api ? offer.release : null, opening, error, dismiss, openUpdate };
}
