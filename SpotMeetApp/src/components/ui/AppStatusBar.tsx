import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';

export default function AppStatusBar() {
  const { isAuthenticated } = useAuth();
  const { colors } = useTheme();
  return <StatusBar style={!isAuthenticated || colors.isDark ? 'light' : 'dark'} />;
}
