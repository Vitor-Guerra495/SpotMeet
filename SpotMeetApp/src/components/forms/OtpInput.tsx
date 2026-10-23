import React, { useState } from 'react';
import { View, Text, TextInput, StyleSheet, TextInputProps } from 'react-native';
import { useTheme } from '../../context/ThemeContext';
import { OTP_LENGTH } from '../../utils/validators';
import { useAuthInputFocus } from '../auth/AuthInputFocus';

// The real input is nearly invisible over the boxes, so a placeholder would show through them; use hint instead.
interface OtpInputProps extends Omit<TextInputProps, 'placeholder'> { label: string; error?: string; hint?: string; }

export default function OtpInput({ label, error, hint, onFocus, onBlur, ...props }: OtpInputProps) {
  const { colors } = useTheme();
  const revealInput = useAuthInputFocus();
  const [focused, setFocused] = useState(false);
  const value = props.value ?? '';
  return (
    <View style={styles.field}>
      <Text style={[styles.label, { color: colors.subtext }]}>{label.toUpperCase()} *</Text>
      <View style={styles.area}>
        <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.boxes}>
          {Array.from({ length: OTP_LENGTH }, (_, index) => (
            <View key={index} style={[styles.box, {
              backgroundColor: colors.inputBg,
              borderColor: error ? colors.danger : focused && index === Math.min(value.length, OTP_LENGTH - 1) ? colors.primary : colors.border,
            }]}>
              <Text style={[styles.digit, { color: colors.text }]}>{value[index] ?? ''}</Text>
            </View>
          ))}
        </View>
        <TextInput
          {...props} keyboardType="number-pad" maxLength={OTP_LENGTH}
          autoCorrect={false} autoCapitalize="none" textContentType="oneTimeCode"
          accessibilityLabel={label + ', seis dígitos, obrigatório'}
          accessibilityHint={error}
          caretHidden selectionColor="transparent"
          style={styles.input}
          onFocus={event => { setFocused(true); revealInput(event.nativeEvent.target); onFocus?.(event); }}
          onBlur={event => { setFocused(false); onBlur?.(event); }}
        />
      </View>
      {error ? <Text accessibilityRole="alert" accessibilityLiveRegion="polite"
        style={[styles.error, { color: colors.danger }]}>{error}</Text> : null}
      {hint ? <Text style={[styles.hint, { color: colors.subtext }]}>{hint}</Text> : null}
      <Text style={[styles.hint, { color: colors.subtext }]}>Validade do código: até 5 minutos.</Text>
    </View>
  );
}
const styles = StyleSheet.create({
  field: { marginTop: 10, marginBottom: 16 },
  label: { fontSize: 11, lineHeight: 16, fontWeight: '700', marginBottom: 8 },
  area: { height: 56 },
  boxes: { flexDirection: 'row', gap: 6 },
  box: { flex: 1, minWidth: 0, height: 56, borderWidth: 1, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  digit: { fontSize: 24, fontWeight: '700' },
  input: { position: 'absolute', width: '100%', height: '100%', opacity: 0.02, color: 'transparent', fontSize: 24 },
  error: { fontSize: 13, lineHeight: 18, marginTop: 6 },
  hint: { fontSize: 12, marginTop: 10, textAlign: 'center' },
});
