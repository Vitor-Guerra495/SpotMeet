import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { API_BASE_URL } from '../config/api';
import { showAlert, confirmAction } from '../utils/alerts';
import { PASSWORD_REGEX, checkPasswordRules, isValidEmail } from '../utils/validators';
import AuthLayout from '../components/auth/AuthLayout';
import DevOtpBanner from '../components/auth/DevOtpBanner';
import ServerSettings from '../components/auth/ServerSettings';

export type AuthMode = 'login' | 'register' | 'verify' | 'forgot' | 'otp' | 'reset';

// Error codes returned by the login endpoint when the account is not verified yet
const EMAIL_NOT_VERIFIED_CODES = ['EMAIL_NOT_VERIFIED'];

export default function AuthScreen({ route, navigation }: any) {
  const { signIn } = useAuth();
  const { colors } = useTheme();

  // Initial mode from the route param, defaults to 'login'
  const initialRouteMode: AuthMode = route.params?.mode || 'login';
  const [mode, setMode] = useState<AuthMode>(initialRouteMode);

  // Form state
  const [email, setEmail] = useState(route.params?.email || '');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [code, setCode] = useState(route.params?.devCode || '');
  const [devCode, setDevCode] = useState(route.params?.devCode || '');
  const [recoveryToken, setRecoveryToken] = useState(route.params?.token || '');
  // Three-step recovery: independent eyes per field and a 60s resend cooldown
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const [verifyingCode, setVerifyingCode] = useState(false);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // Control and feedback state
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [passwordError, setPasswordError] = useState('');

  // Syncs navigation params when they change externally
  // Counts down the resend cooldown on the OTP step
  useEffect(() => {
    if (resendCooldown <= 0) return;
    const id = setInterval(() => setResendCooldown((prev) => (prev <= 1 ? 0 : prev - 1)), 1000);
    return () => clearInterval(id);
  }, [resendCooldown]);

  useEffect(() => {
    if (route.params?.mode) {
      setMode(route.params.mode);
    }
    if (route.params?.email) {
      setEmail(route.params.email);
    }
    if (route.params?.devCode) {
      setDevCode(route.params.devCode);
      setCode(route.params.devCode);
    }
    if (route.params?.token) {
      setRecoveryToken(route.params.token);
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

  // Real-time password validation on register
  const handleRegisterPasswordChange = (value: string) => {
    setPassword(value);
    if (value.length > 0 && !PASSWORD_REGEX.test(value)) {
      setPasswordError('Mín. 6 caracteres com maiúscula, minúscula e número.');
    } else {
      setPasswordError('');
    }
  };

  // API actions

  const handleLogin = async () => {
    if (!email.trim() || !password.trim()) {
      showAlert('Atenção', 'Preencha o e-mail e a senha.');
      return;
    }

    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.trim().toLowerCase(), password }),
      });

      if (!res.ok) {
        const text = await res.text();
        try {
          const errorJson = JSON.parse(text);
          if (EMAIL_NOT_VERIFIED_CODES.includes(errorJson.error)) {
            showAlert('Confirmação Necessária', errorJson.message || 'Verifique seu e-mail para ativar sua conta.');
            setMode('verify');
            return;
          }
          showAlert('Erro no Acesso', errorJson.message || text);
        } catch {
          if (text.includes('não verificada') || text.includes('pendente')) {
            showAlert('Confirmação Necessária', text);
            setMode('verify');
            return;
          }
          showAlert('Erro no Acesso', text || 'E-mail ou senha incorretos.');
        }
        return;
      }

      const data = await res.json();
      signIn({
        token: data.token,
        role: data.role,
        name: data.name,
        email: data.email,
        userId: data.userId,
        emailVerified: data.emailVerified,
      });
      navigation.navigate('Main', { role: data.role });
    } catch {
      showAlert('Erro de Conexão', 'Não foi possível conectar ao servidor. Verifique se o backend está rodando.');
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async () => {
    if (!name.trim() || !email.trim() || !password) {
      showAlert('Atenção', 'Preencha Nome, E-mail e Senha.');
      return;
    }

    if (!isValidEmail(email)) {
      showAlert('E-mail Inválido', 'Por favor, informe um endereço de e-mail válido.');
      return;
    }

    if (!PASSWORD_REGEX.test(password)) {
      showAlert('Senha Inválida', 'A senha deve ter no mínimo 6 caracteres, com pelo menos uma letra maiúscula, uma minúscula e um número.');
      return;
    }

    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: name.trim(),
          email: email.trim().toLowerCase(),
          password,
        }),
      });

      let data: any = {};
      const resText = await res.text();
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
            () => setMode('login'),
            'Fazer Login'
          );
          return;
        }
        showAlert('Erro no Cadastro', data.message || 'Não foi possível realizar o cadastro.');
        return;
      }

      if (data.devCode) {
        setDevCode(data.devCode);
        setCode(data.devCode);
      }

      let msg = data.message || 'Cadastro realizado! Enviamos um código de confirmação para o seu e-mail.';
      if (data.devCode) {
        msg += `\n\nCódigo de Teste (Dev): ${data.devCode}`;
      }

      showAlert('Quase pronto!', msg);
      setMode('verify');
    } catch {
      showAlert('Erro de Conexão', 'Não foi possível conectar ao servidor. Verifique se o backend está rodando.');
    } finally {
      setLoading(false);
    }
  };

  const handleVerify = async () => {
    if (!email.trim() || !code.trim()) {
      showAlert('Campo Obrigatório', 'Informe seu e-mail e o código de confirmação.');
      return;
    }

    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/verify-email`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: email.trim().toLowerCase(),
          code: code.trim(),
        }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Código de verificação incorreto ou expirado.');
      }

      showAlert('Sucesso', data.message || 'Conta ativada com sucesso! Você já pode entrar.');
      setPassword('');
      setMode('login');
    } catch (err: any) {
      showAlert('Erro na Validação', err.message || 'Não foi possível verificar o código.');
    } finally {
      setLoading(false);
    }
  };

  const handleResendVerification = async () => {
    if (!email.trim()) {
      showAlert('Atenção', 'Informe seu e-mail para receber um novo código.');
      return;
    }

    setResending(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/resend-verification`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.trim().toLowerCase() }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Não foi possível reenviar o código.');
      }

      setCooldown(60);
      if (data.devCode) {
        setDevCode(data.devCode);
        setCode(data.devCode);
      }

      let msg = data.message || 'Um novo código de verificação foi enviado para o seu e-mail.';
      if (data.devCode) {
        msg += `\n\nCódigo de Teste (Dev): ${data.devCode}`;
      }
      showAlert('Código Reenviado', msg);
    } catch (err: any) {
      showAlert('Erro ao Reenviar', err.message || 'Falha ao solicitar novo código.');
    } finally {
      setResending(false);
    }
  };

  const handleRequestRecovery = async () => {
    if (!email.trim()) {
      showAlert('Atenção', 'Informe o e-mail cadastrado na sua conta.');
      return;
    }

    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/forgot-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.trim().toLowerCase() }),
      });

      const data = await res.json();
      if (data.devCode) {
        setRecoveryToken(data.devCode);
      }

      let msg = data.message || 'Se a conta estiver cadastrada, o código de recuperação foi enviado para o seu e-mail.';
      if (data.devCode) {
        msg += `\n\nCódigo OTP Gerado (Dev): ${data.devCode}`;
      }

      showAlert('Instruções Enviadas', msg);
      setRecoveryToken(data.devCode || '');
      setResendCooldown(60);
      setMode('otp');
    } catch {
      showAlert('Erro', 'Não foi possível solicitar a recuperação. Tente novamente mais tarde.');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyRecoveryCode = async () => {
    if (!recoveryToken.trim()) {
      showAlert('Atenção', 'Informe o código recebido por e-mail.');
      return;
    }

    setVerifyingCode(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/verify-recovery-code`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: email.trim().toLowerCase(),
          token: recoveryToken.trim(),
        }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Código inválido ou expirado.');
      }

      setNewPassword('');
      setConfirmPassword('');
      setMode('reset');
    } catch (err: any) {
      showAlert('Código Inválido', err.message || 'Não foi possível validar o código.');
    } finally {
      setVerifyingCode(false);
    }
  };

  // Sends the code again and restarts the 60s cooldown
  const handleResendRecoveryCode = async () => {
    if (resendCooldown > 0) return;
    await handleRequestRecovery();
  };

  // Goes back to the e-mail step when the address was typed wrong
  const handleChangeRecoveryEmail = () => {
    setRecoveryToken('');
    setResendCooldown(0);
    setMode('forgot');
  };

  const handleResetPassword = async () => {
    if (!email.trim() || !recoveryToken.trim()) {
      showAlert('Atenção', 'Informe o e-mail e o código OTP recebido.');
      return;
    }

    if (!PASSWORD_REGEX.test(newPassword)) {
      showAlert('Senha Fraca', 'A nova senha deve ter pelo menos 6 caracteres, contendo letra maiúscula, minúscula e número.');
      return;
    }

    if (newPassword !== confirmPassword) {
      showAlert('Senhas Divergentes', 'A confirmação de senha não coincide com a nova senha.');
      return;
    }

    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/auth/reset-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: email.trim().toLowerCase(),
          token: recoveryToken.trim(),
          newPassword,
        }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Código de recuperação inválido ou expirado.');
      }

      showAlert('Sucesso', data.message || 'Senha alterada com sucesso! Você já pode entrar.');
      setPassword('');
      setMode('login');
    } catch (err: any) {
      showAlert('Erro', err.message || 'Não foi possível redefinir sua senha.');
    } finally {
      setLoading(false);
    }
  };

  // Sub-view renderers

  // Sub-view: LOGIN
  const renderLogin = () => (
    <AuthLayout
      title="SpotMeet"
      subtitle="Conecte-se com sua equipe em qualquer lugar"
      cardTitle="Acessar Conta"
    >
      <TextInput
        style={[styles.input, { backgroundColor: colors.inputBg, color: colors.text, borderColor: colors.border }]}
        placeholder="E-mail"
        placeholderTextColor={colors.subtext}
        keyboardType="email-address"
        autoCapitalize="none"
        autoCorrect={false}
        value={email}
        onChangeText={setEmail}
      />

      <TextInput
        style={[styles.input, { backgroundColor: colors.inputBg, color: colors.text, borderColor: colors.border }]}
        placeholder="Senha"
        placeholderTextColor={colors.subtext}
        secureTextEntry
        value={password}
        onChangeText={setPassword}
      />

      <TouchableOpacity
        style={styles.forgotPasswordLink}
        onPress={() => setMode('forgot')}
      >
        <Text style={[styles.forgotPasswordLinkText, { color: colors.primary }]}>Esqueci minha senha</Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.buttonPrimary, loading && styles.buttonDisabled]}
        onPress={handleLogin}
        disabled={loading}
      >
        {loading ? (
          <ActivityIndicator color="#FFF" />
        ) : (
          <Text style={styles.buttonPrimaryText}>Entrar no SpotMeet</Text>
        )}
      </TouchableOpacity>

      <TouchableOpacity
        style={styles.switchAuthContainer}
        onPress={() => setMode('register')}
      >
        <Text style={[styles.switchAuthText, { color: colors.subtext }]}>
          Não tem uma conta? <Text style={[styles.switchAuthHighlight, { color: colors.primary }]}>Cadastre-se</Text>
        </Text>
      </TouchableOpacity>

      <ServerSettings />
    </AuthLayout>
  );

  // Sub-view: REGISTER
  const renderRegister = () => (
    <AuthLayout
      title="Criar Conta SpotMeet"
      subtitle="Crie sua conta e acesse as organizações após o login"
      cardTitle="Informações do Perfil"
    >
      <TextInput
        style={[styles.input, { backgroundColor: colors.inputBg, color: colors.text, borderColor: colors.border }]}
        placeholder="Nome completo"
        placeholderTextColor={colors.subtext}
        value={name}
        onChangeText={setName}
      />

      <TextInput
        style={[styles.input, { backgroundColor: colors.inputBg, color: colors.text, borderColor: colors.border }]}
        placeholder="E-mail"
        placeholderTextColor={colors.subtext}
        keyboardType="email-address"
        autoCapitalize="none"
        autoCorrect={false}
        value={email}
        onChangeText={setEmail}
      />

      <TextInput
        style={[
          styles.input,
          { backgroundColor: colors.inputBg, color: colors.text, borderColor: passwordError ? colors.danger : colors.border },
        ]}
        placeholder="Senha"
        placeholderTextColor={colors.subtext}
        secureTextEntry
        value={password}
        onChangeText={handleRegisterPasswordChange}
      />
      {passwordError ? <Text style={[styles.errorText, { color: colors.danger }]}>{passwordError}</Text> : null}

      <TouchableOpacity
        style={[styles.buttonPrimary, loading && styles.buttonDisabled]}
        onPress={handleRegister}
        disabled={loading}
      >
        {loading ? (
          <ActivityIndicator color="#FFF" />
        ) : (
          <Text style={styles.buttonPrimaryText}>Criar Conta</Text>
        )}
      </TouchableOpacity>

      <TouchableOpacity
        style={styles.switchAuthContainer}
        onPress={() => setMode('login')}
      >
        <Text style={[styles.switchAuthText, { color: colors.subtext }]}>
          Já tem uma conta? <Text style={[styles.switchAuthHighlight, { color: colors.primary }]}>Entrar</Text>
        </Text>
      </TouchableOpacity>
    </AuthLayout>
  );

  // Sub-view: VERIFY EMAIL
  const renderVerify = () => (
    <AuthLayout
      title="Confirmação de Conta"
      subtitle="Insira o código de confirmação enviado para o seu e-mail para ativar seu acesso."
      cardTitle="Validação de Acesso"
    >
      <Text style={[styles.inputLabel, { color: colors.text }]}>E-mail Cadastrado</Text>
      <TextInput
        style={[styles.input, { backgroundColor: colors.inputBg, color: colors.text, borderColor: colors.border }]}
        placeholder="seu.email@exemplo.com"
        placeholderTextColor={colors.subtext}
        keyboardType="email-address"
        autoCapitalize="none"
        value={email}
        onChangeText={setEmail}
      />

      <DevOtpBanner devCode={devCode} />

      <Text style={[styles.inputLabel, { color: colors.text }]}>Código OTP (6 dígitos)</Text>
      <TextInput
        style={[
          styles.codeInput,
          { backgroundColor: colors.inputBg, color: colors.text, borderColor: colors.border },
        ]}
        placeholder="123456"
        placeholderTextColor={colors.subtext}
        keyboardType="number-pad"
        maxLength={8}
        value={code}
        onChangeText={setCode}
      />

      <TouchableOpacity
        style={[styles.buttonPrimary, loading && styles.buttonDisabled]}
        onPress={handleVerify}
        disabled={loading}
      >
        {loading ? (
          <ActivityIndicator color="#FFF" />
        ) : (
          <>
            <Ionicons name="shield-checkmark" size={18} color="#FFF" style={{ marginRight: 8 }} />
            <Text style={styles.buttonPrimaryText}>Confirmar e Ativar Conta</Text>
          </>
        )}
      </TouchableOpacity>

      <View style={styles.resendContainer}>
        <TouchableOpacity
          onPress={handleResendVerification}
          disabled={cooldown > 0 || resending}
        >
          {resending ? (
            <ActivityIndicator size="small" color={colors.primary} />
          ) : (
            <Text style={[styles.resendText, { color: cooldown > 0 ? colors.subtext : colors.primary }]}>
              {cooldown > 0 ? `Reenviar código em ${cooldown}s` : 'Reenviar código por E-mail'}
            </Text>
          )}
        </TouchableOpacity>
      </View>

      <TouchableOpacity style={styles.backLink} onPress={() => setMode('login')}>
        <Ionicons name="arrow-back" size={16} color={colors.primary} style={{ marginRight: 6 }} />
        <Text style={[styles.backLinkText, { color: colors.primary }]}>Voltar ao Login</Text>
      </TouchableOpacity>
    </AuthLayout>
  );

  // Sub-view: FORGOT PASSWORD
  const renderForgot = () => (
    <AuthLayout
      title="Recuperação de Conta"
      subtitle="Etapa 1 de 3. Insira seu e-mail cadastrado para receber o código de verificação."
      cardTitle="Solicitar Código"
    >
      <Text style={[styles.inputLabel, { color: colors.text }]}>E-mail Cadastrado</Text>
      <TextInput
        style={[styles.input, { backgroundColor: colors.inputBg, color: colors.text, borderColor: colors.border }]}
        placeholder="seu.email@exemplo.com"
        placeholderTextColor={colors.subtext}
        keyboardType="email-address"
        autoCapitalize="none"
        value={email}
        onChangeText={setEmail}
      />

      <TouchableOpacity
        style={[styles.buttonPrimary, loading && styles.buttonDisabled]}
        onPress={handleRequestRecovery}
        disabled={loading}
      >
        {loading ? (
          <ActivityIndicator color="#FFF" />
        ) : (
          <>
            <Ionicons name="mail" size={18} color="#FFF" style={{ marginRight: 8 }} />
            <Text style={styles.buttonPrimaryText}>Enviar Código por E-mail</Text>
          </>
        )}
      </TouchableOpacity>

      <TouchableOpacity style={styles.backLink} onPress={() => setMode('login')}>
        <Ionicons name="arrow-back" size={16} color={colors.primary} style={{ marginRight: 6 }} />
        <Text style={[styles.backLinkText, { color: colors.primary }]}>Voltar ao Login</Text>
      </TouchableOpacity>
    </AuthLayout>
  );

  // Sub-view: RECOVERY CODE (step 2 of 3)
  const renderOtp = () => (
    <AuthLayout
      title="Código de Verificação"
      subtitle="Etapa 2 de 3. Digite o código que enviamos para o seu e-mail."
      cardTitle="Confirmar Código"
    >
      <Text style={[styles.emailHint, { color: colors.subtext }]}>
        Enviado para <Text style={{ color: colors.text, fontWeight: 'bold' }}>{email.trim().toLowerCase()}</Text>
      </Text>

      <DevOtpBanner devCode={recoveryToken} message="Código OTP de recuperação carregado para teste." />

      <Text style={[styles.inputLabel, { color: colors.text }]}>Código de Verificação</Text>
      <TextInput
        style={[
          styles.input,
          styles.tokenInput,
          { backgroundColor: colors.inputBg, color: colors.text, borderColor: colors.border },
        ]}
        placeholder="Digite o código recebido"
        placeholderTextColor={colors.subtext}
        keyboardType="number-pad"
        value={recoveryToken}
        onChangeText={setRecoveryToken}
      />

      <TouchableOpacity
        style={[styles.buttonPrimary, verifyingCode && styles.buttonDisabled]}
        onPress={handleVerifyRecoveryCode}
        disabled={verifyingCode}
      >
        {verifyingCode ? (
          <ActivityIndicator color="#FFF" />
        ) : (
          <>
            <Ionicons name="checkmark-circle" size={18} color="#FFF" style={{ marginRight: 8 }} />
            <Text style={styles.buttonPrimaryText}>Validar Código</Text>
          </>
        )}
      </TouchableOpacity>

      {/* Resend is locked for 60s after each send */}
      <TouchableOpacity
        style={styles.secondaryLink}
        onPress={handleResendRecoveryCode}
        disabled={resendCooldown > 0 || loading}
      >
        <Text
          style={[
            styles.secondaryLinkText,
            { color: resendCooldown > 0 ? colors.subtext : colors.primary },
          ]}
        >
          {resendCooldown > 0
            ? `Reenviar e-mail em ${resendCooldown}s`
            : 'Não recebeu? Reenviar e-mail'}
        </Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.secondaryLink} onPress={handleChangeRecoveryEmail}>
        <Text style={[styles.secondaryLinkText, { color: colors.primary }]}>
          E-mail errado? Alterar e-mail
        </Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.backLink} onPress={() => setMode('login')}>
        <Ionicons name="arrow-back" size={16} color={colors.primary} style={{ marginRight: 6 }} />
        <Text style={[styles.backLinkText, { color: colors.primary }]}>Voltar ao Login</Text>
      </TouchableOpacity>
    </AuthLayout>
  );

  // Sub-view: RESET PASSWORD
  const renderReset = () => {
    const rules = checkPasswordRules(newPassword);
    const passwordsMatch = newPassword.length > 0 && newPassword === confirmPassword;

    return (
      <AuthLayout
        title="Criar Nova Senha"
        subtitle="Etapa 3 de 3. Defina e confirme a sua nova senha."
        cardTitle="Redefinição Segura"
      >
        <Text style={[styles.emailHint, { color: colors.subtext }]}>
          Conta: <Text style={{ color: colors.text, fontWeight: 'bold' }}>{email.trim().toLowerCase()}</Text>
        </Text>

        <Text style={[styles.inputLabel, { color: colors.text }]}>Nova Senha</Text>
        <View style={[styles.passwordWrapper, { backgroundColor: colors.inputBg, borderColor: colors.border }]}>
          <TextInput
            style={[styles.passwordInput, { color: colors.text }]}
            placeholder="Digite sua nova senha"
            placeholderTextColor={colors.subtext}
            secureTextEntry={!showNewPassword}
            value={newPassword}
            onChangeText={setNewPassword}
          />
          <TouchableOpacity onPress={() => setShowNewPassword(!showNewPassword)} style={styles.eyeBtn}>
            <Ionicons name={showNewPassword ? 'eye-off' : 'eye'} size={20} color={colors.subtext} />
          </TouchableOpacity>
        </View>

        <Text style={[styles.inputLabel, { color: colors.text }]}>Confirmar Nova Senha</Text>
        <View style={[styles.passwordWrapper, { backgroundColor: colors.inputBg, borderColor: colors.border }]}>
          <TextInput
            style={[styles.passwordInput, { color: colors.text }]}
            placeholder="Repita a nova senha"
            placeholderTextColor={colors.subtext}
            secureTextEntry={!showConfirmPassword}
            value={confirmPassword}
            onChangeText={setConfirmPassword}
          />
          <TouchableOpacity onPress={() => setShowConfirmPassword(!showConfirmPassword)} style={styles.eyeBtn}>
            <Ionicons name={showConfirmPassword ? 'eye-off' : 'eye'} size={20} color={colors.subtext} />
          </TouchableOpacity>
        </View>

        {/* Visual password security checklist */}
        <View style={[styles.rulesBox, { backgroundColor: colors.itemBg, borderColor: colors.border }]}>
          <Text style={[styles.rulesTitle, { color: colors.text }]}>Requisitos da Nova Senha:</Text>
          <View style={styles.ruleItem}>
            <Ionicons
              name={rules.hasMinLength ? 'checkmark-circle' : 'ellipse-outline'}
              size={14}
              color={rules.hasMinLength ? '#4CAF50' : colors.subtext}
            />
            <Text style={[styles.ruleText, { color: rules.hasMinLength ? '#4CAF50' : colors.subtext }]}>
              Mínimo de 6 caracteres
            </Text>
          </View>
          <View style={styles.ruleItem}>
            <Ionicons
              name={rules.hasUppercase ? 'checkmark-circle' : 'ellipse-outline'}
              size={14}
              color={rules.hasUppercase ? '#4CAF50' : colors.subtext}
            />
            <Text style={[styles.ruleText, { color: rules.hasUppercase ? '#4CAF50' : colors.subtext }]}>
              Pelo menos 1 letra maiúscula
            </Text>
          </View>
          <View style={styles.ruleItem}>
            <Ionicons
              name={rules.hasLowercase ? 'checkmark-circle' : 'ellipse-outline'}
              size={14}
              color={rules.hasLowercase ? '#4CAF50' : colors.subtext}
            />
            <Text style={[styles.ruleText, { color: rules.hasLowercase ? '#4CAF50' : colors.subtext }]}>
              Pelo menos 1 letra minúscula
            </Text>
          </View>
          <View style={styles.ruleItem}>
            <Ionicons
              name={rules.hasNumber ? 'checkmark-circle' : 'ellipse-outline'}
              size={14}
              color={rules.hasNumber ? '#4CAF50' : colors.subtext}
            />
            <Text style={[styles.ruleText, { color: rules.hasNumber ? '#4CAF50' : colors.subtext }]}>
              Pelo menos 1 número
            </Text>
          </View>
          {confirmPassword.length > 0 && (
            <View style={styles.ruleItem}>
              <Ionicons
                name={passwordsMatch ? 'checkmark-circle' : 'close-circle'}
                size={14}
                color={passwordsMatch ? '#4CAF50' : colors.danger}
              />
              <Text style={[styles.ruleText, { color: passwordsMatch ? '#4CAF50' : colors.danger }]}>
                {passwordsMatch ? 'Senhas conferem' : 'Senhas não coincidem'}
              </Text>
            </View>
          )}
        </View>

        <TouchableOpacity
          style={[styles.buttonPrimary, loading && styles.buttonDisabled]}
          onPress={handleResetPassword}
          disabled={loading}
        >
          {loading ? (
            <ActivityIndicator color="#FFF" />
          ) : (
            <>
              <Ionicons name="key" size={18} color="#FFF" style={{ marginRight: 8 }} />
              <Text style={styles.buttonPrimaryText}>Atualizar Senha</Text>
            </>
          )}
        </TouchableOpacity>

        <TouchableOpacity style={styles.secondaryLink} onPress={() => setMode('otp')}>
          <Text style={[styles.secondaryLinkText, { color: colors.primary }]}>
            Voltar ao código de verificação
          </Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.backLink} onPress={() => setMode('login')}>
          <Ionicons name="arrow-back" size={16} color={colors.primary} style={{ marginRight: 6 }} />
          <Text style={[styles.backLinkText, { color: colors.primary }]}>Voltar ao Login</Text>
        </TouchableOpacity>
      </AuthLayout>
    );
  };

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
  input: {
    borderRadius: 10,
    padding: 14,
    fontSize: 14,
    borderWidth: 1,
    marginBottom: 14,
  },
  inputLabel: {
    fontSize: 13,
    fontWeight: 'bold',
    marginBottom: 6,
    marginTop: 4,
  },
  codeInput: {
    borderRadius: 10,
    padding: 14,
    fontSize: 22,
    fontWeight: 'bold',
    letterSpacing: 4,
    textAlign: 'center',
    borderWidth: 1,
    marginBottom: 16,
  },
  emailHint: {
    fontSize: 13,
    marginBottom: 14,
    textAlign: 'center',
  },
  tokenInput: {
    letterSpacing: 2,
    fontWeight: 'bold',
  },
  errorText: {
    fontSize: 12,
    marginBottom: 10,
    marginTop: -8,
  },
  forgotPasswordLink: {
    alignSelf: 'flex-end',
    marginBottom: 18,
    marginTop: -4,
  },
  forgotPasswordLinkText: {
    fontSize: 13,
    fontWeight: '600',
  },
  buttonPrimary: {
    backgroundColor: '#5C6BC0',
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    marginTop: 6,
  },
  buttonDisabled: {
    opacity: 0.65,
  },
  buttonPrimaryText: {
    color: '#FFFFFF',
    fontWeight: 'bold',
    fontSize: 16,
  },
  switchAuthContainer: {
    marginTop: 20,
    alignItems: 'center',
  },
  switchAuthText: {
    fontSize: 14,
  },
  switchAuthHighlight: {
    fontWeight: 'bold',
  },
  resendContainer: {
    marginTop: 16,
    alignItems: 'center',
  },
  resendText: {
    fontSize: 13,
    fontWeight: 'bold',
    padding: 6,
  },
  secondaryLink: {
    marginTop: 16,
    alignItems: 'center',
    paddingVertical: 6,
  },
  secondaryLinkText: {
    fontSize: 13,
    fontWeight: 'bold',
    textAlign: 'center',
  },
  backLink: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 14,
    paddingVertical: 6,
  },
  backLinkText: {
    fontSize: 13,
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
  rulesBox: {
    borderRadius: 8,
    padding: 12,
    marginTop: 4,
    marginBottom: 16,
    borderWidth: 1,
  },
  rulesTitle: {
    fontSize: 12,
    fontWeight: 'bold',
    marginBottom: 6,
  },
  ruleItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 3,
    gap: 6,
  },
  ruleText: {
    fontSize: 12,
  },
});
