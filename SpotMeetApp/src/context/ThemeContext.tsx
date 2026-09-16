import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { useAuth } from './AuthContext';
import { API_BASE_URL } from '../config/api';

// Values accepted by the API for preferredTheme
export type PreferredTheme = 'LIGHT' | 'DARK' | 'SYSTEM';

// Themes the app can actually render
export type ThemeType = 'LIGHT' | 'DARK';

export interface ThemeColors {
  background: string;
  card: string;
  text: string;
  subtext: string;
  inputBg: string;
  border: string;
  primary: string;
  tabBarBg: string;
  tabBarBorder: string;
  tabBarActive: string;
  tabBarInactive: string;
  itemBg: string;
  success: string;
  warning: string;
  danger: string;
  isDark: boolean;
}

export const darkTheme: ThemeColors = {
  background: '#121212',
  card: '#1E1E1E',
  text: '#FFFFFF',
  subtext: '#A0A0A0',
  inputBg: '#2A2A2A',
  border: '#333333',
  primary: '#5C6BC0',
  tabBarBg: '#1E1E1E',
  tabBarBorder: '#333333',
  tabBarActive: '#5C6BC0',
  tabBarInactive: '#A0A0A0',
  itemBg: '#2A2A2A',
  success: '#4CAF50',
  warning: '#FFB300',
  danger: '#EF5350',
  isDark: true,
};

export const lightTheme: ThemeColors = {
  background: '#F4F6F9',
  card: '#FFFFFF',
  text: '#1A1A1A',
  subtext: '#666666',
  inputBg: '#ECEFF1',
  border: '#CFD8DC',
  primary: '#5C6BC0',
  tabBarBg: '#FFFFFF',
  tabBarBorder: '#E0E0E0',
  tabBarActive: '#5C6BC0',
  tabBarInactive: '#78909C',
  itemBg: '#F0F2F5',
  success: '#388E3C',
  warning: '#F57C00',
  danger: '#D32F2F',
  isDark: false,
};

interface ThemeContextType {
  theme: ThemeType;
  colors: ThemeColors;
  setTheme: (newTheme: ThemeType, persistToBackend?: boolean) => Promise<void>;
  loadThemeFromProfile: () => Promise<void>;
}

const ThemeContext = createContext<ThemeContextType | null>(null);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const { token, isAuthenticated } = useAuth();
  const [theme, setThemeState] = useState<ThemeType>('DARK');

  // Loads the theme preference from the user profile on the backend
  const loadThemeFromProfile = useCallback(async () => {
    if (!token) return;
    try {
      const res = await fetch(`${API_BASE_URL}/users/me`, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });
      if (res.ok) {
        const data = await res.json();
        if (data.preferredTheme === 'LIGHT' || data.preferredTheme === 'DARK') {
          setThemeState(data.preferredTheme);
        }
      }
    } catch {
      // Ignore transient failures
    }
  }, [token]);

  useEffect(() => {
    if (isAuthenticated) {
      loadThemeFromProfile();
    } else {
      setThemeState('DARK');
    }
  }, [isAuthenticated, loadThemeFromProfile]);

  const setTheme = useCallback(async (newTheme: ThemeType, persistToBackend = false) => {
    setThemeState(newTheme);
    if (persistToBackend && token) {
      try {
        await fetch(`${API_BASE_URL}/users/me`, {
          method: 'PUT',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            preferredTheme: newTheme as PreferredTheme,
          }),
        });
      } catch (err) {
        console.warn('Erro ao sincronizar tema no backend:', err);
      }
    }
  }, [token]);

  const colors = theme === 'LIGHT' ? lightTheme : darkTheme;

  return (
    <ThemeContext.Provider
      value={{
        theme,
        colors,
        setTheme,
        loadThemeFromProfile,
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextType {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme deve ser usado dentro de um ThemeProvider.');
  }
  return ctx;
}
