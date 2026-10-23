import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Modal, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';
import { API_BASE_URL } from '../../config/api';
import { isAppVisible, subscribeVisibility } from '../../utils/syncBus';
import { registerConfirmationHost, type Confirmation } from '../../utils/confirmation';
import ToastHost from './ToastHost';

export default function ConfirmationHost({ active = true }: { active?: boolean }) {
  const { token, role, getSession } = useAuth();
  const version = getSession().version, api = API_BASE_URL;
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [request, setRequest] = useState<Confirmation | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const current = useRef<Confirmation | null>(null);
  const executing = useRef<Confirmation | null>(null), attempted = useRef<Confirmation | null>(null);
  const mounted = useRef(false);

  const close = (force = false) => {
    if (executing.current === current.current && current.current && !force) return;
    const previous = current.current;
    current.current = null;
    executing.current = null;
    if (mounted.current) { setRequest(null); setBusy(false); setError(''); }
    previous?.onClosed();
  };

  useEffect(() => {
    mounted.current = true;
    if (!active) { close(true); return () => { mounted.current = false; }; }
    const unregister = registerConfirmationHost(next => {
      current.current = next; executing.current = null; attempted.current = null;
      setRequest(next); setBusy(false); setError('');
    });
    const unsubscribe = subscribeVisibility(visible => { if (!visible) close(true); });
    // Closing before clearing `mounted` hides the dialog when only the session changed (logout),
    // instead of leaving it drawn until something else re-renders.
    return () => { unregister(); unsubscribe(); close(true); mounted.current = false; };
  }, [active, token, role, version, api]);

  const confirm = async () => {
    const selected = current.current;
    if (!selected || selected !== request || attempted.current === selected || !active || !isAppVisible()
        || getSession().token !== token || getSession().version !== version || API_BASE_URL !== api) return;
    attempted.current = selected; executing.current = selected; setBusy(true);
    try {
      await selected.onConfirm();
      if (current.current === selected) close(true);
    } catch {
      if (current.current === selected && mounted.current) {
        setError('Não foi possível concluir a ação. Feche esta janela e confira os dados antes de tentar novamente.');
      }
    } finally {
      if (current.current === selected && mounted.current) setBusy(false);
      if (executing.current === selected) executing.current = null;
    }
  };

  const visible = active && !!request && current.current === request;
  const cancel = () => { if (current.current === request) close(); };
  const extra = () => {
    const selected = current.current;
    if (!selected || selected !== request || busy) return;
    close(true);
    selected.onExtra?.();
  };
  const accent = request?.destructive ? colors.danger : colors.primary;
  return <Modal visible={visible} transparent animationType="fade" onRequestClose={cancel} onDismiss={cancel}>
    <View style={[styles.overlay, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 16 }]}>
      <TouchableOpacity accessibilityLabel="Cancelar confirmação" accessibilityRole="button" disabled={busy}
        style={StyleSheet.absoluteFill} onPress={cancel} />
      <View accessibilityViewIsModal style={[styles.dialog, { backgroundColor: colors.card, borderColor: colors.border }]}>
        <View style={styles.heading}><Ionicons name={request?.destructive ? 'warning-outline' : 'help-circle-outline'} size={26} color={accent} />
          <Text accessibilityRole="header" style={[styles.title, { color: colors.text }]}>{request?.title}</Text></View>
        <ScrollView style={styles.body} contentContainerStyle={styles.bodyContent}>
          <Text style={[styles.message, { color: colors.subtext }]}>{request?.message}</Text>
          {!!error && <Text accessibilityRole="alert" style={[styles.message, { color: colors.danger }]}>{error}</Text>}
        </ScrollView>
        <View style={styles.actions}>
          <TouchableOpacity accessibilityRole="button" accessibilityLabel={request?.cancelText ?? 'Cancelar'} disabled={busy}
            style={[styles.button, { borderColor: colors.border, opacity: busy ? 0.5 : 1 }]} onPress={cancel}>
            <Text style={[styles.buttonText, { color: colors.text }]}>{request?.cancelText ?? 'Cancelar'}</Text></TouchableOpacity>
          {!!request?.extraText && <TouchableOpacity accessibilityRole="button" accessibilityLabel={request.extraText} disabled={busy}
            style={[styles.button, { borderColor: colors.danger, opacity: busy ? 0.5 : 1 }]} onPress={extra}>
            <Text style={[styles.buttonText, { color: colors.danger }]}>{request.extraText}</Text></TouchableOpacity>}
          <TouchableOpacity accessibilityRole="button" accessibilityLabel={request?.confirmText ?? 'Confirmar'} disabled={busy || !!error}
            accessibilityState={{ disabled: busy || !!error, busy }} onPress={confirm}
            style={[styles.button, { borderColor: accent, backgroundColor: accent, opacity: busy || error ? 0.6 : 1 }]}>
            {busy && <ActivityIndicator color="#FFFFFF" size="small" />}
            <Text style={[styles.buttonText, { color: '#FFFFFF' }]}>{request?.confirmText}</Text></TouchableOpacity>
        </View>
      </View>
    </View>
    <ToastHost active={visible} />
  </Modal>;
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 20, backgroundColor: 'rgba(0,0,0,0.55)' },
  dialog: { width: '100%', maxWidth: 440, maxHeight: '100%', borderRadius: 20, borderWidth: 1, padding: 20 },
  heading: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 14 },
  title: { flex: 1, minWidth: 0, fontSize: 19, lineHeight: 26, fontWeight: '700' },
  body: { flexShrink: 1 }, bodyContent: { gap: 12 }, message: { fontSize: 14, lineHeight: 22 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 22 },
  button: { flexGrow: 1, flexBasis: 120, minHeight: 48, borderWidth: 1, borderRadius: 12, padding: 12, flexDirection: 'row', gap: 8, justifyContent: 'center', alignItems: 'center' },
  buttonText: { flexShrink: 1, fontSize: 14, lineHeight: 20, fontWeight: '600', textAlign: 'center' },
});
