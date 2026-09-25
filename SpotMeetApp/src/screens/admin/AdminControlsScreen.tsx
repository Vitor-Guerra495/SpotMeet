import React, { useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView,
  TextInput, Modal, ActivityIndicator, Alert, Platform,
  KeyboardAvoidingView,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useAuth } from '../../context/AuthContext';
import { API_BASE_URL } from '../../config/api';
import { showAlert } from '../../utils/alerts';

// Confirmation codes expected by the backend for critical actions
const RESTART_CONFIRMATION_CODE = 'RESTART_SYSTEM';
const RESET_CONFIRMATION_CODE = 'CONFIRM_FULL_RESET';

type Subsystem = 'DATABASE' | 'NETWORK' | 'RESOURCES';

// Subsystems that can be restarted individually
const SUBSYSTEMS: { key: Subsystem; label: string; description: string; icon: any }[] = [
  { key: 'DATABASE', label: 'Banco de Dados', description: 'Renova as conexões com o PostgreSQL', icon: 'server-outline' },
  { key: 'NETWORK', label: 'Rede', description: 'Confere novamente o endereço e a porta do servidor', icon: 'wifi-outline' },
  { key: 'RESOURCES', label: 'Recursos', description: 'Libera a memória não utilizada', icon: 'hardware-chip-outline' },
];

