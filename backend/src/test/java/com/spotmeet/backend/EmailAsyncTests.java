package com.spotmeet.backend;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.spotmeet.backend.config.EmailAsyncConfig;
import com.spotmeet.backend.controller.AuthController;
import com.spotmeet.backend.dto.*;
import com.spotmeet.backend.model.AuditLog;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.AuditLogRepository;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.service.EmailDeliveryService;
import com.spotmeet.backend.service.EmailService;
import com.spotmeet.backend.service.UserService;
import com.spotmeet.backend.security.AuthenticationAttempts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Real Spring async proxy and transaction callbacks; no database or SMTP connections.
class EmailAsyncTests {
    enum Flow { REGISTER, RESEND, RECOVERY, CURRENT_EMAIL, CONFIRM_NEW_EMAIL, RESEND_NEW_EMAIL }

    private static final String EMAIL = "user@example.com";
    private static final String NEW_EMAIL = "new@example.com";
    private static final String CODE = "123456";
    private static final String HASH = new BCryptPasswordEncoder(12).encode(CODE);
    private final Map<String, User> accounts = new HashMap<>();
    private final CountDownLatch smtpStarted = new CountDownLatch(1);
    private final CountDownLatch releaseSmtp = new CountDownLatch(1);
    private final AtomicReference<SimpleMailMessage> message = new AtomicReference<>();
    private final AtomicReference<String> deliveryThread = new AtomicReference<>();
    private final AtomicBoolean transactionCommitted = new AtomicBoolean();
    private final AtomicBoolean workerHasTransaction = new AtomicBoolean();
    private final Map<Logger, ListAppender<ILoggingEvent>> logs = new HashMap<>();
    private final ExecutorService caller = Executors.newSingleThreadExecutor();
    private AnnotationConfigApplicationContext context;
    private UserRepository users;
    private AuditLogRepository audit;
    private PasswordEncoder encoder;
    private JavaMailSender smtp;
    private UserService service;
    private EmailService email;
    private ThreadPoolTaskExecutor executor;
    private TransactionTemplate transaction;
    private AuthController auth;

