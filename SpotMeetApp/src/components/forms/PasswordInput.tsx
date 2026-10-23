import React, { forwardRef } from 'react';
import { TextInput, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { useTheme } from '../../context/ThemeContext';
import FormField, { FormFieldProps } from './FormField';

interface PasswordInputProps extends Omit<FormFieldProps, 'secureTextEntry' | 'trailing'> {
  visible: boolean;
  onToggleVisibility: () => void;
}

const PasswordInput = forwardRef<TextInput, PasswordInputProps>(function PasswordInput(
  { visible, onToggleVisibility, ...props }, ref,
) {
  const { colors } = useTheme();
  return (
    <FormField
      {...props} ref={ref} icon={props.icon ?? 'lock-closed-outline'}
      autoCapitalize="none" autoCorrect={false}
      secureTextEntry={!visible}
      trailing={
        <TouchableOpacity
          style={styles.eye} onPress={onToggleVisibility} disabled={props.editable === false}
          accessibilityRole="button"
          accessibilityLabel={`${visible ? 'Ocultar' : 'Mostrar'} ${props.label.toLowerCase()}`}
          accessibilityState={{ disabled: props.editable === false }}
        >
          <Ionicons name={visible ? 'eye-off' : 'eye'} size={21} color={colors.subtext} />
        </TouchableOpacity>
      }
    />
  );
});
export default PasswordInput;
const styles = StyleSheet.create({ eye: { minWidth: 48, minHeight: 48, alignItems: 'center', justifyContent: 'center' } });
