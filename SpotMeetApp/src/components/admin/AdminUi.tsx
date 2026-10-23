import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Modal, ActivityIndicator, type ViewStyle } from 'react-native';
import { Ionicons, type IoniconsIconName } from '@react-native-vector-icons/ionicons';
import { useTheme } from '../../context/ThemeContext';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import FormScrollView from '../forms/FormScrollView';
import ToastHost from '../ui/ToastHost';
import ConfirmationHost from '../ui/ConfirmationHost';

export function AdminHeader({ title, subtitle, refreshing, onRefresh, disabled }: { title: string; subtitle: string; refreshing?: boolean; onRefresh?: () => void; disabled?: boolean }) {
  const { colors } = useTheme(); const insets = useSafeAreaInsets();
  return <View style={[styles.header, { paddingTop: insets.top + 12 }]}><View style={styles.flex}>
    <Text numberOfLines={1} maxFontSizeMultiplier={1.3} style={[styles.title, { color: colors.text }]}>{title}</Text>
    <Text style={[styles.subtitle, { color: colors.subtext }]}>{subtitle}</Text></View>
    {onRefresh && <TouchableOpacity accessibilityRole="button" accessibilityLabel="Atualizar" disabled={disabled} style={styles.iconButton} onPress={onRefresh}>
      {refreshing ? <ActivityIndicator color={colors.primary} /> : <Ionicons name="refresh" size={23} color={colors.primary} />}</TouchableOpacity>}
  </View>;
}
export function AdminCard({ children, style }: { children: React.ReactNode; style?: ViewStyle }) {
  const { colors } = useTheme(); return <View style={[styles.card, { backgroundColor: colors.card, borderColor: colors.border }, style]}>{children}</View>;
}
export function AdminAction({ label, onPress, disabled, danger, filled, icon }: { label: string; onPress: () => void | Promise<void>; disabled?: boolean; danger?: boolean; filled?: boolean; icon?: IoniconsIconName }) {
  const { colors } = useTheme(); const tint = filled ? '#FFFFFF' : danger ? colors.danger : colors.isDark ? '#ADB5EF' : colors.primary;
  return <TouchableOpacity accessibilityRole="button" accessibilityLabel={label} disabled={disabled} onPress={onPress}
    style={[styles.action, { borderColor: danger ? colors.danger : colors.primary, backgroundColor: filled ? danger ? colors.danger : colors.primary : 'transparent', opacity: disabled ? 0.5 : 1 }]}>
    {icon && <Ionicons name={icon} size={19} color={tint} />}<Text style={[styles.actionText, { color: tint }]}>{label}</Text>
  </TouchableOpacity>;
}
export function AdminError({ message }: { message: string }) {
  const { colors } = useTheme(); return message ? <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={[styles.error, { color: colors.danger }]}>{message}</Text> : null;
}
const STATES: Record<string, { label: string; tone: 'success' | 'warning' | 'danger' }> = {
  ONLINE: { label: 'Online', tone: 'success' }, ACTIVE: { label: 'Ativo', tone: 'success' }, OPERATIONAL: { label: 'Operacional', tone: 'success' },
  APPROVED: { label: 'Aprovada', tone: 'success' }, WARNING: { label: 'Alerta', tone: 'warning' }, DEGRADED: { label: 'Degradado', tone: 'warning' },
  PENDING: { label: 'Pendente', tone: 'warning' }, FAILURE: { label: 'Falha', tone: 'danger' }, DOWN: { label: 'Indisponível', tone: 'danger' },
  BLOCKED: { label: 'Bloqueada', tone: 'danger' }, REJECTED: { label: 'Recusada', tone: 'danger' }, UNREACHABLE: { label: 'Sem resposta', tone: 'danger' },
};
export function StatusBadge({ value }: { value: string | null | undefined }) {
  const { colors } = useTheme(); const state = value && Object.prototype.hasOwnProperty.call(STATES, value) ? STATES[value] : null;
  const color = state ? state.tone === 'warning' && !colors.isDark ? '#9A5800' : colors[state.tone] : colors.subtext;
  return <View style={[styles.badge, { borderColor: color }]}><Text maxFontSizeMultiplier={1.3} style={[styles.badgeText, { color }]}>{state?.label ?? 'Indisponível'}</Text></View>;
}
export function AdminMetric({ label, value }: { label: string; value: string }) {
  const { colors } = useTheme(); return <View style={styles.metric}><Text style={[styles.subtitle, { color: colors.subtext }]}>{label}</Text>
    <Text style={[styles.value, { color: colors.text }]}>{value}</Text></View>;
}
export function AdminEmpty({ text }: { text: string }) {
  const { colors } = useTheme(); return <View style={styles.empty}><Ionicons name="folder-open-outline" size={28} color={colors.subtext} /><Text style={[styles.subtitle, { color: colors.subtext }]}>{text}</Text></View>;
}
export function AdminSheet({ title, visible, onClose, disabled, children, footer }: { title: string; visible: boolean; onClose: () => void; disabled?: boolean; children: React.ReactNode; footer?: React.ReactNode }) {
  const { colors } = useTheme(); const insets = useSafeAreaInsets();
  return <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}><View style={[styles.overlay, { paddingTop: Math.max(insets.top + 8, 16) }]}>
    <View style={[styles.sheet, { backgroundColor: colors.background, paddingBottom: insets.bottom, borderColor: colors.border }]}>
      <View style={[styles.handle, { backgroundColor: colors.border }]} /><View style={styles.sheetHeader}>
        <Text style={[styles.cardTitle, styles.flex, { color: colors.text }]}>{title}</Text><TouchableOpacity accessibilityRole="button" accessibilityLabel="Fechar janela" disabled={disabled} style={styles.iconButton} onPress={onClose}>
          <Ionicons name="close-outline" size={24} color={colors.subtext} /></TouchableOpacity></View>
      <FormScrollView contentContainerStyle={styles.content} footer={footer}>{children}</FormScrollView>
    </View></View><ToastHost active={visible} /><ConfirmationHost active={visible} /></Modal>;
}
const styles = StyleSheet.create({
  header: { paddingHorizontal: 20, paddingBottom: 14, flexDirection: 'row', alignItems: 'center', gap: 8 }, flex: { flex: 1, minWidth: 0 },
  title: { fontSize: 25, lineHeight: 32, fontWeight: '700' }, subtitle: { fontSize: 13, lineHeight: 20, flexShrink: 1 },
  iconButton: { minWidth: 48, minHeight: 48, alignItems: 'center', justifyContent: 'center' },
  card: { borderWidth: 1, borderRadius: 16, padding: 16, marginBottom: 12 },
  action: { minHeight: 48, paddingVertical: 10, paddingHorizontal: 12, borderWidth: 1, borderRadius: 12, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 7, marginTop: 8 },
  actionText: { fontSize: 13, lineHeight: 20, fontWeight: '600', flexShrink: 1, textAlign: 'center' }, error: { fontSize: 13, lineHeight: 20, marginBottom: 12 },
  badge: { borderWidth: 1, borderRadius: 14, paddingHorizontal: 8, paddingVertical: 3, alignSelf: 'flex-start' }, badgeText: { fontSize: 11, lineHeight: 16, fontWeight: '600', textAlign: 'center' },
  metric: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 16, marginVertical: 6 }, value: { fontSize: 13, lineHeight: 20, fontWeight: '600', flexShrink: 1, textAlign: 'right' },
  empty: { padding: 24, alignItems: 'center', gap: 12 },
  overlay: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.45)' },
  sheet: { height: '92%', maxHeight: 760, borderWidth: 1, borderTopLeftRadius: 24, borderTopRightRadius: 24, overflow: 'hidden' },
  handle: { width: 38, height: 4, borderRadius: 2, marginTop: 12, alignSelf: 'center' },
  sheetHeader: { paddingHorizontal: 20, paddingVertical: 8, flexDirection: 'row', alignItems: 'center', gap: 10 }, cardTitle: { fontSize: 17, lineHeight: 24, fontWeight: '700' },
  content: { padding: 20, paddingTop: 4, paddingBottom: 24 },
});
