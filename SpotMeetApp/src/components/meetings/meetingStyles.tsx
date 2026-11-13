import React, { useMemo } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons, type IoniconsIconName } from '@react-native-vector-icons/ionicons';
import { useTheme, type ThemeColors } from '../../context/ThemeContext';

/** Styles shared by the meeting screens and components, built from the same theme tokens as the other screens. */
export function createMeetingStyles(colors: ThemeColors) {
  const accent = colors.isDark ? '#ADB5EF' : colors.primary;
  const soft = colors.isDark ? '#2A2D45' : '#EBEDF7';
  return StyleSheet.create({
    flex: { flex: 1, minWidth: 0 },
    row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
    title: { fontSize: 30, lineHeight: 38, fontWeight: '700', color: colors.text, flexShrink: 1 },
    subtitle: { fontSize: 13, lineHeight: 20, color: colors.subtext, flexShrink: 1 },
    sectionLabel: { color: colors.subtext, fontSize: 11, lineHeight: 18, fontWeight: '700', letterSpacing: 0.8, marginBottom: 10, marginTop: 8 },
    card: { padding: 16, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 16, marginBottom: 12 },
    cardTitle: { color: colors.text, fontSize: 15, lineHeight: 22, fontWeight: '700', flexShrink: 1, marginBottom: 3 },
    hint: { fontSize: 12, lineHeight: 18, color: colors.subtext, marginVertical: 5 },
    error: { fontSize: 13, lineHeight: 19, color: colors.danger, marginBottom: 10 },
    badges: { flexDirection: 'row', gap: 6, flexWrap: 'wrap', marginTop: 8 },
    badge: { alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', gap: 4, borderRadius: 14, paddingHorizontal: 9, paddingVertical: 3 },
    badgeText: { fontSize: 12, lineHeight: 17, fontWeight: '600' },
    actions: { flexDirection: 'row', gap: 8, flexWrap: 'wrap', marginTop: 8 },
    action: { flexGrow: 1, minWidth: 130, minHeight: 48, paddingVertical: 10, paddingHorizontal: 12, borderWidth: 1, borderColor: colors.primary, borderRadius: 12,
      flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7, marginBottom: 8 },
    actionText: { color: colors.primary, fontSize: 14, lineHeight: 19, fontWeight: '600', flexShrink: 1, textAlign: 'center' },
    infoRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 12, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: colors.border },
    infoText: { color: colors.text, fontSize: 15, lineHeight: 21, flex: 1, minWidth: 0 },
    infoSub: { color: colors.subtext, fontSize: 13, lineHeight: 19, marginTop: 2 },
    empty: { alignItems: 'center', gap: 10, padding: 20, marginVertical: 10 },
    filters: { flexDirection: 'row', gap: 8, flexWrap: 'wrap', marginBottom: 14 },
    filter: { minHeight: 44, paddingHorizontal: 16, paddingVertical: 10, justifyContent: 'center', borderRadius: 24, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.card },
    filterActive: { backgroundColor: colors.primary, borderColor: colors.primary },
    filterText: { fontSize: 14, lineHeight: 19, fontWeight: '600', color: colors.text },
    personRow: { paddingVertical: 10, borderTopWidth: 1, borderTopColor: colors.border },
    footer: { backgroundColor: colors.card, padding: 16, paddingHorizontal: 20, borderTopWidth: 1, borderColor: colors.border },
    overlay: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.45)', paddingTop: 48 },
    sheet: { height: '92%', maxHeight: 820, backgroundColor: colors.card, borderTopLeftRadius: 24, borderTopRightRadius: 24, borderWidth: 1, borderColor: colors.border, overflow: 'hidden' },
    sheetHandle: { width: 38, height: 4, backgroundColor: colors.border, borderRadius: 2, alignSelf: 'center', marginTop: 12 },
    sheetHeader: { flexDirection: 'row', gap: 10, alignItems: 'center', paddingHorizontal: 20, paddingVertical: 8 },
    sheetTitle: { color: colors.text, fontSize: 22, lineHeight: 28, fontWeight: '700' },
    iconButton: { minWidth: 48, minHeight: 48, alignItems: 'center', justifyContent: 'center' },
    content: { padding: 20, paddingTop: 4, paddingBottom: 24 },
    fieldLabel: { color: colors.subtext, fontSize: 11, lineHeight: 16, fontWeight: '700', letterSpacing: 0.5, marginBottom: 6 },
    fieldError: { fontSize: 13, lineHeight: 18, color: colors.danger, marginTop: 5, marginBottom: 8 },
    pickerFrame: { minHeight: 50, borderWidth: 1, borderColor: colors.border, borderRadius: 14, backgroundColor: colors.inputBg,
      paddingHorizontal: 14, flexDirection: 'row', alignItems: 'center', gap: 10 },
    pickerText: { flex: 1, minWidth: 0, color: colors.text, fontSize: 16, lineHeight: 22 },
    option: { minHeight: 56, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10, paddingHorizontal: 14, paddingVertical: 8, borderRadius: 14, marginBottom: 4 },
    optionActive: { backgroundColor: soft },
    optionText: { color: colors.text, fontSize: 16, lineHeight: 22, flexShrink: 1 },
    numberInput: { minHeight: 50, borderWidth: 1, borderColor: colors.border, borderRadius: 14, backgroundColor: colors.inputBg, color: colors.text,
      fontSize: 16, paddingHorizontal: 14, textAlign: 'left' },
    segment: { flexDirection: 'row', backgroundColor: colors.inputBg, borderRadius: 14, padding: 4, minHeight: 50 },
    segmentItem: { flex: 1, minHeight: 42, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
    segmentActive: { backgroundColor: colors.isDark ? soft : '#FFFFFF' },
    segmentText: { color: colors.subtext, fontSize: 14, lineHeight: 19 },
    dayCircle: { width: 42, height: 42, borderRadius: 21, backgroundColor: colors.inputBg, alignItems: 'center', justifyContent: 'center' },
    dayCircleActive: { backgroundColor: colors.primary },
    dayCircleText: { color: colors.text, fontSize: 15, fontWeight: '700' },
    notice: { flexDirection: 'row', gap: 12, alignItems: 'flex-start', padding: 14, borderRadius: 14, backgroundColor: soft },
    noticeWarning: { backgroundColor: colors.isDark ? '#3D3018' : '#FEEDDB' },
    noticeDanger: { backgroundColor: colors.isDark ? '#3D1E22' : '#FDE4E4' },
    link: { color: accent, fontSize: 14, lineHeight: 19, fontWeight: '600' },
    avatar: { width: 44, height: 44, borderRadius: 22, backgroundColor: soft, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
    avatarText: { color: accent, fontSize: 16, fontWeight: '700' },
    fileCard: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 12, paddingLeft: 14, borderRadius: 14, borderWidth: 1, borderColor: colors.border,
      backgroundColor: colors.inputBg, marginBottom: 4 },
    dateBlock: { width: 56, paddingVertical: 8, borderRadius: 12, backgroundColor: soft, alignItems: 'center' },
    dateBlockSmall: { color: accent, fontSize: 11, lineHeight: 15, fontWeight: '700', letterSpacing: 0.6 },
    dateBlockDay: { color: accent, fontSize: 22, lineHeight: 27, fontWeight: '700' },
    liveCard: { borderColor: colors.primary, borderWidth: 1.5 },
    fab: { position: 'absolute', right: 20, width: 60, height: 60, borderRadius: 20, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center',
      elevation: 6, shadowColor: colors.primary, shadowOpacity: 0.35, shadowRadius: 10, shadowOffset: { width: 0, height: 4 } },
    banner: { flexDirection: 'row', gap: 12, alignItems: 'flex-start', padding: 16, borderRadius: 16, backgroundColor: soft, marginBottom: 6 },
  });
}

