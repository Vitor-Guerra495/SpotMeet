import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  BackHandler,
} from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { Controller, useForm, useWatch } from 'react-hook-form';
import FormField from '../components/forms/FormField';
import PasswordInput from '../components/forms/PasswordInput';
import PasswordRules from '../components/forms/PasswordRules';
import FormButton from '../components/forms/FormButton';
import OtpInput from '../components/forms/OtpInput';
import { useAuth, type AuthSession } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { API_BASE_URL } from '../config/api';
import { showAlert, confirmAction } from '../utils/alerts';
import { PASSWORD_REGEX, isValidEmail, normalizeEmail, isValidOtp, normalizeOtp, isValidName, normalizeName } from '../utils/validators';
import AuthLayout from '../components/auth/AuthLayout';
import ServerSettings from '../components/auth/ServerSettings';

export type AuthMode = 'login' | 'register' | 'verify' | 'forgot' | 'otp' | 'reset';

interface AuthFormValues {
  email: string;
  password: string;
  name: string;
  code: string;
  recoveryToken: string;
  newPassword: string;
  confirmPassword: string;
}

const EMPTY_FORM: AuthFormValues = {
  email: '', password: '', name: '', code: '', recoveryToken: '', newPassword: '', confirmPassword: '',
};

// Error codes returned by the login endpoint when the account is not verified yet
const EMAIL_NOT_VERIFIED_CODES = ['EMAIL_NOT_VERIFIED'];

