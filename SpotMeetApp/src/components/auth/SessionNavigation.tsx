import React, { useRef } from 'react';
import { NavigationContainer, type NavigationContainerRefWithCurrent, type NavigationState, type ParamListBase } from '@react-navigation/native';
import { useAuth } from '../../context/AuthContext';
import { API_BASE_URL } from '../../config/api';
import BiometricGate from './BiometricGate';

interface Props {
  navigationRef: NavigationContainerRefWithCurrent<ParamListBase>;
  children: React.ReactNode;
}
interface SavedNavigation {
  token: string | null;
  version: number;
  userId: number | null;
  role: string | null;
  apiBaseUrl: string;
  state?: NavigationState;
}

export default function SessionNavigation({ navigationRef, children }: Props) {
  const { userId, role, getSession } = useAuth();
  const { token, version } = getSession();
  // Navigation survives a lock in memory; protected screens still unmount.
  const saved = useRef<SavedNavigation | null>(null);
  if (!saved.current || saved.current.token !== token || saved.current.version !== version
      || saved.current.userId !== userId || saved.current.role !== role || saved.current.apiBaseUrl !== API_BASE_URL) {
    saved.current = { token, version, userId, role, apiBaseUrl: API_BASE_URL };
  }
  const snapshot = saved.current;
  const remember = (state: NavigationState | undefined) => {
    const session = getSession();
    if (state && snapshot.token && saved.current === snapshot && session.token === snapshot.token
        && session.version === snapshot.version && API_BASE_URL === snapshot.apiBaseUrl) {
      snapshot.state = state;
    }
  };

  return <BiometricGate>
    <NavigationContainer ref={navigationRef} initialState={snapshot.state} onStateChange={remember}>
      {children}
    </NavigationContainer>
  </BiometricGate>;
}
