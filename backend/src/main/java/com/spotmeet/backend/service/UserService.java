package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.*;
import com.spotmeet.backend.model.AuditLog;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.AuditLogRepository;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.security.AuthenticationAttempts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * User service: secure registration with BCrypt, account verification by e-mail,
 * password recovery and the two-step secure e-mail change flow (LGPD).
 */
@Service
public class UserService {

    /** Lifetime of every one-time code: e-mail verification, password recovery and e-mail change. */
    private static final long CODE_EXPIRATION_MINUTES = 5;
    private static final String DUMMY_CODE_HASH = new BCryptPasswordEncoder(12).encode("timing-placeholder");


    @Autowired
    private UserRepository userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogRepository auditLogRepo;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuthenticationAttempts attempts;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Registers a new user with e-mail verification.
     * If the account exists but is not verified yet, updates credentials and sends a new code.
     */
    @Transactional
    public CodeDeliveryResultDTO registerUser(RegisterRequestDTO dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();
        Optional<User> existingUserOpt = userRepo.findByEmailForUpdate(normalizedEmail);

        User user;
        if (existingUserOpt.isPresent()) {
            User existing = existingUserOpt.get();
            if (existing.isEmailVerified()) {
                throw new IllegalArgumentException("Este e-mail já está cadastrado e verificado no SpotMeet. Por favor, faça login ou use a recuperação de senha.");
            }
            // Account pending verification: update data and regenerate the code
            user = existing;
            user.setName(dto.getName().trim());
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        } else {
            user = new User();
            user.setName(dto.getName().trim());
            user.setEmail(normalizedEmail);
            user.setPassword(passwordEncoder.encode(dto.getPassword())); // BCrypt hash
            user.setRole("USER"); // Least privilege
            user.setEmailVerified(false); // Requires account confirmation
            // Tokens issued to a previous owner of this address must not open the new account.
            user.setTokensValidAfter(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        }

        String verificationCode = generateCode(user.getVerificationToken());
        user.setVerificationToken(passwordEncoder.encode(verificationCode));
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES));

        User saved = userRepo.save(user);

        // Send the code by e-mail
        emailService.sendVerificationEmail(saved.getEmail(), saved.getName(), verificationCode, saved.getVerificationTokenExpiresAt());

        // Audit
        auditLogRepo.save(new AuditLog(saved, "USER_REGISTERED", "Cadastro e envio de verificação solicitados: " + saved.getEmail()));

