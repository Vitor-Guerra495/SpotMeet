import { createContext, useContext } from 'react';

export const AuthInputFocus = createContext<(target: number) => void>(() => {});
export const useAuthInputFocus = () => useContext(AuthInputFocus);
export const AuthCompactLayout = createContext(false);
export const useAuthCompactLayout = () => useContext(AuthCompactLayout);
