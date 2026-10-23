import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { checkPasswordRules } from '../../utils/validators';
import { useTheme } from '../../context/ThemeContext';

export default function PasswordRules({ password, confirmation }: { password: string; confirmation?: string }) {
  const { colors } = useTheme();
  const rules = checkPasswordRules(password);
  const items: [string, boolean][] = [
    ['Mínimo de 6 caracteres', rules.hasMinLength],
    ['Pelo menos 1 letra maiúscula', rules.hasUppercase],
    ['Pelo menos 1 letra minúscula', rules.hasLowercase],
    ['Pelo menos 1 número', rules.hasNumber],
  ];
  if (confirmation) items.push(['As senhas coincidem', password === confirmation]);
  return (
    <View style={styles.container}>
      {items.map(([label, valid]) => (
        <View key={label} style={styles.row}>
          <Ionicons name={valid ? 'checkmark-circle' : 'ellipse-outline'} size={16}
            color={valid ? colors.success : colors.subtext} />
          <Text style={[styles.text, { color: valid ? colors.success : colors.subtext }]}>{label}</Text>
        </View>
      ))}
    </View>
  );
}
const styles = StyleSheet.create({
  container: { marginBottom: 12, gap: 5 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  text: { flex: 1, fontSize: 13, lineHeight: 18 },
});
