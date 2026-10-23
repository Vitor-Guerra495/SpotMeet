import React from 'react';
import { Modal, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../../context/ThemeContext';
import { useBiometric } from '../../context/BiometricContext';
import { useAppUpdate } from '../../hooks/useAppUpdate';
import FormButton from '../forms/FormButton';

export default function AppUpdateNotice() {
  const { colors } = useTheme(), insets = useSafeAreaInsets(), biometric = useBiometric();
  const update = useAppUpdate(!biometric.ready || biometric.locked || biometric.covered || biometric.offered || biometric.busy);
  return <Modal visible={!!update.release} transparent animationType="fade" statusBarTranslucent onRequestClose={update.dismiss}>
    <View style={[styles.overlay, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 16 }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <View accessibilityViewIsModal style={[styles.card, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <Ionicons name="cloud-download-outline" size={38} color={colors.primary} />
          <Text style={[styles.title, { color: colors.text }]}>Nova versão disponível</Text>
          <Text style={[styles.description, { color: colors.subtext }]}>
            {`O SpotMeet ${update.release?.version ?? ''} está disponível. Você pode continuar usando esta versão ou abrir a página do Expo para baixar o novo APK.`}
          </Text>
          {update.error ? <Text accessibilityRole="alert" style={[styles.description, { color: colors.danger }]}>{update.error}</Text> : null}
          <FormButton label="Atualizar" icon="download-outline" loading={update.opening} onPress={update.openUpdate} />
          <TouchableOpacity accessibilityRole="button" accessibilityLabel="Agora não" onPress={update.dismiss} style={styles.secondary}>
            <Text style={[styles.secondaryText, { color: colors.primary }]}>Agora não</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </View>
  </Modal>;
}

const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.65)', paddingHorizontal: 20 },
  content: { flexGrow: 1, justifyContent: 'center', alignItems: 'center' },
  card: { width: '100%', maxWidth: 420, padding: 24, borderRadius: 24, borderWidth: 1, gap: 16 },
  title: { fontSize: 22, lineHeight: 28, fontWeight: '700' },
  description: { fontSize: 14, lineHeight: 21 },
  secondary: { minHeight: 48, justifyContent: 'center', alignItems: 'center', padding: 12 },
  secondaryText: { fontSize: 15, fontWeight: '600' },
});