    @BeforeEach
    void setUp() {
        for (Class<?> type : new Class<?>[]{EmailService.class, EmailDeliveryService.class, EmailAsyncConfig.class}) {
            Logger logger = (Logger) LoggerFactory.getLogger(type);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            logger.setAdditive(false);
            logs.put(logger, appender);
        }
        users = mock(UserRepository.class);
        audit = mock(AuditLogRepository.class);
        encoder = mock(PasswordEncoder.class);
        smtp = mock(JavaMailSender.class);
        when(users.findByEmailForUpdate(anyString())).thenAnswer(call -> Optional.ofNullable(accounts.get(call.getArgument(0))));
        when(users.existsByEmail(anyString())).thenAnswer(call -> accounts.containsKey(call.getArgument(0)));
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(encoder.encode(anyString())).thenReturn(HASH);
        when(encoder.matches(anyString(), anyString())).thenAnswer(call -> CODE.equals(call.getArgument(0)) && HASH.equals(call.getArgument(1)));
        doAnswer(call -> {
            message.set(new SimpleMailMessage(call.getArgument(0, SimpleMailMessage.class)));
            deliveryThread.set(Thread.currentThread().getName());
            workerHasTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            smtpStarted.countDown();
            if (!releaseSmtp.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test sender was not released");
            return null;
        }).when(smtp).send(any(SimpleMailMessage.class));
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test-mail",
                Map.of("spring.mail.username", "sender@example.com", "spring.mail.password", "test-only")));
        context.registerBean(UserRepository.class, () -> users);
        context.registerBean(AuditLogRepository.class, () -> audit);
        context.registerBean(PasswordEncoder.class, () -> encoder);
        context.registerBean(JavaMailSender.class, () -> smtp);
        context.register(EmailAsyncConfig.class, EmailService.class, EmailDeliveryService.class, UserService.class, AuthenticationAttempts.class);
        context.refresh();
        service = context.getBean(UserService.class);
        email = context.getBean(EmailService.class);
        executor = context.getBean("emailExecutor", ThreadPoolTaskExecutor.class);
        SecureRandom random = mock(SecureRandom.class);
        AtomicInteger next = new AtomicInteger(100001);
        when(random.nextInt(1_000_000)).thenAnswer(call -> next.getAndIncrement());
        ReflectionTestUtils.setField(service, "secureRandom", random);
        transaction = new TransactionTemplate(new MemoryTransactionManager());
        auth = new AuthController();
        ReflectionTestUtils.setField(auth, "userService", service);
        User user = new User();
        user.setId(1L);
        user.setName("Test User");
        user.setEmail(EMAIL);
        user.setPassword(HASH);
        user.setVerificationToken(HASH);
        user.setRecoveryToken(HASH);
        user.setCurrentEmailChangeToken(HASH);
        user.setCurrentEmailChangeTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
        user.setPendingNewEmail(NEW_EMAIL);
        user.setNewEmailToken(HASH);
        accounts.put(EMAIL, user);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        releaseSmtp.countDown();
        caller.shutdownNow();
        caller.awaitTermination(5, TimeUnit.SECONDS);
        if (context != null) context.close();
        logs.forEach((logger, appender) -> {
            logger.detachAppender(appender);
            logger.setAdditive(true);
            appender.stop();
        });
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }

    @ParameterizedTest
    @EnumSource(Flow.class)
    void codeRequestCompletesWhileSmtpIsStillBlocked(Flow flow) throws Exception {
        CodeDeliveryResultDTO result = caller.submit(() -> transaction.execute(status -> request(flow))).get(3, TimeUnit.SECONDS);
        assertEquals("Verifique o seu e-mail.", result.getMessage());
        assertTrue(transactionCommitted.get());
        assertTrue(smtpStarted.await(3, TimeUnit.SECONDS));
        assertEquals(1, releaseSmtp.getCount());
        assertTrue(deliveryThread.get().startsWith("spotmeet-mail-"));
        assertFalse(workerHasTransaction.get());
        String destination = flow == Flow.CONFIRM_NEW_EMAIL || flow == Flow.RESEND_NEW_EMAIL ? NEW_EMAIL : EMAIL;
        assertArrayEquals(new String[]{destination}, message.get().getTo());
        assertTrue(message.get().getText().contains("100001"));
        verify(audit).save(argThat(entry -> !entry.getDetails().contains("Entregue") && !entry.getDetails().contains("100001")));
    }

    private CodeDeliveryResultDTO request(Flow flow) {
        return switch (flow) {
            case REGISTER -> {
                RegisterRequestDTO dto = new RegisterRequestDTO();
                dto.setName("Test User");
                dto.setEmail(EMAIL);
                dto.setPassword("Example1");
                yield service.registerUser(dto);
            }
            case RESEND -> service.resendVerification(new ResendVerificationDTO(EMAIL));
            case RECOVERY -> service.forgotPassword(new ForgotPasswordDTO(EMAIL));
            case CURRENT_EMAIL -> service.requestEmailChange(EMAIL);
            case CONFIRM_NEW_EMAIL -> service.confirmEmailChange(EMAIL, new ConfirmEmailChangeDTO(CODE, NEW_EMAIL));
            case RESEND_NEW_EMAIL -> service.resendNewEmailCode(EMAIL);
        };
    }

    @Test
    void deliveryIsNotQueuedUntilTheCodeTransactionCommits() throws Exception {
        transaction.executeWithoutResult(status -> {
            email.sendVerificationEmail(EMAIL, "Test User", CODE, LocalDateTime.now().plusMinutes(5));
            assertEquals(1, smtpStarted.getCount());
            assertEquals(0, executor.getThreadPoolExecutor().getTaskCount());
            verifyNoInteractions(smtp);
        });
        assertTrue(smtpStarted.await(3, TimeUnit.SECONDS));
        assertTrue(transactionCommitted.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rollbackDoesNotQueueAnEmail(boolean throwException) {
        Runnable rollback = () -> transaction.executeWithoutResult(status -> {
            email.sendVerificationEmail(EMAIL, "Test User", CODE, LocalDateTime.now().plusMinutes(5));
            if (throwException) throw new IllegalStateException("Simulated transaction failure");
            status.setRollbackOnly();
        });
        if (throwException) assertThrows(IllegalStateException.class, rollback::run);
        else rollback.run();
        assertFalse(transactionCommitted.get());
        assertEquals(0, executor.getThreadPoolExecutor().getTaskCount());
        verifyNoInteractions(smtp);
    }

    @Test
    void fullQueueKeepsThePublicResponseGenericWithoutSendingOnTheCallerThread() throws Exception {
        email.sendVerificationEmail(EMAIL, "Test User", CODE, LocalDateTime.now().plusMinutes(5));
        assertTrue(smtpStarted.await(3, TimeUnit.SECONDS));
        for (int i = 0; i < 20; i++) email.sendVerificationEmail(EMAIL, "Test User", CODE, LocalDateTime.now().plusMinutes(5));
        ResponseEntity<?> existing = caller.submit(() -> transaction.execute(status -> auth.forgotPassword(new ForgotPasswordDTO(EMAIL)))).get(3, TimeUnit.SECONDS);
        ResponseEntity<?> missing = caller.submit(() -> transaction.execute(status -> auth.forgotPassword(new ForgotPasswordDTO("missing@example.com")))).get(3, TimeUnit.SECONDS);
        assertEquals(200, existing.getStatusCode().value());
        assertEquals(existing.getStatusCode(), missing.getStatusCode());
        assertEquals(existing.getBody(), missing.getBody());
        assertEquals(Map.of("success", true, "message", "Verifique o seu e-mail."), existing.getBody());
        assertEquals(20, executor.getThreadPoolExecutor().getQueue().size());
        assertEquals(1, releaseSmtp.getCount());
        assertTrue(logMessages().contains("Fila de e-mails indisponível"));
        assertSanitizedLogs();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void configuredSmtpFailuresDoNotChangeTheResponseOrExposeArguments(boolean unexpectedError) throws Exception {
        doAnswer(call -> {
            String sensitiveDetail = EMAIL + " Test User " + CODE + " test-only";
            if (unexpectedError) throw new AssertionError(sensitiveDetail);
            throw new MailSendException(sensitiveDetail);
        }).when(smtp).send(any(SimpleMailMessage.class));
        ResponseEntity<?> response = caller.submit(() -> transaction.execute(status -> auth.forgotPassword(new ForgotPasswordDTO(EMAIL)))).get(3, TimeUnit.SECONDS);
        drain();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("success", true, "message", "Verifique o seu e-mail."), response.getBody());
        assertSanitizedLogs();
        assertTrue(logMessages().contains(unexpectedError ? "Falha inesperada" : "Não foi possível encaminhar"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void codesAlreadyExpiredInTheQueueAreNotSent(boolean missingExpiry) throws Exception {
        email.sendVerificationEmail(EMAIL, "Test User", CODE, missingExpiry ? null : LocalDateTime.now().minusSeconds(1));
        drain();
        verifyNoInteractions(smtp);
        assertTrue(logMessages().contains("código expirado"));
        assertSanitizedLogs();
    }

    @Test
    void aCodeThatExpiresWhileWaitingIsNotSent() throws Exception {
        email.sendVerificationEmail(EMAIL, "Test User", CODE, LocalDateTime.now().plusMinutes(5));
        assertTrue(smtpStarted.await(3, TimeUnit.SECONDS));
        LocalDateTime expiresAt = LocalDateTime.now().plusNanos(1);
        email.sendVerificationEmail(EMAIL, "Test User", CODE, expiresAt);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(1))
                .until(() -> !expiresAt.isAfter(LocalDateTime.now()));
        releaseSmtp.countDown();
        drain();
        verify(smtp, times(1)).send(any(SimpleMailMessage.class));
        assertTrue(logMessages().contains("código expirado"));
    }

    @Test
    void missingMailSenderWithConfiguredCredentialsDoesNotEnableTheQaFallback() throws Exception {
        Object target = org.springframework.test.util.AopTestUtils.getTargetObject(context.getBean(EmailDeliveryService.class));
        ReflectionTestUtils.setField(target, "mailSender", null);
        email.sendVerificationEmail(EMAIL, "Test User", CODE, LocalDateTime.now().plusMinutes(5));
        drain();
        verifyNoInteractions(smtp);
        assertTrue(logMessages().contains("Serviço SMTP indisponível"));
        assertSanitizedLogs();
    }

    @Test
    void namesRemainPlainTextAndCannotChangeMailHeaders() throws Exception {
        String name = "<script>alert(1)</script>\r\nBcc: other@example.com";
        releaseSmtp.countDown();
        email.sendVerificationEmail(EMAIL, name, CODE, LocalDateTime.now().plusMinutes(5));
        drain();
        assertArrayEquals(new String[]{EMAIL}, message.get().getTo());
        assertNull(message.get().getBcc());
        assertTrue(message.get().getText().contains(name));
        assertFalse(message.get().getSubject().contains(name));
        verify(smtp).send(any(SimpleMailMessage.class));
    }

    @Test
    void localQaFallbackRequiresMissingSmtpConfigurationAndOmitsPersonalData() throws Exception {
        Object target = org.springframework.test.util.AopTestUtils.getTargetObject(context.getBean(EmailDeliveryService.class));
        ReflectionTestUtils.setField(target, "smtpPassword", "");
        email.sendVerificationEmail(EMAIL, "Test User", CODE, LocalDateTime.now().plusMinutes(5));
        drain();
        verifyNoInteractions(smtp);
        assertTrue(logMessages().contains(">>> " + CODE + " <<<"));
        assertFalse(logMessages().contains(EMAIL));
        assertFalse(logMessages().contains("Test User"));
        assertFalse(logMessages().contains("test-only"));
    }

    @ParameterizedTest
    @CsvSource({
            "RECOVERY, false, false, false", "RECOVERY, true, false, false", "RECOVERY, true, true, true",
            "RESEND, false, false, false", "RESEND, true, false, false", "RESEND, true, false, true",
            "RESEND, true, true, false", "RESEND, true, true, true"
    })
    void publicRequestsKeepTheSameHashWorkWithoutSendingToUnknownOrVerifiedAccounts(Flow flow, boolean exists, boolean verified, boolean previousCode) throws Exception {
        User user = accounts.get(EMAIL);
        user.setEmailVerified(verified);
        if (!previousCode) {
            user.setVerificationToken(null);
            user.setRecoveryToken(null);
        }
        if (!exists) accounts.clear();
        transaction.execute(status -> request(flow));
        verify(encoder, times(1)).matches(eq("100001"), matches("\\$2[aby]\\$12\\$.{53}"));
        verify(encoder, times(1)).encode("100001");
        if (!exists || flow == Flow.RESEND && verified) {
            verify(users, never()).save(any());
            verifyNoInteractions(audit, smtp);
            assertEquals(0, executor.getThreadPoolExecutor().getTaskCount());
        } else {
            assertTrue(smtpStarted.await(3, TimeUnit.SECONDS));
            verify(users).save(user);
            verify(audit).save(any(AuditLog.class));
        }
    }

    private String changePassword(boolean recovery) {
        User user = accounts.get(EMAIL);
        if (recovery) {
            user.setRecoveryTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
            ResetPasswordDTO dto = new ResetPasswordDTO();
            dto.setEmail(EMAIL); dto.setToken(CODE); dto.setNewPassword("NewPassword2");
            return service.resetPassword(dto);
        }
        when(encoder.matches("OldPassword1", HASH)).thenReturn(true);
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setCurrentPassword("OldPassword1"); dto.setNewPassword("NewPassword2");
        return service.changePassword(EMAIL, dto);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void passwordNoticeWaitsForCommitButDoesNotWaitForSmtp(boolean recovery) throws Exception {
        String result = caller.submit(() -> transaction.execute(status -> {
            String response = changePassword(recovery);
            verifyNoInteractions(smtp);
            return response;
        })).get(3, TimeUnit.SECONDS);
        assertTrue(result.contains("sucesso"));
        assertTrue(transactionCommitted.get());
        assertNotNull(accounts.get(EMAIL).getTokensValidAfter());
        assertTrue(smtpStarted.await(3, TimeUnit.SECONDS));
        assertEquals(1, releaseSmtp.getCount());
        assertTrue(deliveryThread.get().startsWith("spotmeet-mail-"));
        assertFalse(workerHasTransaction.get());
        assertArrayEquals(new String[]{EMAIL}, message.get().getTo());
        assertEquals("SpotMeet - Senha alterada", message.get().getSubject());
        assertTrue(message.get().getText().contains("recuperação de senha no aplicativo"));
        for (String secret : new String[]{"OldPassword1", "NewPassword2", CODE, HASH}) assertFalse(message.get().getText().contains(secret));
        assertSanitizedLogs();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rolledBackPasswordChangeNeverQueuesItsNotice(boolean recovery) {
        transaction.executeWithoutResult(status -> { changePassword(recovery); status.setRollbackOnly(); });
        verifyNoInteractions(smtp);
        assertEquals(0, executor.getThreadPoolExecutor().getTaskCount());
        assertFalse(transactionCommitted.get());
    }

    @Test void failedPasswordProofDoesNotScheduleANotice() {
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setCurrentPassword("WrongPassword1"); dto.setNewPassword("NewPassword2");
        assertThrows(IllegalArgumentException.class, () -> transaction.execute(status -> service.changePassword(EMAIL, dto)));
        verifyNoInteractions(smtp);
        assertEquals(0, executor.getThreadPoolExecutor().getTaskCount());
        assertNull(accounts.get(EMAIL).getTokensValidAfter());
    }

    @Test void aFullQueueDoesNotUndoACommittedPasswordChange() throws Exception {
        email.sendVerificationEmail(EMAIL, "Test User", CODE, LocalDateTime.now().plusMinutes(5));
        assertTrue(smtpStarted.await(3, TimeUnit.SECONDS));
        for (int i = 0; i < 20; i++) email.sendVerificationEmail(EMAIL, "Test User", CODE, LocalDateTime.now().plusMinutes(5));
        String result = caller.submit(() -> transaction.execute(status -> changePassword(false))).get(3, TimeUnit.SECONDS);
        assertTrue(result.contains("sucesso"));
        assertTrue(transactionCommitted.get());
        assertNotNull(accounts.get(EMAIL).getTokensValidAfter());
        assertTrue(logMessages().contains("O aviso de alteração de senha não foi agendado"));
        assertSanitizedLogs();
    }

    @Test void failedSmtpDoesNotChangePasswordSuccessOrExposeCredentials() throws Exception {
        doThrow(new MailSendException(EMAIL + " OldPassword1 NewPassword2")).when(smtp).send(any(SimpleMailMessage.class));
        assertTrue(transaction.execute(status -> changePassword(false)).contains("sucesso"));
        drain();
        assertTrue(transactionCommitted.get());
        assertNotNull(accounts.get(EMAIL).getTokensValidAfter());
        assertTrue(logMessages().contains("Não foi possível encaminhar"));
        assertFalse(logMessages().contains("OldPassword1"));
        assertFalse(logMessages().contains("NewPassword2"));
        assertSanitizedLogs();
    }

    @Test void passwordNoticeWithoutSmtpDoesNotUseTheCodeQaFallback() throws Exception {
        Object target = org.springframework.test.util.AopTestUtils.getTargetObject(context.getBean(EmailDeliveryService.class));
        ReflectionTestUtils.setField(target, "smtpPassword", "");
        transaction.execute(status -> changePassword(false)); drain();
        verifyNoInteractions(smtp);
        assertTrue(logMessages().contains("SMTP não configurado"));
        assertFalse(logMessages().contains("Código local de QA"));
        assertSanitizedLogs();
    }

    private void drain() throws Exception {
        executor.submit(() -> {}).get(3, TimeUnit.SECONDS);
    }

    private String logMessages() {
        return logs.values().stream().flatMap(appender -> appender.list.stream())
                .map(ILoggingEvent::getFormattedMessage).reduce("", (first, next) -> first + "\n" + next);
    }

    private void assertSanitizedLogs() {
        String text = logMessages();
        for (String sensitive : new String[]{EMAIL, "Test User", CODE, "100001", "test-only"}) assertFalse(text.contains(sensitive));
        logs.values().forEach(appender -> appender.list.forEach(entry -> assertNull(entry.getThrowableProxy())));
    }

    private class MemoryTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) { transactionCommitted.set(true); }
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
