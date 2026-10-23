import React, { useMemo } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons, type IoniconsIconName } from '@react-native-vector-icons/ionicons';
import { useTheme, type ThemeColors } from '../../context/ThemeContext';

/** Styles shared by the meeting screens and components, built from the same theme tokens as the other screens. */
export function createMeetingStyles(colors: ThemeColors) {
  return StyleSheet.create({
    flex: { flex: 1, minWidth: 0 },
    row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
    title: { fontSize: 25, lineHeight: 32, fontWeight: '700', color: colors.text, flexShrink: 1 },
    subtitle: { fontSize: 13, lineHeight: 20, color: colors.subtext, flexShrink: 1 },
    sectionLabel: { color: colors.subtext, fontSize: 11, lineHeight: 18, fontWeight: '700', letterSpacing: 0.6, marginBottom: 10, marginTop: 8 },
    card: { padding: 16, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 16, marginBottom: 12 },
    cardTitle: { color: colors.text, fontSize: 15, lineHeight: 22, fontWeight: '700', flexShrink: 1, marginBottom: 3 },
    hint: { fontSize: 12, lineHeight: 18, color: colors.subtext, marginVertical: 5 },
    error: { fontSize: 13, lineHeight: 19, color: colors.danger, marginBottom: 10 },
    badges: { flexDirection: 'row', gap: 6, flexWrap: 'wrap', marginTop: 8 },
    badge: { alignSelf: 'flex-start', borderWidth: 1, borderRadius: 14, paddingHorizontal: 8, paddingVertical: 3, marginTop: 4 },
    badgeText: { fontSize: 11, lineHeight: 16, fontWeight: '600' },
    actions: { flexDirection: 'row', gap: 8, flexWrap: 'wrap', marginTop: 8 },
    action: { flexGrow: 1, minWidth: 130, minHeight: 48, paddingVertical: 10, paddingHorizontal: 12, borderWidth: 1, borderColor: colors.primary, borderRadius: 12,
      flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7, marginBottom: 8 },
    actionText: { color: colors.primary, fontSize: 13, lineHeight: 18, fontWeight: '600', flexShrink: 1, textAlign: 'center' },
    infoRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, marginTop: 10 },
    infoText: { color: colors.text, fontSize: 14, lineHeight: 20, flex: 1, minWidth: 0 },
    empty: { alignItems: 'center', gap: 10, padding: 20, marginVertical: 10 },
    filters: { flexDirection: 'row', gap: 8, flexWrap: 'wrap', marginBottom: 12 },
    filter: { minHeight: 48, paddingHorizontal: 14, paddingVertical: 12, justifyContent: 'center', borderRadius: 24, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.card },
    filterActive: { backgroundColor: colors.primary, borderColor: colors.primary },
    filterText: { fontSize: 13, lineHeight: 18, fontWeight: '600', color: colors.text },
    personRow: { paddingVertical: 10, borderTopWidth: 1, borderTopColor: colors.border },
    footer: { backgroundColor: colors.card, padding: 16, paddingHorizontal: 20, borderTopWidth: 1, borderColor: colors.border },
    overlay: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.45)', paddingTop: 48 },
    sheet: { height: '90%', maxHeight: 760, backgroundColor: colors.background, borderTopLeftRadius: 24, borderTopRightRadius: 24, borderWidth: 1, borderColor: colors.border, overflow: 'hidden' },
    sheetHandle: { width: 38, height: 4, backgroundColor: colors.border, borderRadius: 2, alignSelf: 'center', marginTop: 12 },
    sheetHeader: { flexDirection: 'row', gap: 10, alignItems: 'center', paddingHorizontal: 20, paddingVertical: 8 },
    iconButton: { minWidth: 48, minHeight: 48, alignItems: 'center', justifyContent: 'center' },
    content: { padding: 20, paddingTop: 4, paddingBottom: 24 },
  });
}

export type MeetingStyles = ReturnType<typeof createMeetingStyles>;

export function useMeetingStyles() {
  const { colors } = useTheme();
  const styles = useMemo(() => createMeetingStyles(colors), [colors]);
  return { colors, styles };
}

interface BadgeProps { text: string; color?: string; }
export function MeetingBadge({ text, color }: BadgeProps) {
  const { colors, styles } = useMeetingStyles();
  const tint = color ?? colors.primary;
  return <View style={[styles.badge, { borderColor: tint }]}><Text style={[styles.badgeText, { color: tint }]}>{text}</Text></View>;
}

interface ActionProps { label: string; icon: IoniconsIconName; onPress: () => void; danger?: boolean; filled?: boolean; disabled?: boolean; }
export function MeetingAction({ label, icon, onPress, danger, filled, disabled }: ActionProps) {
  const { colors, styles } = useMeetingStyles();
  const tint = danger ? colors.danger : filled ? '#FFFFFF' : colors.isDark ? '#ADB5EF' : colors.primary;
  return (
    <TouchableOpacity accessibilityRole="button" accessibilityLabel={label} accessibilityState={{ disabled: !!disabled }} disabled={disabled} onPress={onPress}
      style={[styles.action, danger && { borderColor: colors.danger }, filled && { backgroundColor: colors.primary }, disabled && { opacity: 0.5 }]}>
      <Ionicons name={icon} size={19} color={tint} /><Text style={[styles.actionText, { color: tint }]}>{label}</Text>
    </TouchableOpacity>
  );
}
