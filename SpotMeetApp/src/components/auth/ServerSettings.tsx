import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../../context/ThemeContext';
import {
  getApiHost,
  getApiPort,
  getDiscoveredHost,
  getUserApiHost,
  isServedByDevServer,
  setApiHost,
  setDiscoveredHost,
} from '../../config/api';
import { clearDiscoveryCache, discoverBackendHost } from '../../config/discovery';

/**
 * Small footer on the login screen that shows which backend the app will call
 * and lets the user type the server IP. Required for standalone builds (APK/IPA),
 * where the app cannot discover the developer machine automatically.
 */
export default function ServerSettings() {
  const { colors } = useTheme();
  const [expanded, setExpanded] = useState(false);
  const [draft, setDraft] = useState(getUserApiHost() ?? '');
  const [, forceRender] = useState(0);
  const [scanning, setScanning] = useState(false);
  const [scanProgress, setScanProgress] = useState('');

  const currentHost = getApiHost();
  const isManual = getUserApiHost() !== null;
  const autoAvailable = isServedByDevServer();
  const discovered = getDiscoveredHost();
  const sourceLabel = isManual ? 'manual' : autoAvailable ? 'Expo' : discovered ? 'encontrado na rede' : 'não encontrado';

  const scan = async () => {
    setScanning(true);
    setScanProgress('');
    await clearDiscoveryCache();
    const result = await discoverBackendHost((scanned, total) => setScanProgress(`${scanned}/${total}`));
    setDiscoveredHost(result.host);
    if (result.host) {
      await setApiHost(null);
      setDraft('');
      setExpanded(false);
    }
    setScanning(false);
    forceRender((n) => n + 1);
  };

  const save = async () => {
    await setApiHost(draft);
    forceRender((n) => n + 1);
    setExpanded(false);
  };

  const reset = async () => {
    await setApiHost(null);
    setDraft('');
    forceRender((n) => n + 1);
    setExpanded(false);
  };

  return (
    <View style={styles.container}>
      <TouchableOpacity style={styles.summaryRow} onPress={() => setExpanded((v) => !v)}>
        <Ionicons name="server-outline" size={14} color={colors.subtext} />
        <Text style={[styles.summaryText, { color: colors.subtext }]}>
          Servidor: {currentHost}:{getApiPort()} ({sourceLabel})
        </Text>
        <Ionicons name={expanded ? 'chevron-up' : 'chevron-down'} size={14} color={colors.subtext} />
      </TouchableOpacity>

      {expanded && (
        <View style={styles.editor}>
          <Text style={[styles.hint, { color: colors.subtext }]}>
            {autoAvailable
              ? 'Detectado pelo servidor do Expo. Informe um IP apenas se o backend estiver em outra máquina.'
              : 'O app procura o backend automaticamente no Wi-Fi. Se não encontrar, toque em "Procurar na rede" ou informe o IP do computador.'}
          </Text>
          {!autoAvailable && (
            <TouchableOpacity
              style={[styles.button, styles.scanButton, { borderColor: colors.primary }]}
              onPress={scan}
              disabled={scanning}
            >
              {scanning ? (
                <View style={styles.scanningRow}>
                  <ActivityIndicator size="small" color={colors.primary} />
                  <Text style={[styles.buttonSecondaryText, { color: colors.primary }]}>Procurando... {scanProgress}</Text>
                </View>
              ) : (
                <Text style={[styles.buttonSecondaryText, { color: colors.primary }]}>Procurar na rede</Text>
              )}
            </TouchableOpacity>
          )}
          <TextInput
            style={[styles.input, { backgroundColor: colors.inputBg, color: colors.text, borderColor: colors.border }]}
            placeholder="Ex.: 192.168.1.185"
            placeholderTextColor={colors.subtext}
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
            value={draft}
            onChangeText={setDraft}
          />
          <View style={styles.actions}>
            <TouchableOpacity style={[styles.button, { backgroundColor: colors.primary }]} onPress={save}>
              <Text style={styles.buttonText}>Salvar</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.button, styles.buttonSecondary, { borderColor: colors.border }]}
              onPress={reset}
            >
              <Text style={[styles.buttonSecondaryText, { color: colors.text }]}>Automático</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginTop: 18,
  },
  summaryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  summaryText: {
    fontSize: 12,
  },
  editor: {
    marginTop: 10,
  },
  hint: {
    fontSize: 12,
    lineHeight: 17,
    marginBottom: 8,
    textAlign: 'center',
  },
  input: {
    borderRadius: 10,
    padding: 12,
    fontSize: 14,
    borderWidth: 1,
    marginBottom: 10,
  },
  actions: {
    flexDirection: 'row',
    gap: 10,
  },
  button: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 10,
    alignItems: 'center',
  },
  buttonText: {
    color: '#FFF',
    fontWeight: 'bold',
    fontSize: 13,
  },
  buttonSecondary: {
    backgroundColor: 'transparent',
    borderWidth: 1,
  },
  buttonSecondaryText: {
    fontWeight: 'bold',
    fontSize: 13,
  },
  scanButton: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    marginBottom: 10,
  },
  scanningRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
});
