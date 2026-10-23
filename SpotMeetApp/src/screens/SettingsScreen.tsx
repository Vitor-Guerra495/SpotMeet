import React, { useState, useEffect, useCallback, useRef } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator, Modal, BackHandler } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect } from '@react-navigation/native';
import { useAuth } from '../context/AuthContext';
import { useTheme, ThemeType } from '../context/ThemeContext';
import { useProfilePhoto } from '../context/ProfilePhotoContext';
import { useBiometric } from '../context/BiometricContext';
import { API_BASE_URL } from '../config/api';
import { showAlert, confirmAction } from '../utils/alerts';
import ToastHost from '../components/ui/ToastHost';
import ConfirmationHost from '../components/ui/ConfirmationHost';
import { notifySync } from '../utils/syncBus';
import { requestConfirmation } from '../utils/confirmation';
import { setLeaveGuard } from '../utils/leaveGuard';
import { PASSWORD_REGEX, isValidEmail, normalizeEmail, isValidOtp, normalizeOtp, isValidName, normalizeName } from '../utils/validators';
import FormField from '../components/forms/FormField';
import PasswordInput from '../components/forms/PasswordInput';
import PasswordRules from '../components/forms/PasswordRules';
import FormButton from '../components/forms/FormButton';
import OtpInput from '../components/forms/OtpInput';
import FormScrollView from '../components/forms/FormScrollView';
import BiometricSettings from '../components/auth/BiometricSettings';
import ProfilePhotoEditor from '../components/profile/ProfilePhotoEditor';

type PresenceStatus = 'PRESENT' | 'AWAY' | 'BUSY';
type SettingsSection = 'profile' | 'security' | 'preferences';
interface ProfileValues { name: string; bio: string; }
interface PreferenceValues { status: PresenceStatus; theme: ThemeType; }
interface PasswordValues { currentPassword: string; newPassword: string; confirmPassword: string; }
interface EmailValues { currentCode: string; newEmail: string; newEmailCode: string; }

const EMPTY_PASSWORDS: PasswordValues = { currentPassword: '', newPassword: '', confirmPassword: '' };
const EMPTY_EMAIL: EmailValues = { currentCode: '', newEmail: '', newEmailCode: '' };
const SECTIONS: [SettingsSection, string][] = [['profile', 'Perfil'], ['security', 'Segurança'], ['preferences', 'Preferências']];
const STATUS_OPTIONS: [PresenceStatus, string][] = [['PRESENT', 'Presente'], ['AWAY', 'Ausente'], ['BUSY', 'Ocupado']];

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && !(error instanceof TypeError) ? error.message : fallback;
}

async function readResponse(response: Response, fallback: string): Promise<Record<string, unknown>> {
  const data: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    const message = response.status < 500 && data && typeof data === 'object' && 'message' in data
      && typeof data.message === 'string' ? data.message : fallback;
    throw new Error(message);
  }
  if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error(fallback);
  return data as Record<string, unknown>;
}

