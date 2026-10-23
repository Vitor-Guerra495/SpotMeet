import React from 'react';
import { TouchableOpacity, Text, ActivityIndicator, StyleSheet, View } from 'react-native';
import { Ionicons, type IoniconsIconName } from '@react-native-vector-icons/ionicons';
import { useTheme } from '../../context/ThemeContext';
import { useAuthCompactLayout } from '../auth/AuthInputFocus';

interface FormButtonProps {
  label: string;
  icon?: IoniconsIconName;
  onPress?: () => void | Promise<void>;
  disabled?: boolean;
  loading?: boolean;
  secondary?: boolean;
}

export default function FormButton({ label, icon, onPress, disabled, loading, secondary }: FormButtonProps) {
  const { colors } = useTheme();
  const compact = useAuthCompactLayout();
  const unavailable = !!disabled || !!loading;
  const color = secondary ? (colors.isDark ? '#9FA8DA' : colors.primary) : '#FFFFFF';
  return (
    <TouchableOpacity
      accessibilityRole="button" accessibilityLabel={label}
      accessibilityState={{ disabled: unavailable, busy: !!loading }}
      disabled={unavailable} onPress={onPress}
      style={[styles.button, {
        backgroundColor: secondary ? 'transparent' : colors.primary,
        borderColor: colors.primary, opacity: unavailable ? 0.55 : 1,
        minHeight: compact ? 48 : 52, paddingVertical: compact ? 10 : 12,
      }]}
    >
      <View style={styles.content}>
        {loading ? <ActivityIndicator color={color} /> : icon ? <Ionicons name={icon} size={20} color={color} /> : null}
        <Text style={[styles.text, { color }]}>{label}</Text>
      </View>
    </TouchableOpacity>
  );
}
const styles = StyleSheet.create({
  button: { minHeight: 52, borderRadius: 14, borderWidth: 1, paddingVertical: 12, paddingHorizontal: 12, marginTop: 8 },
  content: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  text: { flexShrink: 1, fontSize: 16, lineHeight: 22, fontWeight: '600', textAlign: 'center' },
});
