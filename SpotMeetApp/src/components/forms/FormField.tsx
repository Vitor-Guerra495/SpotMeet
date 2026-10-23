import React, { forwardRef, useState } from 'react';
import { View, Text, TextInput, StyleSheet, TextInputProps } from 'react-native';
import { Ionicons, type IoniconsIconName } from '@react-native-vector-icons/ionicons';
import { useTheme } from '../../context/ThemeContext';
import { useAuthInputFocus, useAuthCompactLayout } from '../auth/AuthInputFocus';

export interface FormFieldProps extends TextInputProps {
  label: string;
  required?: boolean;
  error?: string;
  icon?: IoniconsIconName;
  prefix?: string;
  trailing?: React.ReactNode;
}

const FormField = forwardRef<TextInput, FormFieldProps>(function FormField(
  { label, required, error, icon, prefix, trailing, style, onFocus, onBlur, ...props }, ref,
) {
  const { colors } = useTheme();
  const revealInput = useAuthInputFocus();
  const compact = useAuthCompactLayout();
  const [focused, setFocused] = useState(false);
  const borderColor = error ? colors.danger : focused ? colors.primary : colors.border;
  return (
    <View style={[styles.field, { marginBottom: compact ? 8 : 12 }]}>
      <Text style={[styles.label, { color: colors.subtext, marginBottom: compact ? 4 : 5 }]}>
        {label.toUpperCase()}{required ? ' *' : ''}
      </Text>
      <View style={[styles.frame, { backgroundColor: colors.inputBg, borderColor, minHeight: compact ? 48 : 50 }]}>
        {icon ? <Ionicons name={icon} size={20} color={focused ? colors.primary : colors.subtext} /> : null}
        {prefix ? <Text style={{ color: colors.text, fontSize: 16 }}>{prefix}</Text> : null}
        <TextInput
          {...props}
          ref={ref}
          accessibilityLabel={props.accessibilityLabel ?? `${label}${required ? ', obrigatório' : ''}`}
          accessibilityHint={error || props.accessibilityHint}
          placeholderTextColor={colors.subtext}
          style={[styles.input, { color: colors.text, outlineWidth: 0 }, style]}
          onFocus={event => {
            setFocused(true);
            revealInput(event.nativeEvent.target);
            onFocus?.(event);
          }}
          onBlur={event => { setFocused(false); onBlur?.(event); }}
        />
        {trailing}
      </View>
      {error ? (
        <Text accessibilityRole="alert" accessibilityLiveRegion="polite"
          style={[styles.error, { color: colors.danger }]}>{error}</Text>
      ) : null}
    </View>
  );
});
export default FormField;

const styles = StyleSheet.create({
  field: { marginBottom: 12 },
  label: { fontSize: 11, lineHeight: 16, fontWeight: '700', marginBottom: 5, letterSpacing: 0.5 },
  frame: { minHeight: 50, borderWidth: 1, borderRadius: 14, paddingLeft: 14, flexDirection: 'row', alignItems: 'center', gap: 10 },
  input: { flex: 1, minWidth: 0, paddingVertical: 12, paddingRight: 12, fontSize: 16 },
  error: { fontSize: 13, lineHeight: 18, marginTop: 5 },
});