export type MeetingStyles = ReturnType<typeof createMeetingStyles>;

export function useMeetingStyles() {
  const { colors } = useTheme();
  const styles = useMemo(() => createMeetingStyles(colors), [colors]);
  return { colors, styles };
}

interface BadgeProps { text: string; color?: string; icon?: IoniconsIconName; }
/** Colored chip with a light background, as in the design ("Agendada", "Alta", "Aceitou"). */
export function MeetingBadge({ text, color, icon }: BadgeProps) {
  const { colors, styles } = useMeetingStyles();
  const tint = color ?? (colors.isDark ? '#ADB5EF' : colors.primary);
  return <View style={[styles.badge, { backgroundColor: `${tint}22` }]}>
    {icon ? <Ionicons name={icon} size={14} color={tint} /> : null}
    <Text style={[styles.badgeText, { color: tint }]}>{text}</Text>
  </View>;
}

interface ActionProps { label: string; icon: IoniconsIconName; onPress: () => void; danger?: boolean; filled?: boolean; muted?: boolean; disabled?: boolean; }
export function MeetingAction({ label, icon, onPress, danger, filled, muted, disabled }: ActionProps) {
  const { colors, styles } = useMeetingStyles();
  const tint = danger ? colors.danger : filled ? '#FFFFFF' : muted ? colors.subtext : colors.isDark ? '#ADB5EF' : colors.primary;
  return (
    <TouchableOpacity accessibilityRole="button" accessibilityLabel={label} accessibilityState={{ disabled: !!disabled }} disabled={disabled} onPress={onPress}
      style={[styles.action, danger && { borderColor: colors.danger }, filled && { backgroundColor: colors.primary },
        muted && { backgroundColor: colors.inputBg, borderColor: colors.inputBg }, disabled && { opacity: 0.5 }]}>
      <Ionicons name={icon} size={20} color={tint} /><Text style={[styles.actionText, { color: tint }]}>{label}</Text>
    </TouchableOpacity>
  );
}
