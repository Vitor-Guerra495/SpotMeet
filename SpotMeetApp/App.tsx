import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import ToastHost from './src/components/ui/ToastHost';

import { AuthProvider, useAuth } from './src/context/AuthContext';
import { ThemeProvider, useTheme } from './src/context/ThemeContext';
import { loadPersistedApiHost, needsDiscovery, setDiscoveredHost } from './src/config/api';
import { discoverBackendHost } from './src/config/discovery';

import AuthScreen from './src/screens/AuthScreen';
import SettingsScreen from './src/screens/SettingsScreen';
import OrganizationScreen from './src/screens/OrganizationScreen';

// Unified SysAdmin panel screens
import AdminOrganizationsScreen from './src/screens/admin/AdminOrganizationsScreen';
import AdminMonitoringReportsScreen from './src/screens/admin/AdminMonitoringReportsScreen';
import AdminControlsScreen from './src/screens/admin/AdminControlsScreen';

const Stack = createNativeStackNavigator();
const Tab = createBottomTabNavigator();

/**
 * Bottom tab bar exclusive to the Administrator (SysAdmin).
 * Tabs: Approvals (LGPD), Organizations, Monitoring & Reports, System Controls.
 */
function AdminTabs() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarStyle: {
          backgroundColor: colors.tabBarBg,
          borderTopWidth: 1,
          borderTopColor: colors.tabBarBorder,
          // Edge-to-edge: the gesture bar would sit on top of the labels without this inset
          paddingBottom: 5 + insets.bottom,
          height: 60 + insets.bottom,
        },
        tabBarActiveTintColor: colors.tabBarActive,
        tabBarInactiveTintColor: colors.tabBarInactive,
        tabBarIcon: ({ focused, color, size }) => {
          let iconName: keyof typeof Ionicons.glyphMap = 'shield-checkmark';

          if (route.name === 'AdminApprovals') {
            iconName = focused ? 'checkmark-done-circle' : 'checkmark-done-circle-outline';
          } else if (route.name === 'AdminOrganizations') {
            iconName = focused ? 'business' : 'business-outline';
          } else if (route.name === 'AdminReports') {
            iconName = focused ? 'stats-chart' : 'stats-chart-outline';
          } else if (route.name === 'AdminControls') {
            iconName = focused ? 'construct' : 'construct-outline';
          }

          return <Ionicons name={iconName} size={size} color={color} />;
        },
      })}
    >
      <Tab.Screen
        name="AdminApprovals"
        component={AdminOrganizationsScreen}
        initialParams={{ initialTab: 'pending' }}
        options={{ tabBarLabel: 'Aprovações', title: 'Aprovações' }}
      />
      <Tab.Screen
        name="AdminOrganizations"
        component={AdminOrganizationsScreen}
        initialParams={{ initialTab: 'all' }}
        options={{ tabBarLabel: 'Organizações', title: 'Organizações' }}
      />
      <Tab.Screen
        name="AdminReports"
        component={AdminMonitoringReportsScreen}
        options={{ tabBarLabel: 'Monitoramento', title: 'Monitoramento & Relatórios' }}
      />
      <Tab.Screen
        name="AdminControls"
        component={AdminControlsScreen}
        options={{ tabBarLabel: 'Controles', title: 'Controles do Sistema' }}
      />
    </Tab.Navigator>
  );
}

/**
 * Default bottom tab bar for regular users.
 */
function UserTabs() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarStyle: {
          backgroundColor: colors.tabBarBg,
          borderTopWidth: 1,
          borderTopColor: colors.tabBarBorder,
          // Edge-to-edge: the gesture bar would sit on top of the labels without this inset
          paddingBottom: 5 + insets.bottom,
          height: 60 + insets.bottom,
        },
        tabBarActiveTintColor: colors.tabBarActive,
        tabBarInactiveTintColor: colors.tabBarInactive,
        tabBarIcon: ({ focused, color, size }) => {
          let iconName: keyof typeof Ionicons.glyphMap = 'business';

          if (route.name === 'Organization') {
            iconName = focused ? 'business' : 'business-outline';
          } else if (route.name === 'Settings') {
            iconName = focused ? 'settings' : 'settings-outline';
          }

          return <Ionicons name={iconName} size={size} color={color} />;
        },
      })}
    >
      <Tab.Screen
        name="Organization"
        component={OrganizationScreen}
        options={{ tabBarLabel: 'Organização', title: 'Organização' }}
      />
      <Tab.Screen
        name="Settings"
        component={SettingsScreen}
        options={{ tabBarLabel: 'Ajustes', title: 'Ajustes' }}
      />
    </Tab.Navigator>
  );
}

/**
 * Tab dispatcher with role validation (ADMIN / SYSADMIN).
 */
function AppTabs({ route }: any) {
  const { role } = useAuth();
  const profile = route.params?.role || role;
  const isAdmin = profile === 'ADMIN' || profile === 'SYSADMIN';

  return isAdmin ? <AdminTabs /> : <UserTabs />;
}

export default function App() {
  // The API host saved on the device must be known before any screen calls the backend
  const [ready, setReady] = useState(false);
  const [statusText, setStatusText] = useState('');

  useEffect(() => {
    (async () => {
      await loadPersistedApiHost();
      // Standalone builds (APK/IPA) have no Expo dev server: find the backend on the Wi-Fi
      if (needsDiscovery()) {
        setStatusText('Procurando o servidor na rede...');
        const result = await discoverBackendHost((scanned, total) =>
          setStatusText(`Procurando o servidor na rede... ${scanned}/${total}`),
        );
        setDiscoveredHost(result.host);
      }
    })().finally(() => setReady(true));
  }, []);

  if (!ready) {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#1E1E2E' }}>
        <ActivityIndicator color="#FFF" />
        {statusText ? <Text style={{ color: '#E0E0E0', marginTop: 12, fontSize: 13 }}>{statusText}</Text> : null}
      </View>
    );
  }

  return (
    <SafeAreaProvider>
      <AuthProvider>
        <ThemeProvider>
          <NavigationContainer>
            <Stack.Navigator screenOptions={{ headerShown: false }}>
              {/* Auth routes unified through AuthScreen with transparent aliases */}
              <Stack.Screen
                name="Auth"
                component={AuthScreen}
                initialParams={{ mode: 'login' }}
              />
              <Stack.Screen
                name="Register"
                component={AuthScreen}
                initialParams={{ mode: 'register' }}
              />
              <Stack.Screen
                name="VerifyEmail"
                component={AuthScreen}
                initialParams={{ mode: 'verify' }}
              />
              <Stack.Screen
                name="ForgotPassword"
                component={AuthScreen}
                initialParams={{ mode: 'forgot' }}
              />
              <Stack.Screen
                name="ResetPassword"
                component={AuthScreen}
                initialParams={{ mode: 'reset' }}
              />
              <Stack.Screen name="Main" component={AppTabs} />
            </Stack.Navigator>
          </NavigationContainer>
          {/* In-app notifications, above every screen */}
          <ToastHost />
        </ThemeProvider>
      </AuthProvider>
    </SafeAreaProvider>
  );
}
