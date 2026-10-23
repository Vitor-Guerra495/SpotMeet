import React, { useEffect, useMemo, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { Ionicons, type IoniconsIconName } from '@react-native-vector-icons/ionicons';
import { Controller, useForm } from 'react-hook-form';
import { useAuth } from '../../context/AuthContext';
import { useTheme, type ThemeColors } from '../../context/ThemeContext';
import { API_BASE_URL } from '../../config/api';
import { useAdminOperation } from '../../hooks/useAdminOperation';
import { showAlert } from '../../utils/alerts';
import { notifySync } from '../../utils/syncBus';
import { isAdminRole, isRecord } from '../../utils/adminData';
import { PASSWORD_REGEX } from '../../utils/validators';
import FormField from '../../components/forms/FormField';
import FormButton from '../../components/forms/FormButton';
import PasswordInput from '../../components/forms/PasswordInput';
import PasswordRules from '../../components/forms/PasswordRules';
import { AdminHeader, AdminCard, AdminAction, AdminError, AdminEmpty, AdminSheet } from '../../components/admin/AdminUi';

const RESET_CODE = 'CONFIRM_FULL_RESET';
type Subsystem = 'DATABASE' | 'RESOURCES';
type ModalMode = 'restart' | 'reset' | 'password' | null;
interface ControlFields { confirmation: string; reason: string; }
interface PasswordFields { currentPassword: string; newPassword: string; confirmPassword: string; }
const EMPTY_PASSWORDS: PasswordFields = { currentPassword: '', newPassword: '', confirmPassword: '' };
interface Props { navigation: { reset: (state: { index: number; routes: { name: string; params: { mode: string } }[] }) => void }; }
const SUBSYSTEMS: { key: Subsystem; label: string; description: string; icon: IoniconsIconName; result: string }[] = [
  { key: 'DATABASE', label: 'Banco de dados', description: 'Renova as conexões do backend e verifica o acesso ao banco. Não reinicia o serviço PostgreSQL.', icon: 'server-outline', result: 'As conexões foram renovadas. Confira o estado do banco no Monitoramento.' },
  { key: 'RESOURCES', label: 'Recursos', description: 'Solicita a coleta de memória não utilizada. A JVM decide quando executá-la. Não reinicia o servidor.', icon: 'hardware-chip-outline', result: 'A coleta de memória foi solicitada. A liberação de memória não é garantida; confira os recursos no Monitoramento.' },
];

export default function AdminControlsScreen({ navigation }: Props) {
  const { token, role, name, signOut, getSession } = useAuth(); const { colors } = useTheme();
  const styles = useMemo(() => createStyles(colors), [colors]), version = getSession().version;
  const [subsystem, setSubsystem] = useState<Subsystem | null>(null), [modal, setModal] = useState<ModalMode>(null);
  const restartForm = useForm<ControlFields>({ defaultValues: { confirmation: '', reason: '' }, mode: 'onBlur' });
  const resetForm = useForm<ControlFields>({ defaultValues: { confirmation: '', reason: '' }, mode: 'onBlur' });
  const passwordForm = useForm<PasswordFields>({ defaultValues: { ...EMPTY_PASSWORDS }, mode: 'onBlur' });
  const [showPasswords, setShowPasswords] = useState(false);
  const operation = useAdminOperation(`${modal ?? ''}:${subsystem ?? ''}`), isAdmin = isAdminRole(role);
  const selected = SUBSYSTEMS.find(item => item.key === subsystem), disabled = operation.busy || operation.confirming;
  const clearForms = () => { restartForm.reset({ reason: '' }); resetForm.reset({ confirmation: '', reason: '' }); passwordForm.reset({ ...EMPTY_PASSWORDS }); setShowPasswords(false); };
  useEffect(() => {
    setModal(null); setSubsystem(null); clearForms();
  }, [token, role, version, restartForm.reset, resetForm.reset, passwordForm.reset, API_BASE_URL]);
  const open = (mode: ModalMode) => {
    if (!operation.isCurrent() || operation.blocked() || mode === 'restart' && !selected) return;
    clearForms(); operation.clearError(); setModal(mode);
  };
  const close = () => { if (operation.isCurrent() && !operation.blocked()) { setModal(null); clearForms(); operation.clearError(); } };
  const endSession = async () => {
    if (!operation.isCurrent()) return;
    setModal(null); clearForms();
    const removal = signOut(); navigation.reset({ index: 0, routes: [{ name: 'Auth', params: { mode: 'login' } }] });
    const endedVersion = getSession().version;
    await removal;
    return endedVersion;
  };
  const restart = restartForm.handleSubmit(values => {
    if (!selected || modal !== 'restart' || !operation.isCurrent() || operation.blocked()) return;
    const chosen = selected;
    return operation.execute({ path: '/admin/system/restart', method: 'POST', body: { confirmation: 'RESTART_SYSTEM', subsystem: chosen.key, reason: values.reason.trim() || 'Reinicialização rotineira de subsistemas' },
      accept: data => isRecord(data) && data.success === true && data.subsystem === chosen.key,
      onSuccess: () => { setModal(null); restartForm.reset({ reason: '' }); showAlert('Solicitação aceita', chosen.result); notifySync('MONITORING_REFRESH'); } });
  });
  const reset = resetForm.handleSubmit(values => {
    if (modal !== 'reset' || !operation.isCurrent() || operation.blocked()) return;
    return operation.execute({ path: '/admin/system/reset', method: 'POST', body: { confirmation: values.confirmation, reason: values.reason.trim() || 'Manutenção administrativa de segurança' },
      accept: data => isRecord(data) && data.success === true,
      onSuccess: async () => { const endedVersion = await endSession(); if (getSession().version === endedVersion && getSession().token === null) showAlert('Reset concluído', 'O sistema foi limpo. Sua sessão foi encerrada. Entre novamente.'); } });
  });
  // The administrator account has no real mailbox, so the change uses only the current password (same route as Ajustes).
  const changePassword = passwordForm.handleSubmit(values => {
    if (modal !== 'password' || !operation.isCurrent() || operation.blocked()) return;
    return operation.execute({ path: '/users/me/password', method: 'PUT', body: { currentPassword: values.currentPassword, newPassword: values.newPassword },
      accept: data => isRecord(data) && data.success === true,
      onSuccess: async () => { const endedVersion = await endSession(); if (getSession().version === endedVersion && getSession().token === null) showAlert('Senha alterada', 'Entre novamente com a nova senha.'); } });
  });
  const submit = (action: () => Promise<void>) => { if (operation.isCurrent() && !operation.blocked()) return action(); };
  const passwordField = (field: keyof PasswordFields, label: string, placeholder: string) => <Controller key={field} control={passwordForm.control} name={field}
    rules={{ required: 'Campo obrigatório.', validate: value => {
      if (field === 'currentPassword') return true;
      if (field === 'confirmPassword') return value === passwordForm.getValues('newPassword') || 'A confirmação não coincide com a nova senha.';
      if (value === passwordForm.getValues('currentPassword')) return 'A nova senha deve ser diferente da senha atual.';
      return PASSWORD_REGEX.test(value) || 'Use pelo menos 6 caracteres, com maiúscula, minúscula e número.';
    } }}
    render={({ field: controlled, fieldState }) => <PasswordInput label={label} placeholder={placeholder} required value={controlled.value} error={fieldState.error?.message}
      editable={!disabled} visible={showPasswords} onToggleVisibility={() => setShowPasswords(!showPasswords)} onBlur={controlled.onBlur}
      onChangeText={value => { if (operation.isCurrent() && !operation.blocked()) controlled.onChange(value); }} />}
  />;
  const logout = () => operation.confirm('Encerrar sessão', 'Deseja encerrar a sessão administrativa?', () => { void endSession(); }, 'Sair', true);
  const reasonField = (form: typeof restartForm) => <Controller control={form.control} name="reason" rules={{ maxLength: { value: 500, message: 'Use até 500 caracteres no motivo.' } }}
    render={({ field, fieldState }) => <FormField label="Motivo (opcional)" placeholder="Descreva o motivo da ação" value={field.value} error={fieldState.error?.message} maxLength={500} multiline
      editable={!disabled} onBlur={field.onBlur} onChangeText={value => { if (operation.isCurrent() && !operation.blocked()) field.onChange(value); }} />}
  />;
  if (!isAdmin) return <View style={styles.container}><AdminHeader title="Controles" subtitle="Área administrativa" /><AdminEmpty text="Acesso restrito a administradores." /></View>;
  return <View style={styles.container}>
    <AdminHeader title="Controles" subtitle="Manutenção do servidor e ações críticas" />
    <ScrollView contentContainerStyle={styles.content}>
      <AdminCard><View style={styles.row}><View style={styles.avatar}><Ionicons name="shield-checkmark-outline" size={26} color={colors.isDark ? '#ADB5EF' : colors.primary} /></View>
        <View style={styles.flex}><Text style={styles.cardTitle}>{name || 'Administrador'}</Text><Text style={styles.caption}>{role === 'SYSADMIN' ? 'Superadministrador' : 'Administrador'}</Text></View></View>
      </AdminCard>
      <AdminError message={operation.error} />
      <AdminCard><Text style={styles.cardTitle}>Reiniciar subsistema</Text><Text style={styles.caption}>Escolha uma ação e confira seus efeitos antes de confirmar.</Text>
        <Text style={styles.caption}>Subsistema *</Text>
        {SUBSYSTEMS.map(item => <TouchableOpacity key={item.key} accessibilityRole="radio" accessibilityLabel={`Selecionar ${item.label}`} accessibilityState={{ selected: subsystem === item.key, disabled }} disabled={disabled}
          onPress={() => { if (operation.isCurrent() && !operation.blocked()) { setSubsystem(item.key); operation.clearError(); } }} style={[styles.choice, subsystem === item.key && { borderColor: colors.primary, backgroundColor: colors.inputBg }]}>
          <Ionicons name={item.icon} size={22} color={colors.isDark ? '#ADB5EF' : colors.primary} /><View style={styles.flex}><Text style={styles.choiceTitle}>{item.label}</Text><Text style={styles.caption}>{item.description}</Text></View>
          <Ionicons name={subsystem === item.key ? 'radio-button-on-outline' : 'radio-button-off-outline'} size={21} color={colors.primary} />
        </TouchableOpacity>)}
        <AdminAction label="Reiniciar subsistema" onPress={() => open('restart')} disabled={!selected || disabled} filled icon="refresh-outline" />
      </AdminCard>
      <AdminCard><View style={styles.row}><Ionicons name="warning-outline" size={24} color={colors.danger} /><Text style={[styles.cardTitle, styles.flex]}>Reset total</Text></View>
        <Text style={styles.caption}>Exclui organizações, comissões, vínculos, convites, pedidos e todas as outras contas. Somente sua conta administrativa é preservada. Todas as sessões são invalidadas.</Text>
        <Text style={styles.warning}>Esta ação é irreversível.</Text><AdminAction label="Executar Reset Total do Sistema" onPress={() => open('reset')} disabled={disabled} danger icon="trash-outline" />
      </AdminCard>
      <AdminCard><Text style={styles.cardTitle}>Senha de acesso</Text>
        <Text style={styles.caption}>Troque a senha desta conta administrativa. Depois da troca, a sessão é encerrada e é preciso entrar com a nova senha.</Text>
        <AdminAction label="Alterar senha" onPress={() => open('password')} disabled={disabled} icon="key-outline" />
      </AdminCard>
      <AdminAction label="Sair" onPress={logout} disabled={disabled} icon="log-out-outline" />
    </ScrollView>
    <AdminSheet title={modal === 'reset' ? 'Confirmar reset total' : modal === 'password' ? 'Alterar senha' : `Reiniciar ${selected?.label ?? 'subsistema'}`} visible={!!modal} onClose={close} disabled={disabled}
      footer={<View style={styles.footer}><FormButton label={modal === 'reset' ? 'Executar Reset' : modal === 'password' ? 'Alterar senha' : 'Confirmar reinício'}
        onPress={() => submit(modal === 'reset' ? reset : modal === 'password' ? changePassword : restart)} loading={operation.busy} disabled={operation.confirming}
        icon={modal === 'reset' ? 'trash-outline' : modal === 'password' ? 'shield-checkmark-outline' : 'refresh-outline'} /></View>}>
      <AdminError message={operation.error} />
      {modal === 'password' && <>{passwordField('currentPassword', 'Senha atual', 'Digite sua senha atual')}
        {passwordField('newPassword', 'Nova senha', 'Digite a nova senha')}{passwordField('confirmPassword', 'Confirmar nova senha', 'Repita a nova senha')}
        <PasswordRules password={passwordForm.watch('newPassword')} confirmation={passwordForm.watch('confirmPassword')} /></>}
      {modal === 'restart' && selected && <><Text style={styles.cardTitle}>{selected.label}</Text><Text style={styles.caption}>{selected.description}</Text>{reasonField(restartForm)}</>}
      {modal === 'reset' && <><Text style={styles.warning}>Esta ação é irreversível.</Text><Text style={styles.caption}>Todas as outras contas e os dados das organizações e comissões serão excluídos. Sua conta será preservada, mas precisará entrar novamente.</Text>
        <Text style={styles.caption}>Digite exatamente {RESET_CODE} para confirmar. Digitar o texto não executa a ação; use o botão abaixo.</Text>
        <Controller control={resetForm.control} name="confirmation" rules={{ required: 'Informe o texto de confirmação.', validate: value => value === RESET_CODE || `Digite exatamente ${RESET_CODE}.` }}
          render={({ field, fieldState }) => <FormField label="Confirmação" required placeholder="Digite CONFIRM_FULL_RESET" value={field.value} error={fieldState.error?.message} autoCapitalize="none" autoCorrect={false} maxLength={40} editable={!disabled}
            onBlur={field.onBlur} onChangeText={value => { if (operation.isCurrent() && !operation.blocked()) field.onChange(value); }} />}
        />{reasonField(resetForm)}</>}
    </AdminSheet>
  </View>;
}
function createStyles(colors: ThemeColors) { return StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background }, content: { paddingHorizontal: 20, paddingBottom: 24 }, flex: { flex: 1, minWidth: 0 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 8 }, avatar: { width: 48, height: 48, borderRadius: 24, backgroundColor: colors.inputBg, alignItems: 'center', justifyContent: 'center' },
  cardTitle: { color: colors.text, fontSize: 16, lineHeight: 23, fontWeight: '700', marginBottom: 4 }, caption: { color: colors.subtext, fontSize: 13, lineHeight: 20, marginVertical: 6 },
  choice: { minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: 10, borderWidth: 1, borderColor: colors.border, borderRadius: 12, padding: 12, marginTop: 12 },
  choiceTitle: { color: colors.text, fontSize: 14, lineHeight: 21, fontWeight: '600' }, warning: { color: colors.danger, fontSize: 13, lineHeight: 20, fontWeight: '600', marginVertical: 10 },
  footer: { paddingHorizontal: 20, paddingVertical: 12, borderTopWidth: 1, borderColor: colors.border, backgroundColor: colors.background },
}); }