export default function AdminControlsScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const { token, role, name, email, signOut } = useAuth();

  const [loading, setLoading] = useState(false);
  const [restartModal, setRestartModal] = useState(false);
  const [resetModal, setResetModal] = useState(false);

  const [restartReason, setRestartReason] = useState('');
  const [restartSubsystem, setRestartSubsystem] = useState<Subsystem | null>(null);
  const [resetConfirmationCode, setResetConfirmationCode] = useState('');
  const [resetReason, setResetReason] = useState('');

  const isAdmin = role === 'ADMIN' || role === 'SYSADMIN';

  const closeRestartModal = () => {
    setRestartModal(false);
    setRestartSubsystem(null);
    setRestartReason('');
  };

  const handleRestart = async () => {
    if (!restartSubsystem) {
      showAlert('Atenção', 'Escolha o subsistema que será reiniciado.');
      return;
    }
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/admin/system/restart`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          confirmation: RESTART_CONFIRMATION_CODE,
          subsystem: restartSubsystem,
          reason: restartReason.trim() || 'Reinicialização rotineira de subsistemas',
        }),
      });

      if (!res.ok) {
        const errorMsg = await res.text();
        throw new Error(errorMsg || 'Falha ao reiniciar subsistemas.');
      }

      const data = await res.json();
      closeRestartModal();
      showAlert('Sucesso', data.message || 'Subsistema reiniciado.');
    } catch (err: any) {
      showAlert('Erro', err.message || 'Não foi possível executar a reinicialização.');
    } finally {
      setLoading(false);
    }
  };

  const handleReset = async () => {
    if (resetConfirmationCode !== RESET_CONFIRMATION_CODE) {
      showAlert('Código Inválido', `Digite exatamente ${RESET_CONFIRMATION_CODE} para prosseguir.`);
      return;
    }

    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/admin/system/reset`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          confirmation: RESET_CONFIRMATION_CODE,
          reason: resetReason.trim() || 'Manutenção administrativa de segurança',
        }),
      });

      if (!res.ok) {
        const errorMsg = await res.text();
        throw new Error(errorMsg || 'Falha ao executar reset do sistema.');
      }

      const data = await res.json();
      setResetModal(false);
      setResetConfirmationCode('');
      setResetReason('');

      const redirect = () => {
        signOut();
        navigation.reset({
          index: 0,
          routes: [{ name: 'Auth' }],
        });
      };

      showAlert(
        'Reset Total Concluído',
        `${data.message || 'Limpeza efetuada com sucesso.'} Sua sessão foi encerrada por segurança.`,
      );
      redirect();
    } catch (err: any) {
      showAlert('Erro', err.message || 'Falha no reset do sistema.');
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = () => {
    const execute = () => {
      signOut();
      navigation.reset({
        index: 0,
        routes: [{ name: 'Auth' }],
      });
    };

    if (Platform.OS === 'web') {
      if (window.confirm('Deseja realmente encerrar a sessão administrativa?')) {
        execute();
      }
    } else {
      Alert.alert('Encerrar Sessão', 'Deseja realmente deslogar do Painel do Administrador?', [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Sair com Segurança', onPress: execute, style: 'destructive' },
      ]);
    }
  };

  if (!isAdmin) {
    return (
      <View style={styles.centerContainer}>
        <Ionicons name="lock-closed" size={48} color="#C62828" />
        <Text style={styles.accessDeniedTitle}>Acesso Restrito</Text>
        <Text style={styles.accessDeniedText}>Esta área exige privilégios de Administrador.</Text>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView behavior="padding" style={styles.container}>
      {/* Header */}
      <View style={[styles.header, { paddingTop: insets.top + 12 }]}>
        <View style={styles.headerLeft}>
          <View style={styles.iconContainer}>
            <Ionicons name="construct" size={18} color="#FFF" />
          </View>
          <View>
            <Text style={styles.headerTitle}>Controles do Sistema</Text>
            <Text style={styles.headerSubtitle}>Ações Críticas & Auditoria</Text>
          </View>
        </View>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Operator identification */}
        <View style={styles.operatorCard}>
          <View style={styles.operatorAvatar}>
            <Ionicons name="shield" size={24} color="#5C6BC0" />
          </View>
          <View style={styles.operatorInfo}>
            <Text style={styles.operatorName}>{name || 'Administrador do Sistema'}</Text>
            <Text style={styles.operatorEmail}>{email || 'sysadmin@spotmeet.com'}</Text>
            <View style={styles.roleTag}>
              <Text style={styles.roleText}>ROLE: {role}</Text>
            </View>
          </View>
        </View>

        {/* LGPD audit note, kept discreet */}
        <View style={styles.securityAlert}>
          <Ionicons name="information-circle-outline" size={13} color="#8A8A8A" style={{ marginRight: 6 }} />
          <Text style={styles.securityAlertText}>
            Todas as ações críticas são registradas na auditoria com o administrador responsável e a data e hora.
          </Text>
        </View>

        {/* Block: restart */}
        <Text style={styles.sectionHeader}>Gerenciamento de Instância</Text>

        <View style={styles.actionCard}>
          <View style={styles.actionHeader}>
            <Ionicons name="refresh-circle" size={24} color="#4FC3F7" />
            <View style={{ marginLeft: 10, flex: 1 }}>
              <Text style={styles.actionTitle}>Reiniciar Subsistemas</Text>
              <Text style={styles.actionDescription}>
                Reinicia um subsistema por vez (Banco de Dados, Rede ou Recursos) sem interromper o acesso dos usuários.
              </Text>
            </View>
          </View>
          <TouchableOpacity
            style={styles.btnActionRestart}
            onPress={() => setRestartModal(true)}
          >
            <Ionicons name="reload" size={16} color="#FFF" style={{ marginRight: 6 }} />
            <Text style={styles.btnActionText}>Escolher Subsistema</Text>
          </TouchableOpacity>
        </View>

        {/* Block: full system reset */}
        <Text style={styles.sectionHeader}>Ações Críticas Protegidas</Text>

        <View style={[styles.actionCard, styles.dangerBorder]}>
          <View style={styles.actionHeader}>
            <Ionicons name="trash" size={24} color="#E53935" />
            <View style={{ marginLeft: 10, flex: 1 }}>
              <Text style={styles.actionTitle}>Reset Total do Sistema</Text>
              <Text style={styles.actionDescription}>
                Executa limpeza transacional completa (wipe/truncate em cascata) de todas as organizações, comissões, membros e usuários comuns. Preserva estritamente apenas a conta do Administrador principal.
              </Text>
            </View>
          </View>
          <TouchableOpacity
            style={styles.btnActionReset}
            onPress={() => setResetModal(true)}
          >
            <Ionicons name="trash-outline" size={16} color="#FFF" style={{ marginRight: 6 }} />
            <Text style={styles.btnActionText}>Executar Reset Total do Sistema</Text>
          </TouchableOpacity>
        </View>

        {/* Block: logout */}
        <TouchableOpacity style={styles.btnLogout} onPress={handleLogout}>
          <Ionicons name="log-out-outline" size={18} color="#FF6B6B" style={{ marginRight: 8 }} />
          <Text style={styles.btnLogoutText}>Encerrar Sessão Administrativa</Text>
        </TouchableOpacity>
      </ScrollView>

      {/* Modal: restart */}
      <Modal visible={restartModal} transparent animationType="fade">
        <KeyboardAvoidingView behavior="padding" style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <View style={styles.modalHeader}>
              <Ionicons name="refresh-circle" size={26} color="#4FC3F7" />
              <Text style={styles.modalTitle}>Confirmar Reinicialização</Text>
            </View>
            <Text style={styles.modalText}>
              Escolha o subsistema que será reiniciado:
            </Text>

            {SUBSYSTEMS.map((item) => {
              const selected = restartSubsystem === item.key;
              return (
                <TouchableOpacity
                  key={item.key}
                  style={[styles.subsystemOption, selected && styles.subsystemOptionSelected]}
                  onPress={() => setRestartSubsystem(item.key)}
                  disabled={loading}
                >
                  <Ionicons name={item.icon} size={20} color={selected ? '#4FC3F7' : '#AAA'} />
                  <View style={{ marginLeft: 10, flex: 1 }}>
                    <Text style={styles.subsystemOptionTitle}>{item.label}</Text>
                    <Text style={styles.subsystemOptionDescription}>{item.description}</Text>
                  </View>
                  <Ionicons
                    name={selected ? 'radio-button-on' : 'radio-button-off'}
                    size={18}
                    color={selected ? '#4FC3F7' : '#666'}
                  />
                </TouchableOpacity>
              );
            })}

            <TextInput
              style={styles.modalInput}
              placeholder="Motivo da reinicialização (opcional)"
              placeholderTextColor="#888"
              value={restartReason}
              onChangeText={setRestartReason}
            />

            <View style={styles.modalActions}>
              <TouchableOpacity
                style={styles.btnModalCancel}
                onPress={closeRestartModal}
                disabled={loading}
              >
                <Text style={styles.btnModalCancelText}>Cancelar</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.btnModalConfirmRestart, !restartSubsystem && styles.btnDisabled]}
                onPress={handleRestart}
                disabled={loading || !restartSubsystem}
              >
                {loading ? (
                  <ActivityIndicator color="#FFF" size="small" />
                ) : (
                  <Text style={styles.btnModalConfirmText}>Reiniciar Agora</Text>
                )}
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>

      {/* Modal: full system reset */}
      <Modal visible={resetModal} transparent animationType="fade">
        <KeyboardAvoidingView behavior="padding" style={styles.modalOverlay}>
          <View style={[styles.modalContent, styles.dangerBorder]}>
            <View style={styles.modalHeader}>
              <Ionicons name="warning" size={26} color="#E53935" />
              <Text style={styles.modalTitle}>Reset Total do Sistema</Text>
            </View>
            <Text style={styles.modalText}>
              Esta ação irreversível apagará TODAS as organizações, comissões, membros e usuários comuns. Somente a sua conta de Administrador será preservada. Digite exatamente:
            </Text>

            <View style={styles.confirmationKeyBox}>
              <Text style={styles.confirmationKeyText}>{RESET_CONFIRMATION_CODE}</Text>
            </View>

            <TextInput
              style={styles.modalInput}
              placeholder={`Digite ${RESET_CONFIRMATION_CODE}`}
              placeholderTextColor="#888"
              autoCapitalize="characters"
              value={resetConfirmationCode}
              onChangeText={setResetConfirmationCode}
            />

            <TextInput
              style={styles.modalInput}
              placeholder="Motivo formal (Auditoria LGPD)"
              placeholderTextColor="#888"
              value={resetReason}
              onChangeText={setResetReason}
            />

            <View style={styles.modalActions}>
              <TouchableOpacity
                style={styles.btnModalCancel}
                onPress={() => {
                  setResetModal(false);
                  setResetConfirmationCode('');
                  setResetReason('');
                }}
                disabled={loading}
              >
                <Text style={styles.btnModalCancelText}>Cancelar</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[
                  styles.btnModalConfirmReset,
                  resetConfirmationCode !== RESET_CONFIRMATION_CODE && styles.btnDisabled,
                ]}
                onPress={handleReset}
                disabled={loading || resetConfirmationCode !== RESET_CONFIRMATION_CODE}
              >
                {loading ? (
                  <ActivityIndicator color="#FFF" size="small" />
                ) : (
                  <Text style={styles.btnModalConfirmText}>Executar Reset</Text>
                )}
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#121212',
  },
  centerContainer: {
    flex: 1,
    backgroundColor: '#121212',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  accessDeniedTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#FFF',
    marginTop: 12,
  },
  accessDeniedText: {
    fontSize: 14,
    color: '#A0A0A0',
    marginTop: 6,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 15,
    backgroundColor: '#1E1E1E',
    borderBottomWidth: 1,
    borderBottomColor: '#2C2C2C',
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  iconContainer: {
    width: 36,
    height: 36,
    borderRadius: 8,
    backgroundColor: '#5C6BC0',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#FFF',
  },
  headerSubtitle: {
    fontSize: 12,
    color: '#A0A0A0',
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 30,
  },
  operatorCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1E1E1E',
    borderRadius: 10,
    padding: 16,
    borderWidth: 1,
    borderColor: '#2D2D2D',
    marginBottom: 14,
  },
  operatorAvatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: '#262A3E',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 14,
  },
  operatorInfo: {
    flex: 1,
  },
  operatorName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#FFF',
  },
  operatorEmail: {
    fontSize: 13,
    color: '#90CAF9',
    marginTop: 2,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
  },
  roleTag: {
    alignSelf: 'flex-start',
    backgroundColor: '#3949AB',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 4,
    marginTop: 6,
  },
  roleText: {
    fontSize: 11,
    fontWeight: 'bold',
    color: '#FFF',
  },
  securityAlert: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 2,
    paddingVertical: 6,
    marginBottom: 14,
  },
  securityAlertText: {
    fontSize: 10,
    color: '#8A8A8A',
    flex: 1,
    lineHeight: 14,
  },
  sectionHeader: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#FFF',
    marginTop: 10,
    marginBottom: 10,
  },
  actionCard: {
    backgroundColor: '#1E1E1E',
    borderRadius: 10,
    padding: 16,
    borderWidth: 1,
    borderColor: '#2D2D2D',
    marginBottom: 14,
  },
  dangerBorder: {
    borderColor: '#B71C1C',
  },
  actionHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  actionTitle: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#FFF',
  },
  actionDescription: {
    fontSize: 12,
    color: '#A0A0A0',
    marginTop: 4,
    lineHeight: 16,
  },
  btnActionRestart: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#0277BD',
    paddingVertical: 10,
    borderRadius: 6,
  },
  btnActionReset: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#C62828',
    paddingVertical: 10,
    borderRadius: 6,
  },
  btnActionText: {
    color: '#FFF',
    fontSize: 13,
    fontWeight: 'bold',
  },
  btnLogout: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#241414',
    borderWidth: 1,
    borderColor: '#B71C1C',
    paddingVertical: 12,
    borderRadius: 8,
    marginTop: 10,
  },
  btnLogoutText: {
    color: '#FF8A80',
    fontSize: 14,
    fontWeight: 'bold',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.75)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  modalContent: {
    width: '100%',
    maxWidth: 420,
    backgroundColor: '#1E1E1E',
    borderRadius: 12,
    padding: 20,
    borderWidth: 1,
    borderColor: '#333',
  },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  modalTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#FFF',
    marginLeft: 8,
  },
  modalText: {
    fontSize: 13,
    color: '#CCC',
    lineHeight: 18,
    marginBottom: 12,
  },
  modalInput: {
    backgroundColor: '#2A2A2A',
    borderRadius: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
    color: '#FFF',
    fontSize: 13,
    borderWidth: 1,
    borderColor: '#444',
    marginBottom: 10,
  },
  confirmationKeyBox: {
    backgroundColor: '#2C1414',
    padding: 10,
    borderRadius: 6,
    alignItems: 'center',
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#B71C1C',
  },
  confirmationKeyText: {
    color: '#FF8A80',
    fontWeight: 'bold',
    fontSize: 14,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
  },
  modalActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 10,
    marginTop: 6,
  },
  btnModalCancel: {
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 6,
    backgroundColor: '#333',
  },
  btnModalCancelText: {
    color: '#CCC',
    fontSize: 13,
  },
  btnModalConfirmRestart: {
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 6,
    backgroundColor: '#0277BD',
  },
  btnModalConfirmReset: {
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 6,
    backgroundColor: '#C62828',
  },
  btnModalConfirmText: {
    color: '#FFF',
    fontSize: 13,
    fontWeight: 'bold',
  },
  btnDisabled: {
    opacity: 0.4,
  },
  subsystemOption: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#2A2A2A',
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#444',
    paddingHorizontal: 12,
    paddingVertical: 10,
    marginBottom: 8,
  },
  subsystemOptionSelected: {
    borderColor: '#4FC3F7',
  },
  subsystemOptionTitle: {
    fontSize: 13,
    fontWeight: 'bold',
    color: '#FFF',
  },
  subsystemOptionDescription: {
    fontSize: 11,
    color: '#AAA',
    marginTop: 2,
  },
});
