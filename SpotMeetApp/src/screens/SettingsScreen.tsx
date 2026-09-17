import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, TextInput,
  ScrollView, ActivityIndicator, Alert, Platform, Image, Modal,
  KeyboardAvoidingView,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect } from '@react-navigation/native';
import { useAuth } from '../context/AuthContext';
import { useTheme, ThemeType } from '../context/ThemeContext';
import { API_BASE_URL } from '../config/api';
import { showAlert } from '../utils/alerts';
import { useLiveSync } from '../hooks/useLiveSync';
import { notifySync } from '../utils/syncBus';
import { checkPasswordRules } from '../utils/validators';

type PresenceStatus = 'PRESENT' | 'AWAY' | 'BUSY';

// Portuguese labels for user roles returned by the API
const ROLE_LABELS: Record<string, string> = {
  SYSADMIN: 'SysAdmin',
  ADMIN: 'Admin',
  USER: 'Membro',
};

export default function SettingsScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const { token, signOut, updateSession, role, userId } = useAuth();
  const { theme, setTheme, colors } = useTheme();

  // Profile form state
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [bio, setBio] = useState('');
  const [status, setStatus] = useState<PresenceStatus>('PRESENT');

  // Reference to the theme saved in the database (avoids unwanted autosave and allows revert when leaving the tab)
  const savedThemeRef = useRef<ThemeType | null>('DARK');

  // Control state
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [saving, setSaving] = useState(false);

  // Password change form state
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);

  // Secure e-mail change modal state (two-step LGPD flow)
  const [emailModalVisible, setEmailModalVisible] = useState(false);
  const [emailStep, setEmailStep] = useState<1 | 2>(1);
  const [currentCode, setCurrentCode] = useState('');
  const [newEmail, setNewEmail] = useState('');
  const [newEmailCode, setNewEmailCode] = useState('');
  const [loadingModal, setLoadingModal] = useState(false);
  const [modalDevCode, setModalDevCode] = useState('');
  const [changeCooldown, setChangeCooldown] = useState(0);

  // Loads the real profile data of the authenticated user
  const loadProfile = useCallback(async () => {
    if (!token) {
      setLoadingProfile(false);
      return;
    }

    try {
      const res = await fetch(`${API_BASE_URL}/users/me`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (res.ok) {
        const data = await res.json();
        setName(data.name || '');
        setEmail(data.email || '');
        setBio(data.bio || '');

        if (data.availabilityStatus === 'AWAY' || data.availabilityStatus === 'BUSY') {
          setStatus(data.availabilityStatus);
        } else {
          setStatus('PRESENT');
        }

        if (data.preferredTheme === 'LIGHT' || data.preferredTheme === 'DARK') {
          savedThemeRef.current = data.preferredTheme;
          await setTheme(data.preferredTheme, false);
        }
      }
    } catch (err) {
      console.warn('Erro ao carregar dados do perfil:', err);
    } finally {
      setLoadingProfile(false);
    }
  }, [token, setTheme]);

  useFocusEffect(
    useCallback(() => {
      loadProfile();

      return () => {
        // If the user switched the theme but did not press "Salvar Alterações",
        // leaving this tab reverts the theme to the preference saved in the database
        if (savedThemeRef.current) {
          setTheme(savedThemeRef.current, false);
        }
      };
    }, [loadProfile, setTheme])
  );

  // Real-time profile synchronization
  useLiveSync(
    useCallback(() => {
      loadProfile();
    }, [loadProfile]),
    {
      intervalMs: 8000,
      eventTypes: ['PROFILE_MUTATED', 'VISIBILITY_RESTORED'],
    }
  );

  // Saves name, bio, status and theme changes
  const handleSaveProfile = async () => {
    if (!name.trim()) {
      showAlert('Atenção', 'O nome completo não pode ficar em branco.');
      return;
    }

    if (name.trim().length < 2) {
      showAlert('Atenção', 'O nome deve ter pelo menos 2 caracteres.');
      return;
    }

    setSaving(true);
    try {
      const res = await fetch(`${API_BASE_URL}/users/me`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          name: name.trim(),
          bio: bio.trim(),
          availabilityStatus: status,
          preferredTheme: theme,
        }),
      });

      if (!res.ok) {
        const errorMsg = await res.text();
        throw new Error(errorMsg || 'Falha ao atualizar perfil.');
      }

      const updated = await res.json();
      setName(updated.name);
      setBio(updated.bio || '');
      setStatus(updated.availabilityStatus as PresenceStatus);
      if (updated.preferredTheme) {
        savedThemeRef.current = updated.preferredTheme;
        await setTheme(updated.preferredTheme, false);
      }

      // Updates the name in the global auth context
      if (token) {
        updateSession({ name: updated.name, email, userId: updated.id });
      }

      notifySync('PROFILE_MUTATED', { userId });
      showAlert('Sucesso', 'Perfil e preferências salvos com sucesso!');
    } catch (err: any) {
      showAlert('Erro', err.message || 'Não foi possível salvar as alterações.');
    } finally {
      setSaving(false);
    }
  };

  const handleChangePassword = async () => {
    if (!currentPassword) {
      showAlert('Atenção', 'Informe a sua senha atual.');
      return;
    }

    const rules = checkPasswordRules(newPassword);
    if (!rules.valid) {
      showAlert('Atenção', 'A nova senha deve ter no mínimo 6 caracteres, com pelo menos uma letra maiúscula, uma minúscula e um número.');
      return;
    }

    if (newPassword !== confirmPassword) {
      showAlert('Atenção', 'A confirmação não confere com a nova senha.');
      return;
    }

    if (newPassword === currentPassword) {
      showAlert('Atenção', 'A nova senha deve ser diferente da senha atual.');
      return;
    }

    setChangingPassword(true);
    try {
      const res = await fetch(`${API_BASE_URL}/users/me/password`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ currentPassword, newPassword }),
      });

      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.message || 'Não foi possível alterar a senha.');
      }

      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      showAlert('Sucesso', data.message || 'Senha alterada com sucesso!');
    } catch (err: any) {
      showAlert('Erro', err.message || 'Não foi possível alterar a senha.');
    } finally {
      setChangingPassword(false);
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
      if (window.confirm('Deseja realmente sair da sua conta?')) {
        execute();
      }
    } else {
      Alert.alert('Sair da Conta', 'Deseja realmente desconectar do SpotMeet?', [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Sair', onPress: execute, style: 'destructive' },
      ]);
    }
  };

  // Resend timer inside the modal
  useEffect(() => {
    let timer: any;
    if (changeCooldown > 0) {
      timer = setInterval(() => {
        setChangeCooldown((prev) => prev - 1);
      }, 1000);
    }
    return () => {
      if (timer) clearInterval(timer);
    };
  }, [changeCooldown]);

  // Opens the modal and sends the code to the current e-mail
  const openEmailChangeModal = async () => {
    setEmailModalVisible(true);
    setEmailStep(1);
    setCurrentCode('');
    setNewEmail('');
    setNewEmailCode('');
    setModalDevCode('');
    setLoadingModal(true);

    try {
      const res = await fetch(`${API_BASE_URL}/users/me/email-change/request`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Falha ao solicitar código para o e-mail atual.');
      }

      setChangeCooldown(60);
      if (data.devCode) {
        setModalDevCode(data.devCode);
        setCurrentCode(data.devCode);
      }
    } catch (err: any) {
      showAlert('Erro', err.message || 'Não foi possível solicitar código de segurança.');
    } finally {
      setLoadingModal(false);
    }
  };

  // Resends the step 1 code (current e-mail)
  const handleResendCurrentCode = async () => {
    setLoadingModal(true);
    try {
      const res = await fetch(`${API_BASE_URL}/users/me/email-change/request`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Não foi possível reenviar o código.');
      }
      setChangeCooldown(60);
      if (data.devCode) {
        setModalDevCode(data.devCode);
        setCurrentCode(data.devCode);
      }
      showAlert('Código Reenviado', data.message || 'Código de segurança reenviado com sucesso para o seu e-mail atual.');
    } catch (err: any) {
      showAlert('Erro', err.message || 'Falha ao reenviar código.');
    } finally {
      setLoadingModal(false);
    }
  };

  // Validates step 1 and sends the code to the new e-mail (start of step 2)
  const handleAdvanceToStep2 = async () => {
    if (!currentCode.trim() || currentCode.trim().length !== 6) {
      showAlert('Código Inválido', 'Digite o código de 6 dígitos recebido no seu e-mail atual.');
      return;
    }

    const cleanEmail = newEmail.trim().toLowerCase();
    if (!cleanEmail || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) {
      showAlert('Novo E-mail Inválido', 'Por favor, informe um endereço de novo e-mail válido.');
      return;
    }

    if (cleanEmail === email.trim().toLowerCase()) {
      showAlert('Atenção', 'O novo e-mail informado deve ser diferente do e-mail atual.');
      return;
    }

    setLoadingModal(true);
    try {
      const res = await fetch(`${API_BASE_URL}/users/me/email-change/confirm`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          currentCode: currentCode.trim(),
          newEmail: cleanEmail,
        }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Código do e-mail atual inválido ou expirado.');
      }

      setEmailStep(2);
      setChangeCooldown(60);
      setModalDevCode(data.devCode || '');
      if (data.devCode) {
        setNewEmailCode(data.devCode);
      }

      showAlert('Etapa 1 Concluída', data.message || 'Código de validação enviado para o novo e-mail!');
    } catch (err: any) {
      showAlert('Erro na Validação', err.message || 'Não foi possível confirmar o código do e-mail atual.');
    } finally {
      setLoadingModal(false);
    }
  };

  // Resends the step 2 code (new e-mail)
  const handleResendNewEmailCode = async () => {
    setLoadingModal(true);
    try {
      const res = await fetch(`${API_BASE_URL}/users/me/email-change/confirm`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          currentCode: currentCode.trim(),
          newEmail: newEmail.trim().toLowerCase(),
        }),
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Falha ao reenviar código para o novo e-mail.');
      }
      setChangeCooldown(60);
      if (data.devCode) {
        setModalDevCode(data.devCode);
        setNewEmailCode(data.devCode);
      }
      showAlert('Código Reenviado', data.message || 'Novo código de validação enviado para o novo e-mail.');
    } catch (err: any) {
      showAlert('Erro', err.message || 'Não foi possível reenviar o código.');
    } finally {
      setLoadingModal(false);
    }
  };

  // Completes step 2, updates the database, refreshes the JWT and closes the modal
  const handleCompleteEmailChange = async () => {
    if (!newEmailCode.trim() || newEmailCode.trim().length !== 6) {
      showAlert('Código Inválido', 'Digite o código de 6 dígitos recebido no seu novo e-mail.');
      return;
    }

    setLoadingModal(true);
    try {
      const res = await fetch(`${API_BASE_URL}/users/me/email-change/complete`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          newEmailCode: newEmailCode.trim(),
        }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Código de validação incorreto ou expirado.');
      }

      const updatedEmail = data.newEmail || newEmail.trim().toLowerCase();
      setEmail(updatedEmail);

      if (data.token) {
        updateSession({ token: data.token, name, email: updatedEmail, userId });
      }

      setEmailModalVisible(false);
      showAlert('Sucesso!', 'E-mail atualizado com sucesso em conformidade com as regras de segurança e LGPD.');
    } catch (err: any) {
      showAlert('Erro na Conclusão', err.message || 'Não foi possível concluir a alteração de e-mail.');
    } finally {
      setLoadingModal(false);
    }
  };

  const themeColors = colors;

  // Color of the status dot
  const getStatusColor = (st: PresenceStatus) => {
    switch (st) {
      case 'PRESENT':
        return '#4CAF50'; // Green
      case 'AWAY':
        return '#FFB300'; // Yellow
      case 'BUSY':
        return '#F44336'; // Red
    }
  };

  const nameInitial = name ? name.charAt(0).toUpperCase() : 'U';
  const isAdminRole = role === 'SYSADMIN' || role === 'ADMIN';

  return (
    <KeyboardAvoidingView behavior="padding" style={[styles.container, { backgroundColor: themeColors.background }]}>
      {/* Header with brand icon and avatar with presence */}
      <View style={[styles.header, { borderBottomColor: themeColors.border, marginTop: insets.top }]}>
        <View style={styles.headerLeft}>
          <Image
            source={require('../../assets/icone.jpeg')}
            style={styles.brandIcon}
            resizeMode="cover"
          />
          <View>
            <Text style={[styles.headerTitle, { color: themeColors.text }]}>Perfil & Ajustes</Text>
            <Text style={[styles.headerSubtitle, { color: themeColors.subtext }]}>
              {isAdminRole ? 'Painel Administrativo' : 'Membro da Equipe'}
            </Text>
          </View>
        </View>
        <View style={styles.avatarHeader}>
          <Text style={styles.avatarHeaderText}>{nameInitial}</Text>
          <View style={[styles.statusIndicatorSmall, { backgroundColor: getStatusColor(status) }]} />
        </View>
      </View>

      {loadingProfile ? (
        <View style={styles.centerLoading}>
          <ActivityIndicator size="large" color="#5C6BC0" />
          <Text style={[styles.loadingText, { color: themeColors.subtext }]}>Carregando perfil...</Text>
        </View>
      ) : (
        <ScrollView
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{ paddingBottom: 40 }}
        >
          {/* Card: basic data and bio */}
          <View style={[styles.card, { backgroundColor: themeColors.card, borderColor: themeColors.border }]}>
            <View style={styles.profileHeroRow}>
              <View style={[styles.avatarHero, { backgroundColor: themeColors.primary }]}>
                <Text style={styles.avatarHeroText}>{nameInitial}</Text>
                <View style={[styles.statusIndicatorHero, { backgroundColor: getStatusColor(status) }]} />
              </View>
              <View style={styles.profileHeroInfo}>
                <Text style={[styles.heroName, { color: themeColors.text }]}>{name || 'Sem nome'}</Text>
                <Text style={[styles.heroEmail, { color: themeColors.subtext }]}>{email}</Text>
                <View style={styles.roleBadge}>
                  <Text style={styles.roleBadgeText}>
                    {ROLE_LABELS[role || 'USER'] || ROLE_LABELS.USER}
                  </Text>
                </View>
              </View>
            </View>

            <Text style={[styles.label, { color: themeColors.text }]}>Nome Completo</Text>
            <TextInput
              style={[styles.input, { backgroundColor: themeColors.inputBg, color: themeColors.text, borderColor: themeColors.border }]}
              placeholder="Digite seu nome completo"
              placeholderTextColor={themeColors.subtext}
              value={name}
              onChangeText={setName}
            />

            {/* Read-only e-mail with change button */}
            <View style={styles.emailContainer}>
              <Text style={[styles.label, { color: themeColors.text }]}>E-mail</Text>

              <View style={[styles.disabledInput, { backgroundColor: themeColors.inputBg, borderColor: themeColors.border }]}>
                <Ionicons name="mail" size={16} color={themeColors.subtext} style={{ marginRight: 8 }} />
                <Text style={[styles.disabledInputText, { color: themeColors.subtext, flex: 1 }]}>{email}</Text>
                <Ionicons name="lock-closed" size={16} color="#FF9800" />
              </View>

              <TouchableOpacity
                style={styles.btnChangeEmail}
                onPress={openEmailChangeModal}
              >
                <Ionicons name="create-outline" size={16} color="#5C6BC0" style={{ marginRight: 6 }} />
                <Text style={styles.btnChangeEmailText}>Solicitar Alteração Segura de E-mail</Text>
              </TouchableOpacity>
            </View>

            <View style={styles.labelRow}>
              <Text style={[styles.label, { color: themeColors.text }]}>Biografia Pessoal</Text>
              <Text style={[styles.charCounter, { color: themeColors.subtext }]}>
                {bio.length} / 500
              </Text>
            </View>
            <TextInput
              style={[styles.textArea, { backgroundColor: themeColors.inputBg, color: themeColors.text, borderColor: themeColors.border }]}
              placeholder="Conte um pouco sobre suas atribuições, equipe ou apresentação pessoal..."
              placeholderTextColor={themeColors.subtext}
              multiline
              numberOfLines={4}
              maxLength={500}
              value={bio}
              onChangeText={setBio}
            />
          </View>

          {/* Card: security (password change) */}
          <View style={[styles.card, { backgroundColor: themeColors.card, borderColor: themeColors.border }]}>
            <Text style={[styles.cardSectionTitle, { color: themeColors.text }]}>Segurança</Text>
            <Text style={[styles.cardSectionDesc, { color: themeColors.subtext }]}>
              Informe a senha atual e escolha uma nova senha com no mínimo 6 caracteres, contendo letra maiúscula, minúscula e número.
            </Text>

            <Text style={[styles.label, { color: themeColors.text }]}>Senha Atual</Text>
            <View style={[styles.passwordWrapper, { backgroundColor: themeColors.inputBg, borderColor: themeColors.border }]}>
              <TextInput
                style={[styles.passwordInput, { color: themeColors.text }]}
                placeholder="Digite sua senha atual"
                placeholderTextColor={themeColors.subtext}
                value={currentPassword}
                onChangeText={setCurrentPassword}
                secureTextEntry={!showCurrentPassword}
                autoCapitalize="none"
              />
              <TouchableOpacity onPress={() => setShowCurrentPassword(!showCurrentPassword)} style={styles.eyeBtn}>
                <Ionicons name={showCurrentPassword ? 'eye-off' : 'eye'} size={20} color={themeColors.subtext} />
              </TouchableOpacity>
            </View>

            <Text style={[styles.label, { color: themeColors.text }]}>Nova Senha</Text>
            <View style={[styles.passwordWrapper, { backgroundColor: themeColors.inputBg, borderColor: themeColors.border }]}>
              <TextInput
                style={[styles.passwordInput, { color: themeColors.text }]}
                placeholder="Digite a nova senha"
                placeholderTextColor={themeColors.subtext}
                value={newPassword}
                onChangeText={setNewPassword}
                secureTextEntry={!showNewPassword}
                autoCapitalize="none"
              />
              <TouchableOpacity onPress={() => setShowNewPassword(!showNewPassword)} style={styles.eyeBtn}>
                <Ionicons name={showNewPassword ? 'eye-off' : 'eye'} size={20} color={themeColors.subtext} />
              </TouchableOpacity>
            </View>

            <Text style={[styles.label, { color: themeColors.text }]}>Confirmar Nova Senha</Text>
            <View style={[styles.passwordWrapper, { backgroundColor: themeColors.inputBg, borderColor: themeColors.border }]}>
              <TextInput
                style={[styles.passwordInput, { color: themeColors.text }]}
                placeholder="Repita a nova senha"
                placeholderTextColor={themeColors.subtext}
                value={confirmPassword}
                onChangeText={setConfirmPassword}
                secureTextEntry={!showConfirmPassword}
                autoCapitalize="none"
              />
              <TouchableOpacity onPress={() => setShowConfirmPassword(!showConfirmPassword)} style={styles.eyeBtn}>
                <Ionicons name={showConfirmPassword ? 'eye-off' : 'eye'} size={20} color={themeColors.subtext} />
              </TouchableOpacity>
            </View>

            <TouchableOpacity
              style={[styles.saveButton, changingPassword && styles.buttonDisabled]}
              onPress={handleChangePassword}
              disabled={changingPassword}
            >
              {changingPassword ? (
                <ActivityIndicator color="white" />
              ) : (
                <>
                  <Ionicons name="key-outline" size={20} color="white" style={{ marginRight: 8 }} />
                  <Text style={styles.saveButtonText}>Alterar Senha</Text>
                </>
              )}
            </TouchableOpacity>
          </View>

          {/* Card: visual presence status (dots) */}
          <View style={[styles.card, { backgroundColor: themeColors.card, borderColor: themeColors.border }]}>
            <Text style={[styles.cardSectionTitle, { color: themeColors.text }]}>Status de Presença</Text>
            <Text style={[styles.cardSectionDesc, { color: themeColors.subtext }]}>
              Selecione como os outros membros, líderes e comissões verão você no sistema:
            </Text>

            {/* Option: PRESENT */}
            <TouchableOpacity
              style={[
                styles.statusOption,
                status === 'PRESENT' && styles.statusOptionActive,
                { backgroundColor: themeColors.inputBg, borderColor: status === 'PRESENT' ? '#4CAF50' : themeColors.border },
              ]}
              onPress={() => setStatus('PRESENT')}
            >
              <View style={styles.statusOptionLeft}>
                <View style={[styles.statusDotVisual, { backgroundColor: '#4CAF50' }]} />
                <View>
                  <Text style={[styles.statusOptionTitle, { color: themeColors.text }]}>Presente (Disponível)</Text>
                  <Text style={[styles.statusOptionSubtitle, { color: themeColors.subtext }]}>
                    Disponível para reuniões, chamadas e atividades
                  </Text>
                </View>
              </View>
              <Ionicons
                name={status === 'PRESENT' ? 'radio-button-on' : 'radio-button-off'}
                size={22}
                color={status === 'PRESENT' ? '#4CAF50' : themeColors.subtext}
              />
            </TouchableOpacity>

            {/* Option: AWAY */}
            <TouchableOpacity
              style={[
                styles.statusOption,
                status === 'AWAY' && styles.statusOptionActive,
                { backgroundColor: themeColors.inputBg, borderColor: status === 'AWAY' ? '#FFB300' : themeColors.border },
              ]}
              onPress={() => setStatus('AWAY')}
            >
              <View style={styles.statusOptionLeft}>
                <View style={[styles.statusDotVisual, { backgroundColor: '#FFB300' }]} />
                <View>
                  <Text style={[styles.statusOptionTitle, { color: themeColors.text }]}>Ausente</Text>
                  <Text style={[styles.statusOptionSubtitle, { color: themeColors.subtext }]}>
                    Afastado temporariamente ou fora do posto
                  </Text>
                </View>
              </View>
              <Ionicons
                name={status === 'AWAY' ? 'radio-button-on' : 'radio-button-off'}
                size={22}
                color={status === 'AWAY' ? '#FFB300' : themeColors.subtext}
              />
            </TouchableOpacity>

            {/* Option: BUSY */}
            <TouchableOpacity
              style={[
                styles.statusOption,
                status === 'BUSY' && styles.statusOptionActive,
                { backgroundColor: themeColors.inputBg, borderColor: status === 'BUSY' ? '#F44336' : themeColors.border },
              ]}
              onPress={() => setStatus('BUSY')}
            >
              <View style={styles.statusOptionLeft}>
                <View style={[styles.statusDotVisual, { backgroundColor: '#F44336' }]} />
                <View>
                  <Text style={[styles.statusOptionTitle, { color: themeColors.text }]}>Ocupado</Text>
                  <Text style={[styles.statusOptionSubtitle, { color: themeColors.subtext }]}>
                    Em chamada, reunião de comissão ou foco restrito
                  </Text>
                </View>
              </View>
              <Ionicons
                name={status === 'BUSY' ? 'radio-button-on' : 'radio-button-off'}
                size={22}
                color={status === 'BUSY' ? '#F44336' : themeColors.subtext}
              />
            </TouchableOpacity>
          </View>

          {/* Card: preferred app theme */}
          <View style={[styles.card, { backgroundColor: themeColors.card, borderColor: themeColors.border }]}>
            <Text style={[styles.cardSectionTitle, { color: themeColors.text }]}>Tema da Aplicação</Text>
            <Text style={[styles.cardSectionDesc, { color: themeColors.subtext }]}>
              Selecione para testar em tempo real. Apenas será salvo definitivamente ao clicar em "Salvar Alterações":
            </Text>

            <View style={styles.themeRow}>
              {/* Dark mode */}
              <TouchableOpacity
                style={[
                  styles.themeButton,
                  theme === 'DARK' && styles.themeButtonActive,
                  { backgroundColor: themeColors.inputBg, borderColor: theme === 'DARK' ? themeColors.primary : 'transparent' },
                ]}
                onPress={() => setTheme('DARK', false)}
              >
                <Ionicons name="moon" size={24} color={theme === 'DARK' ? '#7986CB' : '#888'} />
                <Text style={[styles.themeButtonText, { color: themeColors.text }]}>Modo Escuro</Text>
                {theme === 'DARK' && (
                  <Ionicons name="checkmark-circle" size={18} color={themeColors.primary} style={{ marginTop: 4 }} />
                )}
              </TouchableOpacity>

              {/* Light mode */}
              <TouchableOpacity
                style={[
                  styles.themeButton,
                  theme === 'LIGHT' && styles.themeButtonActive,
                  { backgroundColor: themeColors.inputBg, borderColor: theme === 'LIGHT' ? themeColors.primary : 'transparent' },
                ]}
                onPress={() => setTheme('LIGHT', false)}
              >
                <Ionicons name="sunny" size={24} color={theme === 'LIGHT' ? '#FFA726' : '#888'} />
                <Text style={[styles.themeButtonText, { color: themeColors.text }]}>Modo Claro</Text>
                {theme === 'LIGHT' && (
                  <Ionicons name="checkmark-circle" size={18} color={themeColors.primary} style={{ marginTop: 4 }} />
                )}
              </TouchableOpacity>
            </View>
          </View>

          {/* Save changes button */}
          <TouchableOpacity
            style={[styles.saveButton, saving && styles.buttonDisabled]}
            onPress={handleSaveProfile}
            disabled={saving}
          >
            {saving ? (
              <ActivityIndicator color="white" />
            ) : (
              <>
                <Ionicons name="checkmark-done" size={20} color="white" style={{ marginRight: 8 }} />
                <Text style={styles.saveButtonText}>Salvar Alterações</Text>
              </>
            )}
          </TouchableOpacity>

          {/* Logout button */}
          <TouchableOpacity style={styles.logoutButton} onPress={handleLogout}>
            <Ionicons name="log-out-outline" size={20} color="#FF6B6B" style={{ marginRight: 8 }} />
            <Text style={styles.logoutButtonText}>Sair da Conta (Logout)</Text>
          </TouchableOpacity>
        </ScrollView>
      )}

      {/* Secure e-mail change modal (two-step LGPD flow) */}
      <Modal
        visible={emailModalVisible}
        transparent
        animationType="fade"
        onRequestClose={() => !loadingModal && setEmailModalVisible(false)}
      >
        <KeyboardAvoidingView behavior="padding" style={styles.modalOverlay}>
          <View style={[styles.modalCard, { backgroundColor: themeColors.card, borderColor: themeColors.border }]}>
            {/* Modal header */}
            <View style={styles.modalHeader}>
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <View style={styles.modalIconBadge}>
                  <Ionicons name="shield-checkmark" size={20} color="#5C6BC0" />
                </View>
                <View>
                  <Text style={[styles.modalTitle, { color: themeColors.text }]}>Alterar E-mail</Text>
                  <Text style={[styles.modalSubtitle, { color: themeColors.subtext }]}>
                    Segurança e Conformidade LGPD
                  </Text>
                </View>
              </View>
              <TouchableOpacity
                onPress={() => !loadingModal && setEmailModalVisible(false)}
                disabled={loadingModal}
                style={styles.modalCloseBtn}
              >
                <Ionicons name="close" size={22} color={themeColors.subtext} />
              </TouchableOpacity>
            </View>

            {/* Step indicator */}
            <View style={styles.stepIndicatorContainer}>
              <View style={[styles.stepItem, emailStep === 1 && styles.stepItemActive]}>
                <View style={[styles.stepCircle, emailStep === 1 ? styles.stepCircleActive : styles.stepCircleDone]}>
                  {emailStep === 2 ? (
                    <Ionicons name="checkmark" size={14} color="#FFF" />
                  ) : (
                    <Text style={styles.stepCircleText}>1</Text>
                  )}
                </View>
                <Text style={[styles.stepLabel, emailStep === 1 && { color: '#5C6BC0', fontWeight: 'bold' }]}>
                  E-mail Atual
                </Text>
              </View>

              <View style={[styles.stepLine, emailStep === 2 && styles.stepLineActive]} />

              <View style={[styles.stepItem, emailStep === 2 && styles.stepItemActive]}>
                <View style={[styles.stepCircle, emailStep === 2 ? styles.stepCircleActive : styles.stepCircleInactive]}>
                  <Text style={[styles.stepCircleText, emailStep !== 2 && { color: themeColors.subtext }]}>2</Text>
                </View>
                <Text style={[styles.stepLabel, emailStep === 2 && { color: '#5C6BC0', fontWeight: 'bold' }]}>
                  Novo E-mail
                </Text>
              </View>
            </View>

            {/* Dev test info banner */}
            {modalDevCode ? (
              <View style={styles.modalDevBanner}>
                <Ionicons name="information-circle" size={18} color="#FF9800" style={{ marginRight: 6 }} />
                <Text style={styles.modalDevBannerText}>
                  Código OTP de Teste (Dev): <Text style={{ fontWeight: 'bold' }}>{modalDevCode}</Text>
                </Text>
              </View>
            ) : null}

            {/* STEP 1 CONTENT */}
            {emailStep === 1 && (
              <View style={styles.modalBody}>
                <Text style={[styles.modalStepDesc, { color: themeColors.subtext }]}>
                  Um código de autorização de 6 dígitos foi enviado para o seu e-mail atual cadastrado:
                </Text>
                <View style={styles.currentEmailHighlight}>
                  <Ionicons name="mail-outline" size={16} color="#5C6BC0" style={{ marginRight: 6 }} />
                  <Text style={styles.currentEmailHighlightText}>{email}</Text>
                </View>

                <Text style={[styles.label, { color: themeColors.text, marginTop: 12 }]}>
                  Código de Confirmação (6 dígitos)
                </Text>
                <TextInput
                  style={[styles.input, { backgroundColor: themeColors.inputBg, color: themeColors.text, borderColor: themeColors.border, letterSpacing: 4, textAlign: 'center', fontSize: 20, fontWeight: 'bold' }]}
                  placeholder="000000"
                  placeholderTextColor={themeColors.subtext}
                  keyboardType="numeric"
                  maxLength={6}
                  value={currentCode}
                  onChangeText={setCurrentCode}
                />

                <Text style={[styles.label, { color: themeColors.text, marginTop: 12 }]}>
                  Novo E-mail Desejado
                </Text>
                <TextInput
                  style={[styles.input, { backgroundColor: themeColors.inputBg, color: themeColors.text, borderColor: themeColors.border }]}
                  placeholder="novo.email@exemplo.com"
                  placeholderTextColor={themeColors.subtext}
                  keyboardType="email-address"
                  autoCapitalize="none"
                  value={newEmail}
                  onChangeText={setNewEmail}
                />

                {/* Resend code to current e-mail */}
                <TouchableOpacity
                  onPress={handleResendCurrentCode}
                  disabled={changeCooldown > 0 || loadingModal}
                  style={styles.modalResendBtn}
                >
                  <Text style={[styles.modalResendText, { color: changeCooldown > 0 ? themeColors.subtext : '#5C6BC0' }]}>
                    {changeCooldown > 0 ? `Reenviar código em ${changeCooldown}s` : 'Reenviar código para e-mail atual'}
                  </Text>
                </TouchableOpacity>

                {/* Advance to step 2 button */}
                <TouchableOpacity
                  style={[styles.modalPrimaryBtn, loadingModal && styles.btnDisabled]}
                  onPress={handleAdvanceToStep2}
                  disabled={loadingModal}
                >
                  {loadingModal ? (
                    <ActivityIndicator color="#FFF" />
                  ) : (
                    <>
                      <Text style={styles.modalPrimaryBtnText}>Avançar para Etapa 2</Text>
                      <Ionicons name="arrow-forward" size={18} color="#FFF" style={{ marginLeft: 6 }} />
                    </>
                  )}
                </TouchableOpacity>
              </View>
            )}

            {/* STEP 2 CONTENT */}
            {emailStep === 2 && (
              <View style={styles.modalBody}>
                <Text style={[styles.modalStepDesc, { color: themeColors.subtext }]}>
                  O código do e-mail atual foi validado! Enviamos agora um novo código de 6 dígitos para o seu novo endereço:
                </Text>
                <View style={styles.currentEmailHighlight}>
                  <Ionicons name="mail-open-outline" size={16} color="#4CAF50" style={{ marginRight: 6 }} />
                  <Text style={[styles.currentEmailHighlightText, { color: '#4CAF50' }]}>{newEmail}</Text>
                </View>

                <Text style={[styles.label, { color: themeColors.text, marginTop: 14 }]}>
                  Código Recebido no Novo E-mail
                </Text>
                <TextInput
                  style={[styles.input, { backgroundColor: themeColors.inputBg, color: themeColors.text, borderColor: themeColors.border, letterSpacing: 4, textAlign: 'center', fontSize: 20, fontWeight: 'bold' }]}
                  placeholder="000000"
                  placeholderTextColor={themeColors.subtext}
                  keyboardType="numeric"
                  maxLength={6}
                  value={newEmailCode}
                  onChangeText={setNewEmailCode}
                />

                {/* Resend code to the new e-mail */}
                <TouchableOpacity
                  onPress={handleResendNewEmailCode}
                  disabled={changeCooldown > 0 || loadingModal}
                  style={styles.modalResendBtn}
                >
                  <Text style={[styles.modalResendText, { color: changeCooldown > 0 ? themeColors.subtext : '#5C6BC0' }]}>
                    {changeCooldown > 0 ? `Reenviar código em ${changeCooldown}s` : 'Reenviar código para novo e-mail'}
                  </Text>
                </TouchableOpacity>

                {/* Step 2 action buttons */}
                <View style={{ flexDirection: 'row', gap: 10, marginTop: 14 }}>
                  <TouchableOpacity
                    style={[styles.modalSecondaryBtn, { borderColor: themeColors.border }]}
                    onPress={() => setEmailStep(1)}
                    disabled={loadingModal}
                  >
                    <Ionicons name="arrow-back" size={16} color={themeColors.subtext} style={{ marginRight: 4 }} />
                    <Text style={[styles.modalSecondaryBtnText, { color: themeColors.subtext }]}>Voltar</Text>
                  </TouchableOpacity>

                  <TouchableOpacity
                    style={[styles.modalPrimaryBtn, { flex: 1 }, loadingModal && styles.btnDisabled]}
                    onPress={handleCompleteEmailChange}
                    disabled={loadingModal}
                  >
                    {loadingModal ? (
                      <ActivityIndicator color="#FFF" />
                    ) : (
                      <>
                        <Ionicons name="checkmark-circle" size={18} color="#FFF" style={{ marginRight: 6 }} />
                        <Text style={styles.modalPrimaryBtnText}>Confirmar e Atualizar</Text>
                      </>
                    )}
                  </TouchableOpacity>
                </View>
              </View>
            )}
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 12, // real status bar height comes from the safe area inset applied to the header
    width: '100%',
    maxWidth: 700,
    alignSelf: 'center',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingBottom: 16,
    marginBottom: 16,
    borderBottomWidth: 1,
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  brandIcon: {
    width: 40,
    height: 40,
    borderRadius: 10,
    marginRight: 12,
    borderWidth: 1.5,
    borderColor: '#5C6BC0',
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: 'bold',
  },
  headerSubtitle: {
    fontSize: 12,
    marginTop: 2,
  },
  avatarHeader: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: '#5C6BC0',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  avatarHeaderText: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: 'bold',
  },
  statusIndicatorSmall: {
    width: 12,
    height: 12,
    borderRadius: 6,
    position: 'absolute',
    bottom: -1,
    right: -1,
    borderWidth: 2,
    borderColor: '#121212',
  },
  centerLoading: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 30,
  },
  loadingText: {
    marginTop: 12,
    fontSize: 14,
  },
  card: {
    borderRadius: 14,
    padding: 18,
    marginBottom: 16,
    borderWidth: 1,
  },
  profileHeroRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
    paddingBottom: 14,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(150, 150, 150, 0.15)',
  },
  avatarHero: {
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  avatarHeroText: {
    color: '#FFF',
    fontSize: 26,
    fontWeight: 'bold',
  },
  statusIndicatorHero: {
    width: 16,
    height: 16,
    borderRadius: 8,
    position: 'absolute',
    bottom: 2,
    right: 2,
    borderWidth: 2.5,
    borderColor: '#1E1E1E',
  },
  profileHeroInfo: {
    marginLeft: 16,
    flex: 1,
  },
  heroName: {
    fontSize: 17,
    fontWeight: 'bold',
  },
  heroEmail: {
    fontSize: 13,
    marginTop: 2,
  },
  roleBadge: {
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(92, 107, 192, 0.18)',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
    marginTop: 6,
  },
  roleBadgeText: {
    color: '#5C6BC0',
    fontSize: 11,
    fontWeight: 'bold',
  },
  label: {
    fontSize: 13,
    fontWeight: 'bold',
    marginBottom: 6,
    marginTop: 10,
  },
  labelRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 10,
    marginBottom: 6,
  },
  charCounter: {
    fontSize: 11,
  },
  input: {
    borderRadius: 8,
    padding: 12,
    fontSize: 14,
    borderWidth: 1,
  },
  disabledInput: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 8,
    padding: 12,
    borderWidth: 1,
  },
  disabledInputText: {
    fontSize: 14,
  },
  textArea: {
    borderRadius: 8,
    padding: 12,
    fontSize: 14,
    borderWidth: 1,
    textAlignVertical: 'top',
    minHeight: 85,
  },
  cardSectionTitle: {
    fontSize: 15,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  cardSectionDesc: {
    fontSize: 12,
    marginBottom: 14,
    lineHeight: 18,
  },
  statusOption: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 14,
    borderRadius: 10,
    marginBottom: 10,
    borderWidth: 1.5,
  },
  statusOptionActive: {
    borderWidth: 2,
  },
  statusOptionLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  statusDotVisual: {
    width: 14,
    height: 14,
    borderRadius: 7,
    marginRight: 12,
  },
  statusOptionTitle: {
    fontSize: 14,
    fontWeight: 'bold',
  },
  statusOptionSubtitle: {
    fontSize: 11,
    marginTop: 2,
  },
  themeRow: {
    flexDirection: 'row',
    gap: 12,
  },
  themeButton: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 18,
    borderRadius: 10,
    borderWidth: 2,
  },
  themeButtonActive: {
    borderColor: '#5C6BC0',
  },
  themeButtonText: {
    fontSize: 13,
    fontWeight: 'bold',
    marginTop: 6,
  },
  saveButton: {
    backgroundColor: '#5C6BC0',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 14,
    borderRadius: 10,
    marginTop: 8,
    marginBottom: 14,
  },
  saveButtonText: {
    color: '#FFF',
    fontSize: 15,
    fontWeight: 'bold',
  },
  passwordWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 10,
    borderWidth: 1,
    marginBottom: 14,
  },
  passwordInput: {
    flex: 1,
    padding: 14,
    fontSize: 14,
  },
  eyeBtn: {
    padding: 12,
  },
  buttonDisabled: {
    opacity: 0.65,
  },
  logoutButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderRadius: 10,
    backgroundColor: 'rgba(211, 47, 47, 0.12)',
    borderWidth: 1,
    borderColor: 'rgba(211, 47, 47, 0.3)',
    marginBottom: 20,
  },
  logoutButtonText: {
    color: '#FF6B6B',
    fontSize: 14,
    fontWeight: 'bold',
  },

  // E-mail and LGPD styles
  emailContainer: {
    marginBottom: 16,
  },
  btnChangeEmail: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 8,
    paddingVertical: 9,
    paddingHorizontal: 12,
    borderRadius: 8,
    backgroundColor: 'rgba(92, 107, 192, 0.12)',
    borderWidth: 1,
    borderColor: 'rgba(92, 107, 192, 0.35)',
  },
  btnChangeEmailText: {
    color: '#5C6BC0',
    fontSize: 13,
    fontWeight: '600',
  },

  // E-mail change modal
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.75)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  modalCard: {
    width: '100%',
    maxWidth: 480,
    borderRadius: 16,
    padding: 22,
    borderWidth: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 10,
    elevation: 8,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 18,
  },
  modalIconBadge: {
    width: 38,
    height: 38,
    borderRadius: 10,
    backgroundColor: 'rgba(92, 107, 192, 0.18)',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
  },
  modalSubtitle: {
    fontSize: 12,
    marginTop: 2,
  },
  modalCloseBtn: {
    padding: 6,
  },
  stepIndicatorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
    paddingVertical: 8,
    backgroundColor: 'rgba(150, 150, 150, 0.08)',
    borderRadius: 10,
  },
  stepItem: {
    flexDirection: 'row',
    alignItems: 'center',
    opacity: 0.5,
  },
  stepItemActive: {
    opacity: 1,
  },
  stepCircle: {
    width: 24,
    height: 24,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 6,
  },
  stepCircleActive: {
    backgroundColor: '#5C6BC0',
  },
  stepCircleDone: {
    backgroundColor: '#4CAF50',
  },
  stepCircleInactive: {
    backgroundColor: 'rgba(150, 150, 150, 0.2)',
  },
  stepCircleText: {
    color: '#FFF',
    fontSize: 12,
    fontWeight: 'bold',
  },
  stepLabel: {
    fontSize: 13,
    fontWeight: '500',
    color: '#999',
  },
  stepLine: {
    width: 30,
    height: 2,
    backgroundColor: 'rgba(150, 150, 150, 0.2)',
    marginHorizontal: 10,
  },
  stepLineActive: {
    backgroundColor: '#4CAF50',
  },
  modalDevBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 152, 0, 0.12)',
    padding: 10,
    borderRadius: 8,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: 'rgba(255, 152, 0, 0.3)',
  },
  modalDevBannerText: {
    color: '#FF9800',
    fontSize: 12,
    flex: 1,
  },
  modalBody: {
    marginTop: 4,
  },
  modalStepDesc: {
    fontSize: 13,
    lineHeight: 18,
    marginBottom: 10,
  },
  currentEmailHighlight: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    paddingHorizontal: 12,
    backgroundColor: 'rgba(92, 107, 192, 0.08)',
    borderRadius: 8,
    marginBottom: 10,
  },
  currentEmailHighlightText: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#5C6BC0',
  },
  modalResendBtn: {
    alignSelf: 'center',
    marginVertical: 12,
  },
  modalResendText: {
    fontSize: 13,
    fontWeight: '600',
  },
  modalPrimaryBtn: {
    backgroundColor: '#5C6BC0',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 13,
    borderRadius: 10,
  },
  modalPrimaryBtnText: {
    color: '#FFF',
    fontSize: 15,
    fontWeight: 'bold',
  },
  modalSecondaryBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 13,
    paddingHorizontal: 18,
    borderRadius: 10,
    borderWidth: 1,
  },
  modalSecondaryBtnText: {
    fontSize: 14,
    fontWeight: '600',
  },
  btnDisabled: {
    opacity: 0.6,
  },
});