export default function AuthScreen({ route, navigation }: any) {
  const { signIn, getSession } = useAuth();
  const { colors } = useTheme();

  // Initial mode from the route param, defaults to 'login'
  const initialRouteMode: AuthMode = route.params?.mode || 'login';
  const [mode, setMode] = useState<AuthMode>(initialRouteMode);

  const form = useForm<AuthFormValues>({
    defaultValues: { ...EMPTY_FORM, email: route.params?.email || '' },
    mode: 'onBlur',
  });
  const { control, setValue, setError, clearErrors, formState: { errors } } = form;
  const values = useWatch({ control });
  const email = values.email ?? '';
  const password = values.password ?? '';
  const name = values.name ?? '';
  const code = values.code ?? '';
  const recoveryToken = values.recoveryToken ?? '';
  const newPassword = values.newPassword ?? '';
  const confirmPassword = values.confirmPassword ?? '';
  const setEmail = (value: string) => setValue('email', value);
  const setPassword = (value: string) => setValue('password', value);
  const setName = (value: string) => setValue('name', value);
  const setCode = (value: string) => setValue('code', value);
  const setRecoveryToken = (value: string) => setValue('recoveryToken', value);
  const setNewPassword = (value: string) => setValue('newPassword', value);
  const setConfirmPassword = (value: string) => setValue('confirmPassword', value);
  const [showPassword, setShowPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const [verifyingCode, setVerifyingCode] = useState(false);
  const [formError, setFormError] = useState('');
  const requestPending = useRef(false);

  // Control and feedback state
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [verificationError, setVerificationError] = useState('');
  const [recoveryCodeError, setRecoveryCodeError] = useState('');
  const formVersion = useRef(0);
  const formActive = useRef(true);
  const otpRequestPending = useRef(false);
  const verificationAttempt = useRef<string | null>(null);
  const recoveryAttempt = useRef<string | null>(null);

  const resetForm = () => {
    formVersion.current += 1;
    otpRequestPending.current = false;
    verificationAttempt.current = null;
    recoveryAttempt.current = null;
    form.reset(EMPTY_FORM);
    requestPending.current = false;
    setFormError('');
    setShowPassword(false);
    setVerificationError('');
    setRecoveryCodeError('');
    setShowNewPassword(false);
    setShowConfirmPassword(false);
    setCooldown(0);
    setResendCooldown(0);
    setLoading(false);
    setResending(false);
    setVerifyingCode(false);
  };

  const goTo = (nextMode: AuthMode) => {
    resetForm();
    setMode(nextMode);
  };

  // Invalidates pending responses when the screen is removed.
  useEffect(() => {
    formActive.current = true;
    return () => {
      formActive.current = false;
      formVersion.current += 1;
    };
  }, []);

  // Counts down the resend cooldown on the OTP step
  useEffect(() => {
    if (resendCooldown <= 0) return;
    const id = setInterval(() => setResendCooldown((prev) => (prev <= 1 ? 0 : prev - 1)), 1000);
    return () => clearInterval(id);
  }, [resendCooldown]);

  // Syncs navigation params when they change externally.
  useEffect(() => {
    if (route.params?.mode) {
      goTo(route.params.mode);
    }
    if (route.params?.email) {
      setEmail(route.params.email);
    }
  }, [route.params]);

  // Timer for OTP code resend
  useEffect(() => {
    let timer: any;
    if (cooldown > 0) {
      timer = setInterval(() => {
        setCooldown((prev) => prev - 1);
      }, 1000);
    }
    return () => {
      if (timer) clearInterval(timer);
    };
  }, [cooldown]);

  const fieldError = (field: keyof AuthFormValues, message: string) => {
    setError(field, { type: 'validate', message });
  };

  const requireValidEmail = () => {
    if (isValidEmail(email)) return true;
    const message = email.trim() ? 'Informe um endereço de e-mail válido.' : 'Informe o e-mail.';
    fieldError('email', message);
    if (mode === 'otp' || mode === 'reset') setFormError(message);
    return false;
  };

  const requireFields = (fields: (keyof AuthFormValues)[]) => {
    let valid = true;
    for (const field of fields) {
      if (!(form.getValues(field) ?? '').trim()) {
        fieldError(field, 'Campo obrigatório.');
        valid = false;
      }
    }
    return valid;
  };

  const applyServerErrors = (data: unknown, fallback: string) => {
    if (data && typeof data === 'object' && 'errors' in data && data.errors && typeof data.errors === 'object') {
      for (const field of ['name', 'email', 'password', 'newPassword'] as const) {
        const message = (data.errors as Record<string, unknown>)[field];
        if (typeof message === 'string') setError(field, { type: 'server', message });
      }
    }
    setFormError(fallback);
  };

  // API actions

  const enterSession = (session: AuthSession) => {
    signIn(session);
    goTo('login');
    navigation.reset({ index: 0, routes: [{ name: 'Main', params: { role: session.role } }] });
  };

  const handleLogin = async () => {
    if (!formActive.current || requestPending.current) return;
    setFormError('');
    if (!requireFields(['email', 'password'])) return;

    if (!requireValidEmail()) return;

    const version = formVersion.current;
    requestPending.current = true;
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: normalizeEmail(email), password }),
      });

      if (!res.ok) {
        const text = await res.text();
        if (version !== formVersion.current) return;
        try {
          const errorJson = JSON.parse(text);
          if (res.status === 429) {
            setFormError(typeof errorJson?.message === 'string' && errorJson.message.trim()
              ? errorJson.message : 'Muitas tentativas. Aguarde alguns minutos antes de tentar novamente.');
            return;
          }
          if (EMAIL_NOT_VERIFIED_CODES.includes(errorJson.error)) {
            showAlert('Confirmação Necessária', errorJson.message || 'Verifique seu e-mail para ativar sua conta.');
            setPassword('');
            setMode('verify');
            return;
          }
          applyServerErrors(errorJson, res.status >= 500 ? 'Não foi possível entrar. Tente novamente.' : 'E-mail ou senha incorretos.');
        } catch {
          if (res.status === 429) {
            setFormError('Muitas tentativas. Aguarde alguns minutos antes de tentar novamente.');
            return;
          }
          if (text.includes('não verificada') || text.includes('pendente')) {
            showAlert('Confirmação Necessária', text);
            setPassword('');
            setMode('verify');
            return;
          }
          setFormError(res.status >= 500 ? 'Não foi possível entrar. Tente novamente.' : 'E-mail ou senha incorretos.');
        }
        return;
      }

      const data = await res.json();
      if (version !== formVersion.current) return;
      if (!data || typeof data.token !== 'string' || !data.token
          || !['USER', 'ADMIN', 'SYSADMIN'].includes(data.role)) {
        setFormError('Não foi possível confirmar o acesso. Tente novamente.');
        return;
      }
      enterSession({
        token: data.token,
        role: data.role,
        name: data.name,
        email: data.email,
        userId: data.userId,
        emailVerified: data.emailVerified,
      });
    } catch {
      if (version !== formVersion.current) return;
      setFormError('Não foi possível conectar. Verifique sua conexão e tente novamente.');
    } finally {
      if (version === formVersion.current) { requestPending.current = false; setLoading(false); }
    }
  };

  const handleRegister = async () => {
    if (!formActive.current || requestPending.current) return;
    setFormError('');
    const cleanName = normalizeName(name);
    if (!requireFields(['name', 'email', 'password'])) return;

    if (!isValidName(cleanName)) {
      fieldError('name', 'O nome deve ter entre 2 e 100 caracteres.');
      return;
    }

    if (!requireValidEmail()) return;

    if (!PASSWORD_REGEX.test(password)) {
      fieldError('password', 'Use pelo menos 6 caracteres, com maiúscula, minúscula e número.');
      return;
    }

    const version = formVersion.current;
    requestPending.current = true;
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: cleanName,
          email: normalizeEmail(email),
          password,
        }),
      });

      let data: any = {};
      const resText = await res.text();
      if (version !== formVersion.current) return;
      try {
        data = JSON.parse(resText);
      } catch {
        data = { message: resText };
      }

      if (!res.ok) {
        if (res.status === 409) {
          confirmAction(
            'Conta Já Cadastrada',
            data.message || 'Este e-mail já está cadastrado no sistema. Deseja fazer login?',
            () => { if (version === formVersion.current) goTo('login'); },
            'Fazer Login'
          );
          return;
        }
        applyServerErrors(data, res.status >= 500 ? 'Não foi possível realizar o cadastro. Tente novamente.' : data.message || 'Não foi possível realizar o cadastro.');
        return;
      }

      setCode('');
      showAlert('Código enviado', 'Verifique o seu e-mail.');
      setName('');
      setPassword('');
      setMode('verify');
    } catch {
      if (version !== formVersion.current) return;
      setFormError('Não foi possível conectar. Verifique sua conexão e tente novamente.');
    } finally {
      if (version === formVersion.current) { requestPending.current = false; setLoading(false); }
    }
  };

  const handleVerify = async (enteredCode = code) => {
    if (!formActive.current || otpRequestPending.current) return;
    if (!email.trim() || !enteredCode.trim()) {
      if (!email.trim()) fieldError('email', 'Informe o e-mail.');
      setVerificationError('Informe o código de confirmação.');
      return;
    }

    if (!requireValidEmail()) return;

    if (!isValidOtp(enteredCode)) {
      setVerificationError('Digite o código de 6 dígitos numéricos recebido por e-mail.');
      return;
    }

    const version = formVersion.current;
    const sessionVersion = getSession().version;
    const isCurrentVerification = () => formActive.current && version === formVersion.current
      && getSession().version === sessionVersion;
    otpRequestPending.current = true;
    verificationAttempt.current = enteredCode;
    setVerificationError('');
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/verify-email`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: normalizeEmail(email),
          code: enteredCode,
        }),
      });

      const data = await res.json();
      if (!isCurrentVerification()) return;
      if (!res.ok) {
        throw new Error(data.message || 'Código de verificação incorreto ou expirado.');
      }

      if (!data || data.success !== true || data.emailVerified !== true
          || typeof data.token !== 'string' || !data.token.trim()
          || !['USER', 'ADMIN', 'SYSADMIN'].includes(data.role)
          || typeof data.name !== 'string' || typeof data.userId !== 'number'
          || data.email !== normalizeEmail(email)) {
        goTo('login');
        showAlert('Confirme seu acesso', 'Não foi possível iniciar a sessão. Entre com seu e-mail e senha.');
        return;
      }
      enterSession({ token: data.token, role: data.role, name: data.name, email: data.email,
        userId: data.userId, emailVerified: true });
    } catch (err: any) {
      if (!isCurrentVerification()) return;
      setVerificationError(err.message || 'Não foi possível verificar o código.');
    } finally {
      if (isCurrentVerification()) {
        otpRequestPending.current = false;
        setLoading(false);
      }
    }
  };

  const handleVerificationCodeChange = (text: string) => {
    if (!formActive.current || otpRequestPending.current) return;
    const nextCode = normalizeOtp(text);
    setCode(nextCode);
    if (nextCode !== code) setVerificationError('');
    if (!isValidOtp(nextCode)) {
      verificationAttempt.current = null;
    } else if (isValidEmail(email) && verificationAttempt.current !== nextCode) {
      return handleVerify(nextCode);
    }
  };

  const handleResendVerification = async () => {
    if (!formActive.current || otpRequestPending.current || cooldown > 0) return;
    if (!email.trim()) {
      fieldError('email', 'Informe o e-mail para receber um novo código.');
      return;
    }

    if (!requireValidEmail()) return;

    const version = formVersion.current;
    otpRequestPending.current = true;
    setResending(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/resend-verification`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: normalizeEmail(email) }),
      });

      const data = await res.json();
      if (version !== formVersion.current) return;
      if (!res.ok) {
        throw new Error(data.message || 'Não foi possível reenviar o código.');
      }

      setCooldown(60);
      setCode('');
      setVerificationError('');
      verificationAttempt.current = null;
      showAlert('Código enviado', 'Verifique o seu e-mail.');
    } catch (err: any) {
      if (version !== formVersion.current) return;
      showAlert('Erro ao Reenviar', err.message || 'Falha ao solicitar novo código.');
    } finally {
      if (version === formVersion.current) {
        otpRequestPending.current = false;
        setResending(false);
      }
    }
  };

  const handleRequestRecovery = async () => {
    if (!formActive.current || otpRequestPending.current) return;
    if (!email.trim()) {
      fieldError('email', 'Informe o e-mail cadastrado na sua conta.');
      return;
    }

    if (!requireValidEmail()) return;

    const version = formVersion.current;
    otpRequestPending.current = true;
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/forgot-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: normalizeEmail(email) }),
      });

      const data = await res.json();
      if (version !== formVersion.current) return;
      if (!res.ok) {
        applyServerErrors(data, 'Não foi possível solicitar a recuperação. Tente novamente.');
        return;
      }
      showAlert('Código enviado', 'Verifique o seu e-mail.');
      setRecoveryToken('');
      setRecoveryCodeError('');
      recoveryAttempt.current = null;
      setResendCooldown(60);
      setMode('otp');
    } catch {
      if (version !== formVersion.current) return;
      setFormError('Não foi possível solicitar a recuperação. Tente novamente mais tarde.');
    } finally {
      if (version === formVersion.current) {
        otpRequestPending.current = false;
        setLoading(false);
      }
    }
  };

  const handleVerifyRecoveryCode = async (enteredCode = recoveryToken) => {
    if (!formActive.current || otpRequestPending.current) return;
    if (!requireValidEmail()) return;
    if (!isValidOtp(enteredCode)) {
      setRecoveryCodeError('Digite o código de 6 dígitos numéricos recebido por e-mail.');
      return;
    }

    const version = formVersion.current;
    otpRequestPending.current = true;
    recoveryAttempt.current = enteredCode;
    setRecoveryCodeError('');
    setVerifyingCode(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/verify-recovery-code`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: normalizeEmail(email),
          token: enteredCode,
        }),
      });

      const data = await res.json();
      if (version !== formVersion.current) return;
      if (!res.ok) {
        throw new Error(data.message || 'Código inválido ou expirado.');
      }

      setNewPassword('');
      setConfirmPassword('');
      setMode('reset');
    } catch (err: any) {
      if (version !== formVersion.current) return;
      setRecoveryCodeError(err.message || 'Não foi possível validar o código.');
    } finally {
      if (version === formVersion.current) {
        otpRequestPending.current = false;
        setVerifyingCode(false);
      }
    }
  };

  const handleRecoveryCodeChange = (text: string) => {
    if (!formActive.current || otpRequestPending.current) return;
    const nextCode = normalizeOtp(text);
    setRecoveryToken(nextCode);
    if (nextCode !== recoveryToken) setRecoveryCodeError('');
    if (!isValidOtp(nextCode)) {
      recoveryAttempt.current = null;
    } else if (isValidEmail(email) && recoveryAttempt.current !== nextCode) {
      return handleVerifyRecoveryCode(nextCode);
    }
  };

  // Sends the code again and restarts the 60s cooldown
  const handleResendRecoveryCode = async () => {
    if (resendCooldown > 0) return;
    await handleRequestRecovery();
  };

  // Goes back to the e-mail step when the address was typed wrong
  const handleChangeRecoveryEmail = () => {
    resetForm();
    setEmail(email);
    setMode('forgot');
  };

  const returnToRecoveryCode = () => {
    formVersion.current += 1;
    requestPending.current = false;
    otpRequestPending.current = false;
    setLoading(false);
    setNewPassword('');
    setConfirmPassword('');
    setShowNewPassword(false);
    setShowConfirmPassword(false);
    clearErrors();
    setFormError('');
    setMode('otp');
  };

  const handleResetPassword = async () => {
    if (!formActive.current || requestPending.current) return;
    setFormError('');
    if (!email.trim() || !recoveryToken.trim()) {
      setFormError('Informe o e-mail e o código recebido antes de redefinir a senha.');
      return;
    }

    if (!isValidOtp(recoveryToken)) {
      setFormError('Digite o código de 6 dígitos numéricos recebido por e-mail.');
      return;
    }

    if (!PASSWORD_REGEX.test(newPassword)) {
      fieldError('newPassword', 'Use pelo menos 6 caracteres, com maiúscula, minúscula e número.');
      return;
    }

    if (newPassword !== confirmPassword) {
      fieldError('confirmPassword', 'A confirmação não coincide com a nova senha.');
      return;
    }

    const version = formVersion.current;
    requestPending.current = true;
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/reset-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: normalizeEmail(email),
          token: recoveryToken,
          newPassword,
        }),
      });

      const data = await res.json();
      if (version !== formVersion.current) return;
      if (!res.ok) {
        throw new Error(data.message || 'Código de recuperação inválido ou expirado.');
      }

      showAlert('Sucesso', data.message || 'Senha alterada com sucesso! Você já pode entrar.');
      goTo('login');
    } catch (err: any) {
      if (version !== formVersion.current) return;
      setFormError(err.message || 'Não foi possível redefinir sua senha.');
    } finally {
      if (version === formVersion.current) { requestPending.current = false; setLoading(false); }
    }
  };

  const busy = loading || resending || verifyingCode;
  const primaryText = colors.isDark ? '#9FA8DA' : colors.primary;

  const renderField = (field: 'email' | 'name' | 'password' | 'newPassword' | 'confirmPassword', label: string, placeholder: string) => {
    const passwordField = field === 'password' || field === 'newPassword' || field === 'confirmPassword';
    const visible = field === 'password' ? showPassword : field === 'newPassword' ? showNewPassword : showConfirmPassword;
    const toggle = () => {
      if (field === 'password') setShowPassword(!showPassword);
      else if (field === 'newPassword') setShowNewPassword(!showNewPassword);
      else setShowConfirmPassword(!showConfirmPassword);
    };
    return (
      <Controller key={field} control={control} name={field}
        rules={{
          required: 'Campo obrigatório.',
          validate: value => {
            if (field === 'email') return isValidEmail(value) || 'Informe um endereço de e-mail válido.';
            if (field === 'name') return isValidName(value) || 'O nome deve ter entre 2 e 100 caracteres.';
            if (field === 'password' && mode === 'login') return true;
            if (field === 'confirmPassword') return value === form.getValues('newPassword') || 'As senhas não coincidem.';
            return !passwordField || PASSWORD_REGEX.test(value) || 'Use pelo menos 6 caracteres, com maiúscula, minúscula e número.';
          },
        }}
        render={({ field: controlled }) => {
          const props = {
            label, placeholder, required: true, editable: !busy,
            value: controlled.value, onBlur: controlled.onBlur,
            error: errors[field]?.message,
            onChangeText: (value: string) => {
              if (requestPending.current || otpRequestPending.current) return;
              controlled.onChange(value);
              clearErrors(field);
              setFormError('');
            },
          };
          return passwordField
            ? <PasswordInput {...props} ref={controlled.ref} visible={visible} onToggleVisibility={toggle} />
            : <FormField {...props} ref={controlled.ref} icon={field === 'email' ? 'mail-outline' : 'person-outline'}
                autoCapitalize={field === 'email' ? 'none' : 'words'} autoCorrect={false}
                keyboardType={field === 'email' ? 'email-address' : 'default'} />;
        }}
      />
    );
  };

  const feedback = () => formError ? (
    <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={[styles.error, { color: colors.danger }]}>{formError}</Text>
  ) : null;
  const link = (label: string, action: () => void, disabled = false) => (
    <TouchableOpacity onPress={action} disabled={disabled} accessibilityRole="button"
      accessibilityState={{ disabled }} style={styles.link}>
      <Text style={[styles.linkText, { color: disabled ? colors.subtext : primaryText }]}>{label}</Text>
    </TouchableOpacity>
  );

  // The Android back button does what the arrow at the top of each step does, instead of leaving the app.
  const backAction = mode === 'login' ? null : mode === 'otp' ? handleChangeRecoveryEmail
    : mode === 'reset' ? returnToRecoveryCode : () => goTo('login');
  const backRef = useRef(backAction);
  backRef.current = backAction;
  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (!backRef.current) return false;
      backRef.current();
      return true;
    });
    return () => subscription.remove();
  }, []);

  // Keeps the e-mail already typed on the login when the person decides to create an account.
  const openRegister = () => {
    const typedEmail = email.trim();
    goTo('register');
    if (typedEmail) setEmail(typedEmail);
  };

  const renderLogin = () => (
    <AuthLayout key={mode} title="SpotMeet" subtitle="Conecte-se com sua equipe em qualquer lugar"
      variant="login" cardTitle="Acessar conta" cardSubtitle="Entre com seu e-mail e senha.">
      {renderField('email', 'E-mail', 'E-mail')}
      {renderField('password', 'Senha', 'Senha')}
      {feedback()}
      {link('Esqueci minha senha', () => goTo('forgot'))}
      <FormButton label="Entrar no SpotMeet" icon="log-in-outline" onPress={handleLogin} loading={loading} />
      <View style={styles.google}>
        <TouchableOpacity disabled accessibilityRole="button" accessibilityState={{ disabled: true }}
          accessibilityLabel="Continuar com Google, em breve" style={[styles.googleButton, { borderColor: colors.border }]}>
          <Ionicons name="logo-google" size={20} color={colors.subtext} />
          <Text style={[styles.googleText, { color: colors.subtext }]}>Continuar com Google</Text>
          <Text style={[styles.badge, { color: colors.subtext, backgroundColor: colors.inputBg }]}>EM BREVE</Text>
        </TouchableOpacity>
      </View>
      {link('Não tem uma conta? Cadastre-se', openRegister)}
      <ServerSettings />
    </AuthLayout>
  );

  const renderRegister = () => (
    <AuthLayout key={mode} title="SpotMeet" subtitle="Organize suas reuniões em um só lugar"
      onBack={() => goTo('login')} cardTitle="Criar conta"
      cardSubtitle="Você confirma seu e-mail por código antes de entrar.">
      {renderField('name', 'Nome completo', 'Nome completo')}
      {renderField('email', 'E-mail', 'E-mail')}
      {renderField('password', 'Senha', 'Senha')}
      <PasswordRules password={password} />
      {feedback()}
      <View style={styles.actions}>
        <FormButton label="Criar Conta" icon="person-add-outline" onPress={handleRegister} loading={loading} />
        {link('Já tem uma conta? Entrar', () => goTo('login'))}
      </View>
    </AuthLayout>
  );

  const renderVerify = () => (
    <AuthLayout key={mode} title="SpotMeet" subtitle="Validação de acesso"
      onBack={() => goTo('login')} cardTitle="Confirme seu e-mail"
      cardSubtitle="Digite o código de 6 dígitos enviado para o e-mail cadastrado.">
      {renderField('email', 'E-mail cadastrado', 'seu.email@exemplo.com')}
      <OtpInput label="Código de confirmação" value={code}
        editable={!busy} error={verificationError} onChangeText={handleVerificationCodeChange} />
      {feedback()}
      <View style={styles.actions}>
        <FormButton label="Confirmar e Ativar Conta" icon="shield-checkmark-outline" onPress={() => handleVerify()} loading={loading} disabled={resending} />
        <FormButton label={cooldown > 0 ? `Reenviar código em ${cooldown}s` : 'Reenviar código por E-mail'}
          icon="refresh-outline" secondary onPress={handleResendVerification}
          disabled={cooldown > 0 || busy} loading={resending} />
        {link('Voltar ao Login', () => goTo('login'))}
      </View>
    </AuthLayout>
  );

  const renderForgot = () => (
    <AuthLayout key={mode} title="SpotMeet" subtitle="Recuperação protegida" step={1}
      onBack={() => goTo('login')} cardTitle="Esqueci minha senha"
      cardSubtitle="Etapa 1 de 3. Informe seu e-mail para receber um código.">
      {renderField('email', 'E-mail cadastrado', 'seu.email@exemplo.com')}
      <Text style={[styles.info, { color: colors.subtext, backgroundColor: colors.itemBg }]}>
        Informe seu e-mail e verifique as instruções recebidas.
      </Text>
      {feedback()}
      <View style={styles.actions}>
        <FormButton label="Enviar Código por E-mail" icon="paper-plane-outline" onPress={handleRequestRecovery} loading={loading} />
        {link('Voltar ao Login', () => goTo('login'))}
      </View>
    </AuthLayout>
  );

  const renderOtp = () => (
    <AuthLayout key={mode} title="SpotMeet" subtitle="Validar identidade" step={2}
      onBack={handleChangeRecoveryEmail} cardTitle="Confirmar código"
      cardSubtitle="Etapa 2 de 3. Digite o código recebido por e-mail.">
      <Text style={[styles.hint, { color: colors.subtext }]}>{normalizeEmail(email)}</Text>
      <OtpInput label="Código de verificação" hint="Digite o código recebido"
        value={recoveryToken} editable={!busy} error={recoveryCodeError} onChangeText={handleRecoveryCodeChange} />
      {feedback()}
      <View style={styles.actions}>
        <FormButton label="Validar Código" icon="checkmark-circle-outline" onPress={() => handleVerifyRecoveryCode()} loading={verifyingCode} disabled={loading} />
        {link(resendCooldown > 0 ? `Reenviar código em ${resendCooldown}s` : 'Não recebeu? Reenviar código',
          handleResendRecoveryCode, resendCooldown > 0 || busy)}
        {link('E-mail errado? Alterar e-mail', handleChangeRecoveryEmail)}
      </View>
    </AuthLayout>
  );

  const renderReset = () => (
    <AuthLayout key={mode} title="SpotMeet" subtitle="Redefinição segura" step={3}
      onBack={returnToRecoveryCode} cardTitle="Criar nova senha"
      cardSubtitle="Etapa 3 de 3. Defina e confirme a nova senha.">
      {renderField('newPassword', 'Nova senha', 'Digite sua nova senha')}
      {renderField('confirmPassword', 'Confirmar nova senha', 'Repita a nova senha')}
      <PasswordRules password={newPassword} confirmation={confirmPassword} />
      {feedback()}
      <View style={styles.actions}>
        <FormButton label="Atualizar Senha" icon="shield-checkmark-outline" onPress={handleResetPassword} loading={loading} />
        {link('Voltar ao código de verificação', returnToRecoveryCode)}
      </View>
    </AuthLayout>
  );

  switch (mode) {
    case 'register':
      return renderRegister();
    case 'verify':
      return renderVerify();
    case 'forgot':
      return renderForgot();
    case 'otp':
      return renderOtp();
    case 'reset':
      return renderReset();
    case 'login':
    default:
      return renderLogin();
  }
}

const styles = StyleSheet.create({
  actions: { flexGrow: 1, justifyContent: 'flex-end', paddingTop: 14 },
  link: { minHeight: 48, justifyContent: 'center', alignItems: 'center', paddingVertical: 8 },
  linkText: { fontSize: 13, lineHeight: 18, fontWeight: '600', textAlign: 'center' },
  error: { fontSize: 13, lineHeight: 18, marginBottom: 8 },
  hint: { fontSize: 14, lineHeight: 20, textAlign: 'center' },
  info: { padding: 14, borderRadius: 12, fontSize: 13, lineHeight: 19, marginTop: 8 },
  google: { marginTop: 8 },
  googleButton: { minHeight: 48, borderWidth: 1, borderRadius: 14, paddingHorizontal: 10, flexDirection: 'row', alignItems: 'center', gap: 7 },
  googleText: { flex: 1, fontSize: 13, fontWeight: '600' },
  badge: { fontSize: 9, fontWeight: '700', paddingHorizontal: 5, paddingVertical: 4, borderRadius: 5 },
});
