import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { useBiometric } from '../../context/BiometricContext';
import { useTheme } from '../../context/ThemeContext';
import { BIOMETRIC_DELAYS } from '../../utils/biometricSession';
import { useAuth } from '../../context/AuthContext';
import { useIsFocused } from '@react-navigation/native';

export default function BiometricSettings({ disabled = false }: { disabled?: boolean }) {
  const biometric = useBiometric();
  const { getSession } = useAuth();
  const session = getSession();
  const focused = useIsFocused();
  const active = useRef(true);
  const focusedRef = useRef(focused);
  focusedRef.current = focused;
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
  const isCurrent = () => active.current && focusedRef.current && getSession().token === session.token
    && getSession().version === session.version;
  const { colors } = useTheme();
  const busy = disabled || biometric.busy;
  // Only the switch itself shows the spinner; changing the delay keeps the icon in place.
  const [toggling, setToggling] = useState(false);
  const toggle = async () => {
    if (!isCurrent() || busy) return;
    setToggling(true);
    try { await (biometric.enabled ? biometric.disable() : biometric.enable()); }
    finally { if (active.current) setToggling(false); }
  };
  return <View style={[styles.card, { backgroundColor: colors.card, borderColor: colors.border }]}>
    <View style={styles.row}>
      <Ionicons name="finger-print-outline" size={25} color={colors.primary} />
      <View style={styles.text}>
        <Text style={[styles.title, { color: colors.text }]}>Bloqueio biométrico</Text>
        <Text style={[styles.description, { color: colors.subtext }]}>
          Protege este acesso no aparelho. O desbloqueio também aceita o PIN do sistema.
        </Text>
      </View>
    </View>
    <TouchableOpacity accessibilityRole="switch" accessibilityLabel="Bloqueio biométrico"
      accessibilityState={{ checked: biometric.enabled, disabled: busy }} disabled={busy}
      onPress={toggle}
      style={[styles.toggle, { backgroundColor: colors.inputBg }]}>
      <Text style={[styles.title, { color: colors.text }]}>{biometric.enabled ? 'Habilitado' : 'Desligado'}</Text>
      {biometric.busy && toggling ? <ActivityIndicator color={colors.primary} />
        : <View style={[styles.track, { backgroundColor: biometric.enabled ? colors.primary : colors.subtext,
            alignItems: biometric.enabled ? 'flex-end' : 'flex-start' }]}>
            <View style={[styles.knob, { backgroundColor: colors.card }]} />
          </View>}
    </TouchableOpacity>
    {biometric.enabled ? <>
      <Text style={[styles.label, { color: colors.text }]}>Bloquear após deixar o app em segundo plano</Text>
      <View accessibilityRole="radiogroup" style={styles.options}>
        {BIOMETRIC_DELAYS.map(option => <TouchableOpacity key={option.value} accessibilityRole="radio"
          accessibilityLabel={option.label} accessibilityState={{ checked: biometric.delay === option.value, disabled: busy }}
          disabled={busy} onPress={() => { if (isCurrent() && !busy) return biometric.setDelay(option.value); }}
          style={[styles.option, { backgroundColor: colors.inputBg,
            borderColor: biometric.delay === option.value ? colors.primary : colors.border }]}>
          <Ionicons name={biometric.delay === option.value ? 'radio-button-on' : 'radio-button-off'} size={19} color={colors.primary} />
          <Text style={[styles.description, { color: colors.text }]}>{option.label}</Text>
        </TouchableOpacity>)}
      </View>
      <Text style={[styles.description, { color: colors.subtext }]}>Se o aplicativo for fechado, o desbloqueio é sempre solicitado, mesmo dentro do prazo. Sair da conta remove essa proteção e a sessão guardada.</Text>
    </> : null}
    {biometric.error ? <Text accessibilityRole="alert" style={[styles.description, { color: colors.danger }]}>{biometric.error}</Text> : null}
  </View>;
}

const styles = StyleSheet.create({
  card: { padding: 16, borderRadius: 16, borderWidth: 1, marginTop: 20, gap: 14 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  text: { flex: 1, gap: 4 },
  title: { fontSize: 16, fontWeight: '600' },
  description: { fontSize: 13, lineHeight: 19 },
  toggle: { minHeight: 52, borderRadius: 12, paddingHorizontal: 14, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  track: { width: 46, height: 26, borderRadius: 13, padding: 3, justifyContent: 'center' },
  knob: { width: 20, height: 20, borderRadius: 10 },
  label: { fontSize: 14, fontWeight: '600' },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  option: { minHeight: 48, paddingHorizontal: 12, borderRadius: 12, borderWidth: 1, flexDirection: 'row', alignItems: 'center', gap: 8 },
});