        return deliveryResult(saved, saved.getEmail());
    }

    /**
     * Returns the full profile of the authenticated user.
     */
    public UserProfileResponseDTO getMyProfile(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + email));
        return UserProfileResponseDTO.fromEntity(user);
    }

    /**
     * Updates profile data: name, bio, availability status and preferred theme.
     */
    public UserProfileResponseDTO updateMyProfile(String email, UpdateUserProfileDTO dto) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + email));

        if (dto.getName() != null && !dto.getName().trim().isBlank()) {
            user.setName(dto.getName().trim());
        }

        if (dto.getBio() != null) {
            user.setBio(dto.getBio().trim());
        }

        if (dto.getAvailabilityStatus() != null && !dto.getAvailabilityStatus().isBlank()) {
            String statusStr = dto.getAvailabilityStatus().trim().toUpperCase();
            try {
                user.setAvailabilityStatus(User.AvailabilityStatus.valueOf(statusStr));
            } catch (IllegalArgumentException e) {
                // Unknown value ignored
            }
        }

        if (dto.getPreferredTheme() != null && !dto.getPreferredTheme().isBlank()) {
            String themeStr = dto.getPreferredTheme().trim().toUpperCase();
            try {
                user.setPreferredTheme(User.PreferredTheme.valueOf(themeStr));
            } catch (IllegalArgumentException e) {
                // Unknown value ignored
            }
        }

        User saved = userRepo.save(user);

        try {
            auditLogRepo.save(new AuditLog(
                    saved,
                    "PROFILE_UPDATED",
                    "Atualização de perfil: nome, status=" + saved.getAvailabilityStatus() + ", tema=" + saved.getPreferredTheme()
            ));
        } catch (Exception e) {
            // Audit failure does not block the response
        }

        return UserProfileResponseDTO.fromEntity(saved);
    }

    /**
     * Updates the availability status of the user.
     */
    public User updateAvailabilityStatus(Long userId, User.AvailabilityStatus newStatus) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        user.setAvailabilityStatus(newStatus);
        return userRepo.save(user);
    }

    /**
     * Updates the preferred theme of the user.
     */
    public User updatePreferredTheme(Long userId, User.PreferredTheme newTheme) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        user.setPreferredTheme(newTheme);
        return userRepo.save(user);
    }

    /**
     * Validates the verification code sent by e-mail and activates the account (emailVerified = true).
     */
    @Transactional
    public User verifyEmail(VerifyEmailDTO dto) {
        if (dto == null || dto.getCode() == null || dto.getCode().trim().isBlank()) {
            throw new IllegalArgumentException("Código de verificação não informado.");
        }

        String cleanCode = dto.getCode().trim();
        User user = attempts.verify(AuthenticationAttempts.Purpose.EMAIL_VERIFICATION, dto.getEmail(), () -> {
            User account = findCodeUser(dto.getEmail(), "Código de verificação inválido ou inexistente.");

            if (account.isEmailVerified() || !matchesCode(cleanCode, account.getVerificationToken())) {
                throw new IllegalArgumentException("Código de verificação incorreto.");
            }

            if (isExpired(account.getVerificationTokenExpiresAt())) {
                throw new IllegalArgumentException("O código de verificação expirou. Solicite um novo código no aplicativo.");
            }
            return account;
        });

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);
        userRepo.save(user);

        try {
            auditLogRepo.save(new AuditLog(user, "ACCOUNT_VERIFIED", "Conta ativada com sucesso: " + user.getEmail()));
        } catch (Exception e) {
            // Audit failure does not block success
        }

        return user;
    }

    /**
     * Sends a new verification code to the given e-mail.
     */
    @Transactional
    public CodeDeliveryResultDTO resendVerification(ResendVerificationDTO dto) {
        CodeDeliveryResultDTO result = deliveryResult(null, null);

        if (dto != null && dto.getEmail() != null && !dto.getEmail().trim().isBlank()) {
            User user = userRepo.findByEmailForUpdate(dto.getEmail().trim().toLowerCase()).orElse(null);
            String newCode = generateCode(user != null && !user.isEmailVerified() ? user.getVerificationToken() : null);
            String codeHash = passwordEncoder.encode(newCode);

            if (user != null && !user.isEmailVerified()) {
                user.setVerificationToken(codeHash);
                user.setVerificationTokenExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES));
                userRepo.save(user);

                emailService.sendVerificationEmail(user.getEmail(), user.getName(), newCode, user.getVerificationTokenExpiresAt());

                try {
                    auditLogRepo.save(new AuditLog(user, "VERIFICATION_RESENT", "Reenvio de verificação solicitado: " + user.getEmail()));
                } catch (Exception e) {
                    // Ignored
                }
            }
        }

        return result;
    }

    /**
     * Generates a temporary password recovery token and sends it by e-mail.
     */
    @Transactional
    public CodeDeliveryResultDTO forgotPassword(ForgotPasswordDTO dto) {
        CodeDeliveryResultDTO result = deliveryResult(null, null);

        if (dto != null && dto.getEmail() != null && !dto.getEmail().trim().isBlank()) {
            User user = userRepo.findByEmailForUpdate(dto.getEmail().trim().toLowerCase()).orElse(null);
            String recoveryToken = generateCode(user != null ? user.getRecoveryToken() : null);
            String codeHash = passwordEncoder.encode(recoveryToken);

            if (user != null) {
                user.setRecoveryToken(codeHash);
                user.setRecoveryTokenExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES));
                userRepo.save(user);

                emailService.sendPasswordRecoveryEmail(user.getEmail(), user.getName(), recoveryToken, user.getRecoveryTokenExpiresAt());

                try {
                    auditLogRepo.save(new AuditLog(user, "PASSWORD_RECOVERY_REQUESTED", "Envio de recuperação solicitado: " + user.getEmail()));
                } catch (Exception e) {
                    // Ignored
                }
            }
        }

        return result;
    }

    /**
     * Finds the account of a recovery code and validates that the code matches and has not expired.
     * Shared by the code-only check (step 2 of the recovery flow) and by the actual reset.
     *
     * @throws IllegalArgumentException if the code is unknown, does not match or is expired.
     */
    private User resolveRecoveryToken(String email, String token) {
        return attempts.verify(AuthenticationAttempts.Purpose.RECOVERY, email, () -> validateRecoveryToken(email, token));
    }

    private User validateRecoveryToken(String email, String token) {
        String cleanToken = token != null ? token.trim() : "";
        User user = findCodeUser(email, "Código de recuperação inválido ou inexistente.");

        if (!matchesCode(cleanToken, user.getRecoveryToken())) {
            throw new IllegalArgumentException("Código de recuperação incorreto.");
        }

        if (isExpired(user.getRecoveryTokenExpiresAt())) {
            throw new IllegalArgumentException("O código de recuperação expirou. Solicite um novo código no aplicativo.");
        }

        return user;
    }

    /**
     * Checks a recovery code without consuming it, so the app can validate step 2
     * of the recovery flow before asking for the new password.
     */
    @Transactional
    public String verifyRecoveryCode(VerifyRecoveryCodeDTO dto) {
        if (dto == null || dto.getToken() == null || dto.getToken().trim().isBlank()) {
            throw new IllegalArgumentException("Token ou código de recuperação não informado.");
        }
        resolveRecoveryToken(dto.getEmail(), dto.getToken());
        return "Código validado. Defina a nova senha.";
    }

    /**
     * Validates the recovery token, hashes the new password with BCrypt and saves it.
     */
    @Transactional
    public String resetPassword(ResetPasswordDTO dto) {
        if (dto == null || dto.getToken() == null || dto.getToken().trim().isBlank()) {
            throw new IllegalArgumentException("Token ou código de recuperação não informado.");
        }

        User user = resolveRecoveryToken(dto.getEmail(), dto.getToken());

        // BCrypt hash of the new password
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setTokensValidAfter(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        user.setRecoveryToken(null);
        user.setRecoveryTokenExpiresAt(null);
        user.setEmailVerified(true);
        userRepo.save(user);
        emailService.sendPasswordChangedEmail(user.getEmail());

        try {
            auditLogRepo.save(new AuditLog(user, "PASSWORD_RESET", "Senha redefinida com sucesso para: " + user.getEmail()));
        } catch (Exception e) {
            // Audit failure does not block completion
        }

        return "Senha redefinida com sucesso! Você já pode acessar sua conta com a nova senha.";
    }

    /**
     * Changes the password of the authenticated user after validating the current one.
     */
    @Transactional
    public String changePassword(String authenticatedEmail, ChangePasswordDTO dto) {
        User user = userRepo.findByEmailForUpdate(authenticatedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        attempts.verify(AuthenticationAttempts.Purpose.CURRENT_PASSWORD, "user:" + user.getId(), () -> {
            if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("A senha atual está incorreta.");
            }
            return null;
        });

        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("A nova senha deve ser diferente da senha atual.");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setTokensValidAfter(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        userRepo.save(user);
        emailService.sendPasswordChangedEmail(user.getEmail());

        try {
            auditLogRepo.save(new AuditLog(user, "PASSWORD_CHANGED", "Senha alterada pelo próprio usuário: " + user.getEmail()));
        } catch (Exception e) {
            // Audit failure does not block completion
        }

        return "Senha alterada com sucesso!";
    }

    // Two-step secure e-mail change flow (LGPD)

    /**
     * Step 1 (start): sends a 6-digit authorization code to the current e-mail of the authenticated user.
     */
    @Transactional
    public CodeDeliveryResultDTO requestEmailChange(String authenticatedEmail) {
        User user = userRepo.findByEmailForUpdate(authenticatedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + authenticatedEmail));

        String authorizationCode = generateCode(user.getCurrentEmailChangeToken());
        user.setCurrentEmailChangeToken(passwordEncoder.encode(authorizationCode));
        user.setCurrentEmailChangeTokenExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES));
        user.setPendingNewEmail(null);
        user.setNewEmailToken(null);
        user.setNewEmailTokenExpiresAt(null);
        userRepo.save(user);

        emailService.sendCurrentEmailChangeCode(user.getEmail(), user.getName(), authorizationCode, user.getCurrentEmailChangeTokenExpiresAt());

        try {
            auditLogRepo.save(new AuditLog(user, "LGPD_EMAIL_CHANGE_REQUESTED", "Envio de autorização solicitado para o e-mail atual: " + user.getEmail()));
        } catch (Exception e) {
            // Audit error ignored
        }

        return deliveryResult(user, user.getEmail());
    }

    /**
     * Step 1 (end) / Step 2 (start): validates the current e-mail code, checks the
     * availability of the new e-mail and sends the activation code to the new address.
     */
    @Transactional
    public CodeDeliveryResultDTO confirmEmailChange(String authenticatedEmail, ConfirmEmailChangeDTO dto) {
        if (dto == null || dto.getCurrentCode() == null || dto.getCurrentCode().trim().isBlank()) {
            throw new IllegalArgumentException("O código de autorização do e-mail atual é obrigatório.");
        }
        if (dto.getNewEmail() == null || dto.getNewEmail().trim().isBlank()) {
            throw new IllegalArgumentException("O novo e-mail é obrigatório.");
        }

        String newEmail = dto.getNewEmail().trim().toLowerCase();
        User user = userRepo.findByEmailForUpdate(authenticatedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + authenticatedEmail));

        if (user.getEmail().equalsIgnoreCase(newEmail)) {
            throw new IllegalArgumentException("O novo e-mail não pode ser idêntico ao e-mail atual.");
        }

        attempts.verify(AuthenticationAttempts.Purpose.CURRENT_EMAIL, "user:" + user.getId(), () -> {
            if (!matchesCode(dto.getCurrentCode().trim(), user.getCurrentEmailChangeToken())) {
                throw new IllegalArgumentException("Código de autorização do e-mail atual incorreto.");
            }

            if (isExpired(user.getCurrentEmailChangeTokenExpiresAt())) {
                throw new IllegalArgumentException("O código de autorização expirou. Solicite um novo envio no aplicativo.");
            }
            return null;
        });

        if (userRepo.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("O novo e-mail informado já está vinculado a outra conta no SpotMeet.");
        }

        // Activation code for the new e-mail
        String newEmailCode = generateCode(user.getNewEmailToken());
        user.setCurrentEmailChangeToken(null);
        user.setCurrentEmailChangeTokenExpiresAt(null);
        user.setPendingNewEmail(newEmail);
        user.setNewEmailToken(passwordEncoder.encode(newEmailCode));
        user.setNewEmailTokenExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES));
        userRepo.save(user);

        emailService.sendNewEmailConfirmationCode(newEmail, user.getName(), newEmailCode, user.getNewEmailTokenExpiresAt());

        try {
            auditLogRepo.save(new AuditLog(user, "LGPD_EMAIL_CHANGE_STEP1_CONFIRMED", "Envio de confirmação solicitado para o endereço pendente: " + newEmail));
        } catch (Exception e) {
            // Ignored
        }

        return deliveryResult(user, newEmail);
    }

    /** Resends only to the new address authorized in step 1. */
    @Transactional
    public CodeDeliveryResultDTO resendNewEmailCode(String authenticatedEmail) {
        User user = userRepo.findByEmailForUpdate(authenticatedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        String newEmail = user.getPendingNewEmail();
        if (newEmail == null || newEmail.isBlank() || user.getNewEmailToken() == null) {
            throw new IllegalArgumentException("Nenhuma troca de e-mail autorizada. Inicie o processo novamente.");
        }
        if (userRepo.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("O novo e-mail informado já foi cadastrado por outro usuário.");
        }

        String newCode = generateCode(user.getNewEmailToken());
        user.setNewEmailToken(passwordEncoder.encode(newCode));
        user.setNewEmailTokenExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES));
        userRepo.save(user);
        emailService.sendNewEmailConfirmationCode(newEmail, user.getName(), newCode, user.getNewEmailTokenExpiresAt());
        auditLogRepo.save(new AuditLog(user, "LGPD_EMAIL_CHANGE_CODE_RESENT", "Reenvio solicitado ao endereço pendente"));
        return deliveryResult(user, newEmail);
    }

    /**
     * Step 2 (end): validates the code received on the new e-mail, updates the e-mail,
     * clears temporary tokens and records the LGPD audit entry.
     */
    @Transactional
    public User completeEmailChange(String authenticatedEmail, CompleteEmailChangeDTO dto) {
        if (dto == null || dto.getNewEmailCode() == null || dto.getNewEmailCode().trim().isBlank()) {
            throw new IllegalArgumentException("O código enviado ao novo e-mail é obrigatório.");
        }

        User user = userRepo.findByEmailForUpdate(authenticatedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + authenticatedEmail));

        String newEmail = user.getPendingNewEmail();
        if (newEmail == null || newEmail.isBlank()) {
            throw new IllegalArgumentException("Nenhuma solicitação de troca de e-mail pendente encontrada. Inicie o processo novamente.");
        }

        attempts.verify(AuthenticationAttempts.Purpose.NEW_EMAIL, "user:" + user.getId(), () -> {
            if (!matchesCode(dto.getNewEmailCode().trim(), user.getNewEmailToken())) {
                throw new IllegalArgumentException("Código de ativação do novo e-mail incorreto.");
            }

            if (isExpired(user.getNewEmailTokenExpiresAt())) {
                throw new IllegalArgumentException("O código de ativação do novo e-mail expirou. Por favor, reinicie a solicitação.");
            }
            return null;
        });

        if (userRepo.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("O novo e-mail informado já foi cadastrado por outro usuário.");
        }

        String oldEmail = user.getEmail();

        // Apply the change
        user.setEmail(newEmail);
        user.setEmailVerified(true);
        user.setPendingNewEmail(null);
        user.setCurrentEmailChangeToken(null);
        user.setCurrentEmailChangeTokenExpiresAt(null);
        user.setNewEmailToken(null);
        user.setNewEmailTokenExpiresAt(null);

        User updated = userRepo.save(user);

        // Strict LGPD audit record
        try {
            auditLogRepo.save(new AuditLog(
                    updated,
                    "LGPD_EMAIL_CHANGED",
                    "Alteração de e-mail concluída com dupla validação criptográfica. De: " + oldEmail + " | Para: " + newEmail
            ));
        } catch (Exception e) {
            // Audit failure does not block success
        }

        return updated;
    }

    private User findCodeUser(String email, String invalidMessage) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(invalidMessage);
        }
        return userRepo.findByEmailForUpdate(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException(invalidMessage));
    }

    private boolean matchesCode(String code, String hash) {
        return code != null && code.matches(ValidationPatterns.OTP)
                && hash != null && hash.length() == 60 && hash.startsWith("$2")
                && passwordEncoder.matches(code, hash);
    }

    private boolean isExpired(LocalDateTime expiresAt) {
        return expiresAt == null || !expiresAt.isAfter(LocalDateTime.now());
    }

    private String generateCode(String previousHash) {
        // Keeps the BCrypt comparison work when there is no previous code or account.
        String comparisonHash = previousHash != null && previousHash.length() == 60 && previousHash.startsWith("$2")
                ? previousHash : DUMMY_CODE_HASH;
        String code;
        do {
            code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        } while (code.equals(previousHash) || matchesCode(code, comparisonHash));
        return code;
    }

    private CodeDeliveryResultDTO deliveryResult(User user, String destination) {
        CodeDeliveryResultDTO result = new CodeDeliveryResultDTO("Verifique o seu e-mail.");
        result.setUser(user);
        result.setDestination(destination);
        return result;
    }
}
