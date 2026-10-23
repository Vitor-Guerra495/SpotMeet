import React, { useEffect, useRef } from 'react';
import { ActivityIndicator, Modal, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { useBiometric } from '../../context/BiometricContext';
import { useTheme } from '../../context/ThemeContext';
import FormButton from '../forms/FormButton';

export default function BiometricGate({ children }: { children: React.ReactNode }) {
  const biometric = useBiometric();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const attemptedUnlock = useRef(false);

  useEffect(() => {
    if (!biometric.locked) attemptedUnlock.current = false;
    if (biometric.ready && biometric.locked && !biometric.covered && !attemptedUnlock.current) {
      attemptedUnlock.current = true;
      void biometric.unlock();
    }
  }, [biometric.ready, biometric.locked, biometric.covered]);

  const protectedView = (
    <View style={[styles.root, { backgroundColor: colors.background,
      paddingTop: insets.top + 24, paddingBottom: insets.bottom + 24 }]}>
      <ScrollView contentContainerStyle={styles.lockContent}>
        <View style={[styles.symbol, { backgroundColor: colors.card }]}>
          <Ionicons name="finger-print-outline" size={52} color={colors.primary} />
        </View>
        <Text style={[styles.title, { color: colors.text }]}>SpotMeet bloqueado</Text>
        <Text style={[styles.description, { color: colors.subtext }]}>
          Use a biometria ou o PIN do aparelho para continuar. Sua sessão será confirmada no servidor.
        </Text>
        {biometric.error ? <Text accessibilityRole="alert" style={[styles.error, { color: colors.danger }]}>{biometric.error}</Text> : null}
        <View style={styles.actions}>
          <FormButton label="Desbloquear" icon="lock-open-outline" loading={biometric.busy} onPress={biometric.unlock} />
          <TouchableOpacity accessibilityRole="button" onPress={biometric.returnToLogin} style={styles.secondary}>
            <Text style={[styles.secondaryText, { color: colors.primary }]}>Entrar com e-mail e senha</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </View>
  );

  if (!biometric.ready) return <View style={[styles.loading, { backgroundColor: colors.background }]}>
    <ActivityIndicator color={colors.primary} />
    <Text style={[styles.description, { color: colors.subtext }]}>Preparando acesso...</Text>
  </View>;

  return <View style={styles.root}>
    {biometric.locked ? protectedView : children}
    <Modal visible={biometric.offered && !biometric.locked} transparent animationType="fade"
      statusBarTranslucent onRequestClose={biometric.dismissOffer}>
      <View style={[styles.overlay, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 16 }]}>
        <ScrollView contentContainerStyle={styles.popupContent}>
          <View style={[styles.popup, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Ionicons name="finger-print-outline" size={38} color={colors.primary} />
            <Text style={[styles.popupTitle, { color: colors.text }]}>Deseja habilitar a biometria?</Text>
            <Text style={[styles.description, { color: colors.subtext }]}>
              Proteja a reabertura do app neste aparelho. A opção é voluntária e será removida ao sair da conta.
            </Text>
            {biometric.error ? <Text accessibilityRole="alert" style={[styles.error, { color: colors.danger }]}>{biometric.error}</Text> : null}
            <FormButton label="Sim" icon="finger-print-outline" loading={biometric.busy} onPress={biometric.enable} />
            <TouchableOpacity accessibilityRole="button" accessibilityLabel="Não" disabled={biometric.busy}
              onPress={biometric.dismissOffer} style={styles.secondary}>
              <Text style={[styles.secondaryText, { color: colors.subtext }]}>Não</Text>
            </TouchableOpacity>
          </View>
        </ScrollView>
      </View>
    </Modal>
    <Modal visible={biometric.covered} animationType="none" statusBarTranslucent onRequestClose={() => {}}>
      <View style={[styles.loading, { backgroundColor: colors.background }]}>
        <Ionicons name="lock-closed-outline" size={40} color={colors.primary} />
        <Text style={[styles.title, { color: colors.text }]}>SpotMeet</Text>
      </View>
    </Modal>
  </View>;
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 16, padding: 24 },
  lockContent: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 18 },
  symbol: { padding: 24, borderRadius: 28 },
  title: { fontSize: 25, fontWeight: '700', textAlign: 'center' },
  description: { fontSize: 14, lineHeight: 21, textAlign: 'center' },
  actions: { width: '100%', maxWidth: 380, gap: 8 },
  secondary: { minHeight: 48, alignItems: 'center', justifyContent: 'center', padding: 12 },
  secondaryText: { fontSize: 15, fontWeight: '600', textAlign: 'center' },
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.65)', paddingHorizontal: 20 },
  popupContent: { flexGrow: 1, justifyContent: 'center', alignItems: 'center' },
  popup: { width: '100%', maxWidth: 420, padding: 24, borderRadius: 24, borderWidth: 1, gap: 16 },
  popupTitle: { fontSize: 22, fontWeight: '700', lineHeight: 28 },
  error: { fontSize: 14, lineHeight: 20, textAlign: 'center' },
});
