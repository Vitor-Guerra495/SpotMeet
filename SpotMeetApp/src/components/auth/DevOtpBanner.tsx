import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

interface DevOtpBannerProps {
  devCode?: string | null;
  message?: string;
}

export default function DevOtpBanner({ devCode, message }: DevOtpBannerProps) {
  if (!devCode) return null;

  return (
    <View style={styles.devBanner}>
      <Ionicons name="information-circle" size={20} color="#FF9800" style={styles.icon} />
      <View style={styles.content}>
        <Text style={styles.devBannerTitle}>Código OTP de Teste (Dev):</Text>
        <Text style={styles.devBannerCode}>{devCode}</Text>
        <Text style={styles.devBannerDesc}>
          {message || 'Ambiente local sem envio externo. O código acima foi carregado automaticamente para testes.'}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  devBanner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: 'rgba(255, 152, 0, 0.12)',
    borderColor: '#FF9800',
    borderWidth: 1,
    borderRadius: 8,
    padding: 10,
    marginTop: 4,
    marginBottom: 14,
    width: '100%',
  },
  icon: {
    marginRight: 8,
    marginTop: 2,
  },
  content: {
    flex: 1,
  },
  devBannerTitle: {
    color: '#FF9800',
    fontSize: 12,
    fontWeight: 'bold',
  },
  devBannerCode: {
    color: '#FFB74D',
    fontSize: 20,
    fontWeight: 'bold',
    letterSpacing: 2,
    marginVertical: 2,
  },
  devBannerDesc: {
    color: '#E0E0E0',
    fontSize: 11,
    lineHeight: 15,
  },
});