export default function SettingsScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const { token, signOut, updateSession, userId, getSession } = useAuth();
  const profilePhoto = useProfilePhoto();
  const biometric = useBiometric();
  const renderedSessionVersion = getSession().version;
  const { theme, setTheme, colors } = useTheme();
  const [section, setSection] = useState<SettingsSection>('profile');
  const sectionRef = useRef<SettingsSection>('profile');

  const profileForm = useForm<ProfileValues>({ defaultValues: { name: '', bio: '' }, mode: 'onBlur' });
  const profile = useWatch({ control: profileForm.control });
  const name = profile.name ?? '';
  const bio = profile.bio ?? '';
  const [email, setEmail] = useState('');
  const preferencesForm = useForm<PreferenceValues>({ defaultValues: { status: 'PRESENT', theme } });
  const preferences = useWatch({ control: preferencesForm.control });
  const status = preferences.status ?? 'PRESENT';
  const selectedTheme = preferences.theme ?? theme;
  const savedThemeRef = useRef<ThemeType>(theme);
  const loadedSession = useRef<{ token: string; userId: number | null | undefined } | null>(null);

  const passwordForm = useForm<PasswordValues>({ defaultValues: { ...EMPTY_PASSWORDS }, mode: 'onBlur' });
  const passwords = useWatch({ control: passwordForm.control });
  const currentPassword = passwords.currentPassword ?? '';
  const newPassword = passwords.newPassword ?? '';
  const confirmPassword = passwords.confirmPassword ?? '';
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const emailForm = useForm<EmailValues>({ defaultValues: { ...EMPTY_EMAIL }, mode: 'onBlur' });
  const emailValues = useWatch({ control: emailForm.control });
  const currentCode = emailValues.currentCode ?? '';
  const newEmail = emailValues.newEmail ?? '';
  const newEmailCode = emailValues.newEmailCode ?? '';
  const setCurrentCode = (value: string) => emailForm.setValue('currentCode', value);
  const setNewEmail = (value: string) => emailForm.setValue('newEmail', value);
  const setNewEmailCode = (value: string) => emailForm.setValue('newEmailCode', value);

  const [loadingProfile, setLoadingProfile] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [profileError, setProfileError] = useState('');
  const [preferencesError, setPreferencesError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [emailRequestError, setEmailRequestError] = useState('');
  const [savingSection, setSavingSection] = useState<SettingsSection | null>(null);
  const [changingPassword, setChangingPassword] = useState(false);
  const profileRequestPending = useRef(false);
  const passwordRequestPending = useRef(false);
  const profileReadVersion = useRef(0);
  const themePreviewTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingTheme = useRef<ThemeType | null>(null);

  const [emailModalVisible, setEmailModalVisible] = useState(false);
  const [emailStep, setEmailStep] = useState<1 | 2>(1);
  const [currentCodeError, setCurrentCodeError] = useState('');
  const [newEmailCodeError, setNewEmailCodeError] = useState('');
  const [loadingModal, setLoadingModal] = useState(false);
  const [changeCooldown, setChangeCooldown] = useState(0);
  const emailFlowStep = useRef<0 | 1 | 2>(0);
  const emailRequestPending = useRef(false);
  const currentCodeAttempt = useRef<string | null>(null);
  const newEmailCodeAttempt = useRef<string | null>(null);
  const screenActive = useRef(true);
  const isCurrentScreen = () => screenActive.current && getSession().version === renderedSessionVersion;

  // Read on every render so react-hook-form keeps the dirty state up to date.
  const profileUnsaved = Object.keys(profileForm.formState.dirtyFields).length > 0 || !!profilePhoto.draft;
  const preferencesUnsaved = Object.keys(preferencesForm.formState.dirtyFields).length > 0;
  const unsavedRef = useRef({ profile: false, preferences: false });
  unsavedRef.current = { profile: profileUnsaved, preferences: preferencesUnsaved };

  const cancelThemePreview = () => {
    if (themePreviewTimer.current !== null) clearTimeout(themePreviewTimer.current);
    themePreviewTimer.current = null;
    pendingTheme.current = null;
  };

  const previewTheme = (value: ThemeType) => {
    if (!isCurrentScreen() || sectionRef.current !== 'preferences' || profileRequestPending.current) return;
    if (preferencesForm.getValues('theme') === value) return;
    preferencesForm.setValue('theme', value, { shouldDirty: true });
    setPreferencesError('');
    pendingTheme.current = value;
    if (themePreviewTimer.current !== null) return;
    void setTheme(value, false);
    // Coalesce rapid taps while keeping the last selection.
    themePreviewTimer.current = setTimeout(() => {
      themePreviewTimer.current = null;
      const next = pendingTheme.current;
      pendingTheme.current = null;
      if (next && next !== value && isCurrentScreen() && sectionRef.current === 'preferences') {
        void setTheme(next, false);
      }
    }, 150);
  };

  useEffect(() => {
    screenActive.current = true;
    return () => { screenActive.current = false; profileReadVersion.current += 1; cancelThemePreview(); };
  }, []);

  const clearPasswords = () => {
    passwordForm.reset(EMPTY_PASSWORDS);
    setShowCurrentPassword(false);
    setShowNewPassword(false);
    setShowConfirmPassword(false);
    setPasswordError('');
  };

  // Profile reads happen on focus or retry, never on a polling timer.
  const loadProfile = useCallback(async () => {
    const readVersion = ++profileReadVersion.current;
    if (!token) return;
    setLoadingProfile(true);
    setLoadError('');
    try {
      const response = await fetch(`${API_BASE_URL}/users/me`, {
        method: 'GET', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      });
      const data = await readResponse(response, 'Não foi possível carregar o perfil. Tente novamente.');
      if (!isCurrentScreen() || readVersion !== profileReadVersion.current) return;
      if (typeof data.name !== 'string' || typeof data.email !== 'string'
          || (typeof data.id === 'number' && userId != null && data.id !== userId)) {
        throw new Error('Não foi possível confirmar os dados do perfil.');
      }
      const preserveDrafts = !!loadedSession.current && loadedSession.current.userId === userId && loadedSession.current.token !== token;
      profileForm.reset({ name: data.name, bio: typeof data.bio === 'string' ? data.bio : '' }, { keepDirtyValues: preserveDrafts });
      setEmail(data.email);
      const loadedTheme = data.preferredTheme === 'LIGHT' || data.preferredTheme === 'DARK'
        ? data.preferredTheme : savedThemeRef.current;
      preferencesForm.reset({
        status: data.availabilityStatus === 'AWAY' || data.availabilityStatus === 'BUSY' ? data.availabilityStatus : 'PRESENT',
        theme: loadedTheme,
      }, { keepDirtyValues: preserveDrafts });
      savedThemeRef.current = loadedTheme;
      loadedSession.current = { token, userId };
      await setTheme(preferencesForm.getValues('theme'), false);
    } catch (error) {
      if (isCurrentScreen() && readVersion === profileReadVersion.current) {
        setLoadError(errorMessage(error, 'Não foi possível carregar o perfil. Tente novamente.'));
      }
    } finally {
      if (isCurrentScreen() && readVersion === profileReadVersion.current) setLoadingProfile(false);
    }
  }, [token, userId, setTheme, getSession, renderedSessionVersion, profileForm.reset, preferencesForm.reset]);

  useFocusEffect(useCallback(() => {
    loadProfile();
    return () => {
      profileReadVersion.current += 1;
      cancelThemePreview();
      if (isCurrentScreen()) {
        clearPasswords();
        setTheme(savedThemeRef.current, false);
      }
    };
  }, [loadProfile, setTheme]));

  const discardSection = (target: SettingsSection) => {
    if (target === 'profile') {
      profileForm.reset();
      setProfileError('');
      profilePhoto.discard();
    } else if (target === 'preferences') {
      cancelThemePreview();
      preferencesForm.reset();
      setPreferencesError('');
      void setTheme(savedThemeRef.current, false);
    }
  };

  // Returns true when the user must decide first; `proceed` runs after saving or discarding.
  const askUnsaved = (proceed: () => void) => {
    const current = sectionRef.current;
    if (!isCurrentScreen() || current === 'security' || !unsavedRef.current[current]) return false;
    if (profileRequestPending.current || passwordRequestPending.current) return true;
    requestConfirmation({
      title: 'Alterações não salvas',
      message: current === 'profile'
        ? 'Você alterou o perfil e ainda não salvou. Deseja salvar antes de sair?'
        : 'Você alterou as preferências e ainda não salvou. Deseja salvar antes de sair?',
      confirmText: 'Salvar', cancelText: 'Continuar editando', extraText: 'Descartar', destructive: false,
      onConfirm: async () => {
        const saved = current === 'profile' ? await handleSaveProfile() : await handleSavePreferences();
        if (saved && isCurrentScreen()) proceed();
      },
      onExtra: () => {
        if (!isCurrentScreen()) return;
        discardSection(current);
        proceed();
      },
    });
    return true;
  };
  const askUnsavedRef = useRef(askUnsaved);
  askUnsavedRef.current = askUnsaved;

  // Leaving Ajustes asks about unsaved changes, drops an unsaved photo preview and reopens on Perfil.
  const discardPhoto = profilePhoto.discard;
  useFocusEffect(useCallback(() => {
    const removeGuard = setLeaveGuard(proceed => askUnsavedRef.current(proceed));
    const back = BackHandler.addEventListener('hardwareBackPress', () => askUnsavedRef.current(() => navigation.goBack()));
    return () => {
      removeGuard();
      back.remove();
      discardPhoto();
      sectionRef.current = 'profile';
      setSection('profile');
    };
  }, [navigation, discardPhoto]));

  const changeSection = (next: SettingsSection) => {
    if (!isCurrentScreen() || passwordRequestPending.current) return;
    if (next !== sectionRef.current && askUnsaved(() => switchSection(next))) return;
    switchSection(next);
  };

  const switchSection = (next: SettingsSection) => {
    if (!isCurrentScreen()) return;
    if (next !== 'preferences' && pendingTheme.current) {
      const nextTheme = pendingTheme.current;
      cancelThemePreview();
      void setTheme(nextTheme, false);
    }
    if (next !== 'security') clearPasswords();
    sectionRef.current = next;
    setSection(next);
  };

  const mapFieldErrors = (data: Record<string, unknown>, target: 'profile' | 'password') => {
    if (!data.errors || typeof data.errors !== 'object') return;
    const errors = data.errors as Record<string, unknown>;
    if (target === 'profile') {
      for (const field of ['name', 'bio'] as const) {
        if (typeof errors[field] === 'string') profileForm.setError(field, { type: 'server', message: errors[field] });
      }
    } else {
      for (const field of ['currentPassword', 'newPassword'] as const) {
        if (typeof errors[field] === 'string') passwordForm.setError(field, { type: 'server', message: errors[field] });
      }
    }
  };

  const handleSaveProfile = async (): Promise<boolean> => {
    if (!isCurrentScreen() || sectionRef.current !== 'profile' || profileRequestPending.current || passwordRequestPending.current) return false;
    setProfileError('');
    const cleanName = normalizeName(name);
    if (!isValidName(cleanName)) {
      profileForm.setError('name', { type: 'validate', message: 'O nome deve ter entre 2 e 100 caracteres.' });
      return false;
    }
    if (bio.length > 500) {
      profileForm.setError('bio', { type: 'validate', message: 'A biografia pode ter no máximo 500 caracteres.' });
      return false;
    }
    profileRequestPending.current = true;
    setSavingSection('profile');
    try {
      const response = await fetch(`${API_BASE_URL}/users/me`, {
        method: 'PUT', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: cleanName, bio: bio.trim() }),
      });
      const data: unknown = await response.json().catch(() => null);
      if (!isCurrentScreen()) return false;
      if (!response.ok) {
        if (response.status < 500 && data && typeof data === 'object') mapFieldErrors(data as Record<string, unknown>, 'profile');
        throw new Error('Não foi possível salvar o perfil. Confira os campos e tente novamente.');
      }
      if (!data || typeof data !== 'object' || !('name' in data) || typeof data.name !== 'string' || !isValidName(data.name)
          || ('id' in data && typeof data.id === 'number' && userId != null && data.id !== userId)) {
        throw new Error('Não foi possível confirmar a atualização do perfil.');
      }
      profileForm.reset({ name: data.name, bio: 'bio' in data && typeof data.bio === 'string' ? data.bio : '' });
      updateSession({ name: data.name });
      notifySync('PROFILE_MUTATED', { userId });
      // A photo still in preview is saved with the profile; its error, if any, stays below the photo.
      const photoSaved = profilePhoto.draft ? await profilePhoto.save({ quiet: true }) : null;
      if (!isCurrentScreen()) return false;
      if (photoSaved === false) showAlert('Perfil salvo', 'Os dados do perfil foram salvos, mas a foto não. Veja o aviso abaixo da foto.');
      else showAlert('Sucesso', photoSaved ? 'Perfil e foto salvos com sucesso.' : 'Perfil salvo com sucesso.');
      return photoSaved !== false;
    } catch (error) {
      if (isCurrentScreen()) setProfileError(errorMessage(error, 'Não foi possível salvar o perfil. Tente novamente.'));
      return false;
    } finally {
      profileRequestPending.current = false;
      if (isCurrentScreen()) setSavingSection(null);
    }
  };

  const handleSavePreferences = async (): Promise<boolean> => {
    if (!isCurrentScreen() || sectionRef.current !== 'preferences' || profileRequestPending.current || passwordRequestPending.current) return false;
    const preferenceValues = preferencesForm.getValues();
    cancelThemePreview();
    void setTheme(preferenceValues.theme, false);
    profileRequestPending.current = true;
    setSavingSection('preferences');
    setPreferencesError('');
    try {
      const response = await fetch(`${API_BASE_URL}/users/me`, {
        method: 'PUT', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ availabilityStatus: preferenceValues.status, preferredTheme: preferenceValues.theme }),
      });
      const data = await readResponse(response, 'Não foi possível salvar as preferências. Tente novamente.');
      if (!isCurrentScreen()) return false;
      if ((typeof data.id === 'number' && userId != null && data.id !== userId)
          || (data.preferredTheme !== 'LIGHT' && data.preferredTheme !== 'DARK')
          || !['PRESENT', 'AWAY', 'BUSY', 'ONLINE'].includes(data.availabilityStatus as string)) {
        throw new Error('Não foi possível confirmar a atualização das preferências.');
      }
      const savedStatus = data.availabilityStatus === 'AWAY' || data.availabilityStatus === 'BUSY' ? data.availabilityStatus : 'PRESENT';
      preferencesForm.reset({ status: savedStatus, theme: data.preferredTheme });
      savedThemeRef.current = data.preferredTheme;
      await setTheme(data.preferredTheme, false);
      if (!isCurrentScreen()) return false;
      notifySync('PROFILE_MUTATED', { userId });
      showAlert('Sucesso', 'Preferências salvas com sucesso.');
      return true;
    } catch (error) {
      if (isCurrentScreen()) setPreferencesError(errorMessage(error, 'Não foi possível salvar as preferências. Tente novamente.'));
      return false;
    } finally {
      profileRequestPending.current = false;
      if (isCurrentScreen()) setSavingSection(null);
    }
  };

  const endSession = async () => {
    if (!isCurrentScreen()) return;
    clearPasswords();
    emailForm.reset(EMPTY_EMAIL);
    emailFlowStep.current = 0;
    screenActive.current = false;
    const removal = signOut();
    const endedVersion = getSession().version;
    navigation.reset({ index: 0, routes: [{ name: 'Auth', params: { mode: 'login' } }] });
    await removal;
    return endedVersion;
  };

  const handleChangePassword = async () => {
    if (!isCurrentScreen() || sectionRef.current !== 'security' || passwordRequestPending.current) return;
    setPasswordError('');
    if (!currentPassword) {
      passwordForm.setError('currentPassword', { type: 'validate', message: 'Informe a sua senha atual.' });
      return;
    }
    if (!PASSWORD_REGEX.test(newPassword)) {
      passwordForm.setError('newPassword', { type: 'validate', message: 'Use pelo menos 6 caracteres, com maiúscula, minúscula e número.' });
      return;
    }
    if (newPassword !== confirmPassword) {
      passwordForm.setError('confirmPassword', { type: 'validate', message: 'A confirmação não coincide com a nova senha.' });
      return;
    }
    if (newPassword === currentPassword) {
      passwordForm.setError('newPassword', { type: 'validate', message: 'A nova senha deve ser diferente da senha atual.' });
      return;
    }
    passwordRequestPending.current = true;
    setChangingPassword(true);
    try {
      if (!await biometric.confirmIdentity()) {
        if (isCurrentScreen()) setPasswordError('Confirme com a biometria para alterar a senha.');
        return;
      }
      if (!isCurrentScreen()) return;
      const response = await fetch(`${API_BASE_URL}/users/me/password`, {
        method: 'PUT', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      const data: unknown = await response.json().catch(() => null);
      if (!isCurrentScreen()) return;
      if (!response.ok) {
        if (response.status < 500 && data && typeof data === 'object') {
          mapFieldErrors(data as Record<string, unknown>, 'password');
        }
        const message = response.status < 500 && data && typeof data === 'object'
          && 'message' in data && typeof data.message === 'string' ? data.message : 'Não foi possível alterar a senha. Tente novamente.';
        if (response.status === 400 && message === 'A senha atual está incorreta.') {
          passwordForm.setError('currentPassword', { type: 'server', message });
          return;
        }
        if (response.status === 400 && message === 'A nova senha deve ser diferente da senha atual.') {
          passwordForm.setError('newPassword', { type: 'server', message });
          return;
        }
        throw new Error(message);
      }
      if (!data || typeof data !== 'object' || !('success' in data) || data.success !== true) {
        throw new Error('Não foi possível confirmar a alteração da senha.');
      }
      await biometric.keepForPasswordChange();
      const endedVersion = await endSession();
      if (getSession().version === endedVersion && getSession().token === null) {
        showAlert('Sucesso', 'Senha alterada com sucesso. Entre novamente com a nova senha.');
      }
    } catch (error) {
      if (isCurrentScreen()) setPasswordError(errorMessage(error, 'Não foi possível alterar a senha. Tente novamente.'));
    } finally {
      passwordRequestPending.current = false;
      if (isCurrentScreen()) setChangingPassword(false);
    }
  };

  const handleLogout = () => {
    // The dialog closes right away; clearing the saved session on the device continues in the background.
    if (isCurrentScreen()) confirmAction('Sair da Conta', 'Deseja realmente desconectar do SpotMeet?',
      () => { if (isCurrentScreen()) void endSession(); }, 'Sair', true);
  };

  useEffect(() => {
    if (changeCooldown <= 0) return;
    const timer = setInterval(() => setChangeCooldown(previous => Math.max(0, previous - 1)), 1000);
    return () => clearInterval(timer);
  }, [changeCooldown]);


  // Opens the modal and sends the code to the current e-mail
  const openEmailChangeModal = async () => {
    if (!isCurrentScreen() || sectionRef.current !== 'profile' || profileRequestPending.current || passwordRequestPending.current || emailRequestPending.current) return;
    emailRequestPending.current = true;
    const confirmed = await biometric.confirmIdentity();
    emailRequestPending.current = false;
    if (!isCurrentScreen()) return;
    if (!confirmed) {
      showAlert('Confirmação necessária', 'Confirme com a biometria para alterar o e-mail.');
      return;
    }
    emailForm.reset(EMPTY_EMAIL);
    setEmailRequestError('');
    emailFlowStep.current = 1;
    emailRequestPending.current = true;
    currentCodeAttempt.current = null;
    newEmailCodeAttempt.current = null;
    setEmailModalVisible(true);
    setEmailStep(1);
    setCurrentCode('');
    setNewEmail('');
    setNewEmailCode('');
    setCurrentCodeError('');
    setNewEmailCodeError('');
    setLoadingModal(true);

    try {
      const res = await fetch(`${API_BASE_URL}/users/me/email-change/request`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      const data = await readResponse(res, 'Falha ao solicitar código para o e-mail atual.');
      if (!isCurrentScreen()) return;
      if (data.success !== true) throw new Error('Não foi possível confirmar a operação. Tente novamente.');

      setEmailRequestError('');
      setChangeCooldown(60);
      showAlert('Código enviado', 'Verifique o seu e-mail.');
    } catch (error) {
      if (!isCurrentScreen()) return;
      setEmailRequestError(errorMessage(error, 'Não foi possível solicitar código de segurança.'));
    } finally {
      if (isCurrentScreen()) {
        emailRequestPending.current = false;
        setLoadingModal(false);
      }
    }
  };

  // Resends the step 1 code (current e-mail)
  const handleResendCurrentCode = async () => {
    if (!isCurrentScreen() || emailFlowStep.current !== 1 || emailRequestPending.current || changeCooldown > 0) return;
    emailRequestPending.current = true;
    setLoadingModal(true);
    try {
      const res = await fetch(`${API_BASE_URL}/users/me/email-change/request`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });
      const data = await readResponse(res, 'Não foi possível reenviar o código.');
      if (!isCurrentScreen()) return;
      if (data.success !== true) throw new Error('Não foi possível confirmar a operação. Tente novamente.');
      setEmailRequestError('');
      setChangeCooldown(60);
      setCurrentCode('');
      setCurrentCodeError('');
      currentCodeAttempt.current = null;
      showAlert('Código enviado', 'Verifique o seu e-mail.');
    } catch (error) {
      if (!isCurrentScreen()) return;
      setEmailRequestError(errorMessage(error, 'Falha ao reenviar código.'));
    } finally {
      if (isCurrentScreen()) {
        emailRequestPending.current = false;
        setLoadingModal(false);
      }
    }
  };

  // Validates step 1 and sends the code to the new e-mail (start of step 2)
  const handleAdvanceToStep2 = async (enteredCode = currentCode) => {
    if (!isCurrentScreen() || emailFlowStep.current !== 1 || emailRequestPending.current) return;
    if (!isValidOtp(enteredCode)) {
      setCurrentCodeError('Digite o código de 6 dígitos recebido no seu e-mail atual.');
      return;
    }

    const cleanEmail = normalizeEmail(emailForm.getValues('newEmail'));
    if (!isValidEmail(cleanEmail)) {
      emailForm.setError('newEmail', { type: 'validate', message: 'Informe um endereço de e-mail válido.' });
      return;
    }

    if (cleanEmail === normalizeEmail(email)) {
      emailForm.setError('newEmail', { type: 'validate', message: 'O novo e-mail deve ser diferente do atual.' });
      return;
    }

    emailRequestPending.current = true;
    currentCodeAttempt.current = enteredCode;
    setCurrentCodeError('');
    setLoadingModal(true);
    try {
      const res = await fetch(`${API_BASE_URL}/users/me/email-change/confirm`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          currentCode: enteredCode,
          newEmail: cleanEmail,
        }),
      });

      const data = await readResponse(res, 'Código do e-mail atual inválido ou expirado.');
      if (!isCurrentScreen()) return;
      if (data.success !== true) throw new Error('Não foi possível confirmar a operação. Tente novamente.');

      emailFlowStep.current = 2;
      setEmailStep(2);
      setEmailRequestError('');
      setChangeCooldown(60);
      setCurrentCode('');
      setNewEmailCode('');
      setNewEmailCodeError('');
      newEmailCodeAttempt.current = null;
      showAlert('Código enviado', 'Verifique o seu e-mail.');
    } catch (error) {
      if (!isCurrentScreen()) return;
      setCurrentCodeError(errorMessage(error, 'Não foi possível confirmar o código do e-mail atual.'));
    } finally {
      if (isCurrentScreen()) {
        emailRequestPending.current = false;
        setLoadingModal(false);
      }
    }
  };

  const tryVerifyCurrentCode = (enteredCode: string) => {
    const cleanEmail = normalizeEmail(emailForm.getValues('newEmail'));
    if (emailFlowStep.current === 1 && isValidOtp(enteredCode) && isValidEmail(cleanEmail)
      && cleanEmail !== normalizeEmail(email) && currentCodeAttempt.current !== enteredCode) {
      return handleAdvanceToStep2(enteredCode);
    }
  };

  const handleCurrentCodeChange = (text: string) => {
    if (!isCurrentScreen() || emailFlowStep.current !== 1 || emailRequestPending.current) return;
    const nextCode = normalizeOtp(text);
    setCurrentCode(nextCode);
    if (nextCode !== currentCode) setCurrentCodeError('');
    if (!isValidOtp(nextCode)) currentCodeAttempt.current = null;
    return tryVerifyCurrentCode(nextCode);
  };

  // Resends the step 2 code (new e-mail)
  const handleResendNewEmailCode = async () => {
    if (!isCurrentScreen() || emailFlowStep.current !== 2 || emailRequestPending.current || changeCooldown > 0) return;
    emailRequestPending.current = true;
    setLoadingModal(true);
    try {
      const res = await fetch(`${API_BASE_URL}/users/me/email-change/resend-new`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });
      const data = await readResponse(res, 'Falha ao reenviar código para o novo e-mail.');
      if (!isCurrentScreen()) return;
      if (data.success !== true) throw new Error('Não foi possível confirmar a operação. Tente novamente.');
      setEmailRequestError('');
      setChangeCooldown(60);
      setNewEmailCode('');
      setNewEmailCodeError('');
      newEmailCodeAttempt.current = null;
      showAlert('Código enviado', 'Verifique o seu e-mail.');
    } catch (error) {
      if (!isCurrentScreen()) return;
      setEmailRequestError(errorMessage(error, 'Não foi possível reenviar o código.'));
    } finally {
      if (isCurrentScreen()) {
        emailRequestPending.current = false;
        setLoadingModal(false);
      }
    }
  };

  // Completes step 2, updates the database, refreshes the JWT and closes the modal
  const handleCompleteEmailChange = async (enteredCode = newEmailCode) => {
    if (!isCurrentScreen() || emailFlowStep.current !== 2 || emailRequestPending.current) return;
    if (!isValidOtp(enteredCode)) {
      setNewEmailCodeError('Digite o código de 6 dígitos recebido no seu novo e-mail.');
      return;
    }

    emailRequestPending.current = true;
    newEmailCodeAttempt.current = enteredCode;
    setNewEmailCodeError('');
    setLoadingModal(true);
    try {
      const res = await fetch(`${API_BASE_URL}/users/me/email-change/complete`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          newEmailCode: enteredCode,
        }),
      });

      const data = await readResponse(res, 'Código de validação incorreto ou expirado.');
      if (!isCurrentScreen()) return;
      if (data.success !== true) throw new Error('Não foi possível confirmar a operação. Tente novamente.');

      if (typeof data.token !== 'string' || !data.token.trim()
          || typeof data.newEmail !== 'string' || !isValidEmail(data.newEmail)) {
        throw new Error('Não foi possível confirmar a alteração de e-mail. Entre novamente se necessário.');
      }
      setEmail(data.newEmail);
      emailFlowStep.current = 0;
      setEmailModalVisible(false);
      emailForm.reset(EMPTY_EMAIL);
      currentCodeAttempt.current = null;
      newEmailCodeAttempt.current = null;
      setChangeCooldown(0);
      emailRequestPending.current = false;
      setLoadingModal(false);
      updateSession({ token: data.token, email: data.newEmail });
      showAlert('Sucesso', 'E-mail atualizado com sucesso.');
    } catch (error) {
      if (!isCurrentScreen()) return;
      setNewEmailCodeError(errorMessage(error, 'Não foi possível concluir a alteração de e-mail.'));
    } finally {
      if (isCurrentScreen()) {
        emailRequestPending.current = false;
        setLoadingModal(false);
      }
    }
  };

  const handleNewEmailCodeChange = (text: string) => {
    if (!isCurrentScreen() || emailFlowStep.current !== 2 || emailRequestPending.current) return;
    const nextCode = normalizeOtp(text);
    setNewEmailCode(nextCode);
    if (nextCode !== newEmailCode) setNewEmailCodeError('');
    if (!isValidOtp(nextCode)) {
      newEmailCodeAttempt.current = null;
    } else if (newEmailCodeAttempt.current !== nextCode) {
      return handleCompleteEmailChange(nextCode);
    }
  };

  const closeEmailChangeModal = () => {
    if (emailRequestPending.current) return;
    emailFlowStep.current = 0;
    setEmailModalVisible(false);
    emailForm.reset(EMPTY_EMAIL);
    setEmailRequestError('');
    setChangeCooldown(0);
    setCurrentCode('');
    setNewEmail('');
    setNewEmailCode('');
    setCurrentCodeError('');
    setNewEmailCodeError('');
    currentCodeAttempt.current = null;
    newEmailCodeAttempt.current = null;
  };


  const accent = colors.isDark ? '#9FA8DA' : colors.primary;
  const busyProfile = savingSection === 'profile';
  const feedback = (message: string) => message ? (
    <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={[styles.error, { color: colors.danger }]}>{message}</Text>
  ) : null;

  const renderPassword = (field: keyof PasswordValues, label: string, placeholder: string) => {
    const visible = field === 'currentPassword' ? showCurrentPassword : field === 'newPassword' ? showNewPassword : showConfirmPassword;
    const toggle = () => {
      if (!isCurrentScreen() || sectionRef.current !== 'security' || passwordRequestPending.current) return;
      if (field === 'currentPassword') setShowCurrentPassword(!visible);
      else if (field === 'newPassword') setShowNewPassword(!visible);
      else setShowConfirmPassword(!visible);
    };
    return (
      <Controller key={field} control={passwordForm.control} name={field}
        rules={{ required: 'Campo obrigatório.', validate: value => {
          if (field === 'currentPassword') return true;
          if (field === 'confirmPassword') return value === passwordForm.getValues('newPassword') || 'As senhas não coincidem.';
          return PASSWORD_REGEX.test(value) || 'Use pelo menos 6 caracteres, com maiúscula, minúscula e número.';
        } }}
        render={({ field: controlled }) => (
          <PasswordInput label={label} placeholder={placeholder} required ref={controlled.ref}
            value={controlled.value} editable={!changingPassword} visible={visible} onToggleVisibility={toggle}
            onBlur={controlled.onBlur} error={passwordForm.formState.errors[field]?.message}
            onChangeText={value => {
              if (!isCurrentScreen() || sectionRef.current !== 'security' || passwordRequestPending.current) return;
              controlled.onChange(value);
              passwordForm.clearErrors(field);
              setPasswordError('');
            }} />
        )} />
    );
  };

  const renderProfile = () => (
    <>
      <ProfilePhotoEditor name={name} disabled={savingSection !== null || changingPassword} />
      <Controller control={profileForm.control} name="name"
        rules={{ required: 'Campo obrigatório.', validate: value => isValidName(value) || 'O nome deve ter entre 2 e 100 caracteres.' }}
        render={({ field }) => (
          <FormField ref={field.ref} label="Nome completo" placeholder="Digite seu nome completo" required icon="person-outline"
            value={field.value} editable={!busyProfile} autoCapitalize="words" autoCorrect={false}
            error={profileForm.formState.errors.name?.message} onBlur={field.onBlur}
            onChangeText={value => {
              if (!isCurrentScreen() || sectionRef.current !== 'profile' || profileRequestPending.current) return;
              field.onChange(value); profileForm.clearErrors('name'); setProfileError('');
            }} />
        )} />
      <Controller control={profileForm.control} name="bio"
        rules={{ maxLength: { value: 500, message: 'A biografia pode ter no máximo 500 caracteres.' } }}
        render={({ field }) => (
          <FormField ref={field.ref} label="Biografia (opcional)" placeholder="Conte um pouco sobre você"
            value={field.value} editable={!busyProfile} multiline maxLength={500} textAlignVertical="top"
            style={{ minHeight: 100 }} onBlur={field.onBlur} error={profileForm.formState.errors.bio?.message}
            onChangeText={value => {
              if (!isCurrentScreen() || sectionRef.current !== 'profile' || profileRequestPending.current) return;
              field.onChange(value); profileForm.clearErrors('bio'); setProfileError('');
            }} />
        )} />
      <Text style={[styles.counter, { color: colors.subtext }]}>{bio.length} / 500</Text>
      <FormField label="E-mail" value={email} editable={false} icon="mail-outline"
        trailing={<TouchableOpacity accessibilityRole="button" accessibilityLabel="Alterar e-mail"
          disabled={savingSection !== null || changingPassword} onPress={openEmailChangeModal} style={styles.emailAction}>
          <Text style={[styles.emailActionText, { color: accent }]}>Alterar</Text>
        </TouchableOpacity>} />
      {feedback(profileError)}
    </>
  );

  const renderSecurity = () => (
    <>
      <View style={styles.sectionIntro}>
        <Ionicons name="shield-checkmark-outline" size={25} color={accent} />
        <View style={styles.introText}>
          <Text style={[styles.sectionTitle, { color: colors.text }]}>Altere sua senha</Text>
          <Text style={[styles.description, { color: colors.subtext }]}>Após salvar, entre novamente com a nova senha.</Text>
        </View>
      </View>
      {renderPassword('currentPassword', 'Senha atual', 'Digite sua senha atual')}
      {renderPassword('newPassword', 'Nova senha', 'Digite a nova senha')}
      {renderPassword('confirmPassword', 'Confirmar nova senha', 'Repita a nova senha')}
      <View style={[styles.rules, { backgroundColor: colors.card, borderColor: colors.border }]}>
        <PasswordRules password={newPassword} confirmation={confirmPassword} />
      </View>
      {feedback(passwordError)}
      <BiometricSettings disabled={changingPassword || savingSection !== null} />
    </>
  );

  const renderPreferences = () => (
    <>
      <Text style={[styles.label, { color: colors.subtext }]}>DISPONIBILIDADE</Text>
      <View accessibilityRole="radiogroup" style={styles.optionsRow}>
        {STATUS_OPTIONS.map(([value, label]) => (
          <TouchableOpacity key={value} accessibilityRole="radio" accessibilityLabel={label}
            accessibilityState={{ checked: status === value, disabled: savingSection !== null }}
            disabled={savingSection !== null}
            style={[styles.statusOption, { backgroundColor: colors.card, borderColor: status === value ? colors.primary : colors.border }]}
            onPress={() => {
              if (!isCurrentScreen() || sectionRef.current !== 'preferences' || profileRequestPending.current) return;
              preferencesForm.setValue('status', value, { shouldDirty: true }); setPreferencesError('');
            }}>
            <View style={[styles.statusDot, { backgroundColor: value === 'PRESENT' ? colors.success : value === 'AWAY' ? colors.warning : colors.danger }]} />
            <Text style={[styles.optionLabel, { color: colors.text }]}>{label}</Text>
            {status === value ? <Ionicons name="checkmark-circle" size={16} color={accent} /> : null}
          </TouchableOpacity>
        ))}
      </View>
      <Text style={[styles.label, { color: colors.subtext }]}>TEMA</Text>
      <View accessibilityRole="radiogroup" style={styles.optionsRow}>
        {(['LIGHT', 'DARK'] as const).map(value => (
          <TouchableOpacity key={value} accessibilityRole="radio"
            accessibilityLabel={value === 'LIGHT' ? 'Tema claro' : 'Tema escuro'}
            accessibilityState={{ checked: selectedTheme === value, disabled: savingSection !== null }}
            disabled={savingSection !== null}
            onPress={() => previewTheme(value)}
            style={[styles.themeOption, { backgroundColor: colors.card, borderColor: selectedTheme === value ? colors.primary : colors.border }]}>
            <View style={[styles.themePreview, { backgroundColor: value === 'LIGHT' ? '#F4F6F9' : '#121212' }]}>
              <View style={[styles.previewLine, { backgroundColor: value === 'LIGHT' ? '#CFD8DC' : '#333333' }]} />
              <View style={[styles.previewCard, { backgroundColor: value === 'LIGHT' ? '#FFFFFF' : '#1E1E1E' }]} />
              <View style={[styles.previewAction, { backgroundColor: colors.primary }]} />
            </View>
            <View style={styles.themeLabel}>
              <Ionicons name={value === 'LIGHT' ? 'sunny-outline' : 'moon-outline'} size={18} color={accent} />
              <Text style={[styles.optionLabel, { color: colors.text }]}>{value === 'LIGHT' ? 'Claro' : 'Escuro'}</Text>
              {selectedTheme === value ? <Ionicons name="checkmark-circle" size={16} color={accent} /> : null}
            </View>
          </TouchableOpacity>
        ))}
      </View>
      <Text style={[styles.description, { color: colors.subtext, marginVertical: 12 }]}>
        O tema muda na prévia. Salve as preferências para manter a escolha; sair de Ajustes desfaz a prévia não salva.
      </Text>
      {feedback(preferencesError)}
      <TouchableOpacity accessibilityRole="button" onPress={handleLogout}
        style={[styles.logout, { borderColor: colors.danger }]}>
        <Ionicons name="log-out-outline" size={21} color={colors.danger} />
        <Text style={[styles.optionLabel, { color: colors.danger }]}>Sair da conta</Text>
      </TouchableOpacity>
    </>
  );

  return (
    <View style={[styles.root, { backgroundColor: colors.background }]}>
      <View style={[styles.header, { paddingTop: insets.top + 16 }]}>
        <Text style={[styles.title, { color: colors.text }]}>Ajustes</Text>
        <Text style={[styles.description, { color: colors.subtext }]}>Perfil, segurança e preferências</Text>
        <View style={[styles.tabs, { backgroundColor: colors.inputBg }]}>
          {SECTIONS.map(([value, label]) => (
            <TouchableOpacity key={value} accessibilityRole="tab"
              accessibilityState={{ selected: section === value, disabled: changingPassword }}
              disabled={changingPassword} onPress={() => changeSection(value)}
              style={[styles.tab, section === value && { backgroundColor: colors.card }]}>
              <Text style={[styles.tabLabel, { color: section === value ? accent : colors.subtext }]}>{label}</Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>
      {loadingProfile ? <View style={styles.loading}><ActivityIndicator color={colors.primary} />
        <Text style={[styles.description, { color: colors.subtext }]}>Carregando perfil...</Text></View>
      : loadError ? <View style={styles.loading}>{feedback(loadError)}
        <FormButton label="Tentar novamente" icon="refresh-outline" onPress={loadProfile} /></View>
      : <>
        <FormScrollView key={section} contentContainerStyle={styles.content} footer={
          <View style={[styles.footer, { backgroundColor: colors.background, borderTopColor: colors.border }]}>
          {(section === 'profile' && profileUnsaved) || (section === 'preferences' && preferencesUnsaved)
            ? <Text style={[styles.description, { color: colors.subtext }]}>Alterações não salvas</Text> : null}
          {section === 'profile'
            ? <FormButton label="Salvar Alterações" icon="save-outline" onPress={() => { void handleSaveProfile(); }}
                loading={savingSection === 'profile'} disabled={savingSection !== null || changingPassword || !profileUnsaved} />
            : section === 'security'
              ? <FormButton label="Alterar Senha" icon="shield-checkmark-outline" onPress={handleChangePassword} loading={changingPassword} />
              : <FormButton label="Salvar preferências" icon="save-outline" onPress={() => { void handleSavePreferences(); }}
                  loading={savingSection === 'preferences'} disabled={savingSection !== null || changingPassword || !preferencesUnsaved} />}
          </View>
        }>
          {section === 'profile' ? renderProfile() : section === 'security' ? renderSecurity() : renderPreferences()}
        </FormScrollView>
      </>}
      <Modal visible={emailModalVisible} transparent animationType="slide" onRequestClose={closeEmailChangeModal}>
        <View style={styles.overlay}>
          <FormScrollView key={emailStep} contentContainerStyle={[styles.sheetContainer, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 16 }]}>
            <View style={[styles.sheet, { backgroundColor: colors.card }]}>
              <View style={styles.sheetHeader}>
                <Text style={[styles.stepLabel, { color: accent }]}>ETAPA {emailStep} DE 2</Text>
                <TouchableOpacity accessibilityRole="button" accessibilityLabel="Fechar alteração de e-mail"
                  disabled={loadingModal} onPress={closeEmailChangeModal} style={styles.close}>
                  <Ionicons name="close" size={22} color={colors.subtext} />
                </TouchableOpacity>
              </View>
              <View style={styles.progress}>
                {[1, 2].map(value => <View key={value} style={[styles.progressStep, { backgroundColor: value <= emailStep ? colors.primary : colors.border }]} />)}
              </View>
              <Text style={[styles.sectionTitle, { color: colors.text, fontSize: 20 }]}>
                {emailStep === 1 ? 'Confirme seu e-mail atual' : 'Verifique o novo e-mail'}
              </Text>
              <Text style={[styles.description, { color: colors.subtext, marginTop: 8 }]}>
                {emailStep === 1 ? 'Confira o código recebido em ' : 'Digite o código enviado para '}
                <Text style={{ color: colors.text, fontWeight: '600' }}>{emailStep === 1 ? email : newEmail}</Text>
              </Text>
              <OtpInput label={emailStep === 1 ? 'Código do e-mail atual' : 'Código do novo e-mail'}
                value={emailStep === 1 ? currentCode : newEmailCode}
                editable={!loadingModal} error={emailStep === 1 ? currentCodeError : newEmailCodeError}
                onChangeText={emailStep === 1 ? handleCurrentCodeChange : handleNewEmailCodeChange} />
              {emailStep === 1 ? <Controller control={emailForm.control} name="newEmail"
                rules={{ required: 'Campo obrigatório.', validate: value => isValidEmail(value) || 'Informe um endereço de e-mail válido.' }}
                render={({ field }) => (
                  <FormField ref={field.ref} label="Novo e-mail" placeholder="novo.email@exemplo.com" required icon="mail-outline"
                    value={field.value} editable={!loadingModal} keyboardType="email-address" autoCapitalize="none" autoCorrect={false}
                    error={emailForm.formState.errors.newEmail?.message}
                    onBlur={field.onBlur} onEndEditing={() => tryVerifyCurrentCode(emailForm.getValues('currentCode'))}
                    onChangeText={value => {
                      if (!isCurrentScreen() || emailFlowStep.current !== 1 || emailRequestPending.current) return;
                      field.onChange(value); emailForm.clearErrors('newEmail'); setEmailRequestError('');
                    }} />
                )} /> : null}
              {feedback(emailRequestError)}
              <TouchableOpacity accessibilityRole="button" disabled={changeCooldown > 0 || loadingModal}
                onPress={emailStep === 1 ? handleResendCurrentCode : handleResendNewEmailCode} style={styles.link}>
                <Text style={[styles.linkText, { color: changeCooldown > 0 ? colors.subtext : accent }]}>
                  {changeCooldown > 0 ? `Reenviar código em ${changeCooldown}s` : emailStep === 1 ? 'Reenviar código para e-mail atual' : 'Reenviar código para novo e-mail'}
                </Text>
              </TouchableOpacity>
              {emailStep === 1
                ? <FormButton label="Avançar para Etapa 2" icon="arrow-forward-outline" onPress={() => handleAdvanceToStep2()} loading={loadingModal} />
                : <>
                  <FormButton label="Confirmar e Atualizar" icon="checkmark-circle-outline" onPress={() => handleCompleteEmailChange()} loading={loadingModal} />
                  <TouchableOpacity accessibilityRole="button" disabled={loadingModal} style={styles.link}
                    onPress={() => {
                      if (!isCurrentScreen() || emailRequestPending.current) return;
                      emailFlowStep.current = 1;
                      setCurrentCode(''); currentCodeAttempt.current = null; setEmailStep(1);
                      setEmailRequestError('Para alterar o endereço novamente, reenvie o código para o e-mail atual.');
                    }}>
                    <Text style={[styles.linkText, { color: accent }]}>Voltar</Text>
                  </TouchableOpacity>
                </>}
            </View>
          </FormScrollView>
        </View>
      <ToastHost active={emailModalVisible} /><ConfirmationHost active={emailModalVisible} />
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: { paddingHorizontal: 16, paddingBottom: 12, width: '100%', maxWidth: 480, alignSelf: 'center' },
  title: { fontSize: 26, fontWeight: '700', marginBottom: 5 },
  description: { fontSize: 13, lineHeight: 19 },
  tabs: { flexDirection: 'row', borderRadius: 14, padding: 4, marginTop: 14, gap: 3 },
  tab: { flex: 1, minHeight: 48, borderRadius: 10, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 3 },
  tabLabel: { fontSize: 12, lineHeight: 17, fontWeight: '600', textAlign: 'center' },
  content: { flexGrow: 1, padding: 16, width: '100%', maxWidth: 480, alignSelf: 'center' },
  counter: { fontSize: 12, textAlign: 'right', marginBottom: 12, marginTop: -5 },
  emailAction: { minHeight: 48, paddingHorizontal: 12, justifyContent: 'center' },
  emailActionText: { fontSize: 12, fontWeight: '600' },
  error: { fontSize: 13, lineHeight: 19, marginVertical: 8 },
  loading: { flex: 1, padding: 24, justifyContent: 'center', alignItems: 'center', gap: 10 },
  footer: { paddingHorizontal: 16, paddingTop: 4, paddingBottom: 12, borderTopWidth: 1, width: '100%', maxWidth: 480, alignSelf: 'center' },
  sectionIntro: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 18 },
  introText: { flex: 1, gap: 5 },
  sectionTitle: { fontSize: 16, lineHeight: 23, fontWeight: '700' },
  rules: { borderWidth: 1, padding: 12, paddingBottom: 0, borderRadius: 14, marginBottom: 12 },
  future: { flexDirection: 'row', gap: 12, borderWidth: 1, borderRadius: 14, padding: 14, alignItems: 'center', marginTop: 8 },
  label: { fontSize: 11, fontWeight: '700', marginBottom: 10, marginTop: 6 },
  optionsRow: { flexDirection: 'row', gap: 8, marginBottom: 16 },
  statusOption: { flex: 1, borderWidth: 2, borderRadius: 14, minHeight: 88, padding: 10, alignItems: 'center', justifyContent: 'center', gap: 7 },
  statusDot: { width: 10, height: 10, borderRadius: 5 },
  optionLabel: { fontSize: 12, lineHeight: 17, fontWeight: '600', textAlign: 'center' },
  themeOption: { flex: 1, borderWidth: 2, borderRadius: 14, padding: 8 },
  themePreview: { height: 94, padding: 10, borderRadius: 9, gap: 8 },
  previewLine: { height: 6, width: '60%', borderRadius: 3 },
  previewCard: { height: 35, borderRadius: 6 },
  previewAction: { height: 8, width: '55%', borderRadius: 4 },
  themeLabel: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7 },
  logout: { minHeight: 48, borderWidth: 1, borderRadius: 14, flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 10, marginTop: 12 },
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.65)' },
  sheetContainer: { flexGrow: 1, justifyContent: 'flex-end', paddingHorizontal: 12, alignItems: 'center' },
  sheet: { width: '100%', maxWidth: 480, padding: 18, borderRadius: 24 },
  sheetHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  stepLabel: { fontSize: 11, fontWeight: '700' },
  close: { minHeight: 48, minWidth: 48, justifyContent: 'center', alignItems: 'center' },
  progress: { flexDirection: 'row', gap: 6, marginBottom: 16 },
  progressStep: { flex: 1, height: 4, borderRadius: 2 },
  link: { minHeight: 48, alignItems: 'center', justifyContent: 'center', paddingVertical: 8 },
  linkText: { fontSize: 13, lineHeight: 18, fontWeight: '600', textAlign: 'center' },
});
