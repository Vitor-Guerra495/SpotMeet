package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.*;
import com.spotmeet.backend.model.AuditLog;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.AuditLogRepository;
import com.spotmeet.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * User service: secure registration with BCrypt, account verification by e-mail,
 * password recovery and the two-step secure e-mail change flow (LGPD).
 */
@Service
public class UserService {

    /** Lifetime of every one-time code: e-mail verification, password recovery and e-mail change. */
    private static final long CODE_EXPIRATION_MINUTES = 5;


    @Autowired
    private UserRepository userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogRepository auditLogRepo;

    @Autowired
    private EmailService emailService;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Registers a new user with e-mail verification.
     * If the account exists but is not verified yet, updates credentials and sends a new code.
     */
    public CodeDeliveryResultDTO registerUser(RegisterRequestDTO dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();
        Optional<User> existingUserOpt = userRepo.findByEmail(normalizedEmail);

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
        }

        // 6-digit OTP confirmation code, expires in 30 minutes
        String verificationCode = String.format("%06d", secureRandom.nextInt(1_000_000));
        user.setVerificationToken(verificationCode);
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES));

        User saved = userRepo.save(user);

        // Send the code by e-mail
        boolean delivered = emailService.sendVerificationEmail(saved.getEmail(), saved.getName(), verificationCode);

        // Audit
        auditLogRepo.save(new AuditLog(saved, "USER_REGISTERED", "Email: " + saved.getEmail() + " | Canal: EMAIL | EntregueExterno=" + delivered));

        CodeDeliveryResultDTO result = new CodeDeliveryResultDTO();
        result.setUser(saved);
        result.setDelivered(delivered);
        result.setChannel("EMAIL");
        result.setDestination(saved.getEmail());
        result.setOtpCode(verificationCode);

        if (delivered) {
            result.setMessage("Código de verificação enviado para o seu e-mail!");
        } else {
            result.setMessage("Conta criada com sucesso!");
            result.setWarning("Ambiente de Desenvolvimento: O código OTP gerado é " + verificationCode);
        }

        return result;
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
    public String verifyEmail(VerifyEmailDTO dto) {
        if (dto == null || dto.getCode() == null || dto.getCode().trim().isBlank()) {
            throw new IllegalArgumentException("Código de verificação não informado.");
        }

        String cleanCode = dto.getCode().trim();
        User user = null;

        if (dto.getEmail() != null && !dto.getEmail().trim().isBlank()) {
            user = userRepo.findByEmail(dto.getEmail().trim().toLowerCase()).orElse(null);
        }

        if (user == null) {
            user = userRepo.findByVerificationToken(cleanCode)
                    .orElseThrow(() -> new IllegalArgumentException("Código de verificação inválido ou inexistente."));
        }

        if (user.isEmailVerified()) {
            return "Conta já verificada com sucesso. Você já pode fazer login no SpotMeet.";
        }

        if (user.getVerificationToken() == null || !user.getVerificationToken().equalsIgnoreCase(cleanCode)) {
            throw new IllegalArgumentException("Código de verificação incorreto.");
        }

        if (user.getVerificationTokenExpiresAt() != null && user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("O código de verificação expirou. Solicite um novo código no aplicativo.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);
        userRepo.save(user);

        try {
            auditLogRepo.save(new AuditLog(user, "ACCOUNT_VERIFIED", "Conta ativada com sucesso: " + user.getEmail()));
        } catch (Exception e) {
            // Audit failure does not block success
        }

        return "Conta verificada com sucesso! Você já pode acessar o sistema.";
    }

    /**
     * Sends a new verification code to the given e-mail.
     */
    public CodeDeliveryResultDTO resendVerification(ResendVerificationDTO dto) {
        CodeDeliveryResultDTO result = new CodeDeliveryResultDTO();
        result.setChannel("EMAIL");

        if (dto != null && dto.getEmail() != null && !dto.getEmail().trim().isBlank()) {
            User user = userRepo.findByEmail(dto.getEmail().trim().toLowerCase()).orElse(null);

            if (user != null && !user.isEmailVerified()) {
                String newCode = String.format("%06d", secureRandom.nextInt(1_000_000));
                user.setVerificationToken(newCode);
                user.setVerificationTokenExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES));
                userRepo.save(user);

                boolean delivered = emailService.sendVerificationEmail(user.getEmail(), user.getName(), newCode);
                result.setDelivered(delivered);
                result.setDestination(user.getEmail());
                result.setOtpCode(newCode);

                try {
                    auditLogRepo.save(new AuditLog(user, "VERIFICATION_RESENT", "Código reenviado via e-mail para: " + user.getEmail() + " | EntregueExterno=" + delivered));
                } catch (Exception e) {
                    // Ignored
                }
            }
        }

        if (result.isDelivered()) {
            result.setMessage("Novo código de verificação enviado com sucesso para seu e-mail!");
        } else if (result.getOtpCode() != null) {
            result.setMessage("Novo código gerado! Modo Desenvolvimento: código OTP para teste: " + result.getOtpCode());
            result.setWarning("Ambiente de Desenvolvimento: Provedor de envio externo não configurado.");
        } else {
            result.setMessage("Se o e-mail informado estiver cadastrado e pendente de validação, um novo código foi enviado.");
        }

        return result;
    }

    /**
     * Generates a temporary password recovery token and sends it by e-mail.
     */
    public CodeDeliveryResultDTO forgotPassword(ForgotPasswordDTO dto) {
        CodeDeliveryResultDTO result = new CodeDeliveryResultDTO();
        result.setChannel("EMAIL");

        if (dto != null && dto.getEmail() != null && !dto.getEmail().trim().isBlank()) {
            User user = userRepo.findByEmail(dto.getEmail().trim().toLowerCase()).orElse(null);

            if (user != null) {
                String recoveryToken = String.format("%06d", secureRandom.nextInt(1_000_000));
                user.setRecoveryToken(recoveryToken);
                user.setRecoveryTokenExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES));
                userRepo.save(user);

                boolean delivered = emailService.sendPasswordRecoveryEmail(user.getEmail(), user.getName(), recoveryToken);
                result.setDelivered(delivered);
                result.setDestination(user.getEmail());
                result.setOtpCode(recoveryToken);

                try {
                    auditLogRepo.save(new AuditLog(user, "PASSWORD_RECOVERY_REQUESTED", "Token de recuperação gerado para: " + user.getEmail() + " | EntregueExterno=" + delivered));
                } catch (Exception e) {
                    // Ignored
                }
            }
        }

        if (result.isDelivered()) {
            result.setMessage("Código de recuperação enviado com sucesso para o seu e-mail!");
        } else if (result.getOtpCode() != null) {
            result.setMessage("Modo Desenvolvimento: seu código OTP de recuperação para teste é: " + result.getOtpCode());
            result.setWarning("Ambiente de Desenvolvimento: Provedor de envio externo não configurado.");
        } else {
            result.setMessage("Se o e-mail informado estiver cadastrado em nossa base, o código de recuperação foi enviado.");
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
        String cleanToken = token != null ? token.trim() : "";
        User user = null;

        if (email != null && !email.trim().isBlank()) {
            user = userRepo.findByEmail(email.trim().toLowerCase()).orElse(null);
        }

        if (user == null) {
            user = userRepo.findByRecoveryToken(cleanToken)
                    .orElseThrow(() -> new IllegalArgumentException("Código de recuperação inválido ou inexistente."));
        }

        if (user.getRecoveryToken() == null || !user.getRecoveryToken().equalsIgnoreCase(cleanToken)) {
            throw new IllegalArgumentException("Código de recuperação incorreto.");
        }

        if (user.getRecoveryTokenExpiresAt() != null && user.getRecoveryTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("O código de recuperação expirou. Solicite um novo código no aplicativo.");
        }

        return user;
    }

    /**
     * Checks a recovery code without consuming it, so the app can validate step 2
     * of the recovery flow before asking for the new password.
     */
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
    public String resetPassword(ResetPasswordDTO dto) {
        if (dto == null || dto.getToken() == null || dto.getToken().trim().isBlank()) {
            throw new IllegalArgumentException("Token ou código de recuperação não informado.");
        }

        User user = resolveRecoveryToken(dto.getEmail(), dto.getToken());

        // BCrypt hash of the new password
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setRecoveryToken(null);
        user.setRecoveryTokenExpiresAt(null);
        user.setEmailVerified(true);
        userRepo.save(user);

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
    public String changePassword(String authenticatedEmail, ChangePasswordDTO dto) {
        User user = userRepo.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("A senha atual está incorreta.");
        }

        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("A nova senha deve ser diferente da senha atual.");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepo.save(user);

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
    public CodeDeliveryResultDTO requestEmailChange(String authenticatedEmail) {
        User user = userRepo.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + authenticatedEmail));

        String authorizationCode = String.format("%06d", secureRandom.nextInt(1_000_000));
        user.setCurrentEmailChangeToken(authorizationCode);
        user.setCurrentEmailChangeTokenExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES));
        userRepo.save(user);

        boolean delivered = emailService.sendCurrentEmailChangeCode(user.getEmail(), user.getName(), authorizationCode);

        try {
            auditLogRepo.save(new AuditLog(user, "LGPD_EMAIL_CHANGE_REQUESTED", "Código de autorização gerado para o e-mail atual: " + user.getEmail() + " | Entregue=" + delivered));
        } catch (Exception e) {
            // Audit error ignored
        }

        CodeDeliveryResultDTO result = new CodeDeliveryResultDTO();
        result.setUser(user);
        result.setDelivered(delivered);
        result.setChannel("EMAIL");
        result.setDestination(user.getEmail());
        result.setOtpCode(authorizationCode);

        if (delivered) {
            result.setMessage("Código de autorização enviado para o seu e-mail atual (" + user.getEmail() + ").");
        } else {
            result.setMessage("Código de autorização gerado com sucesso.");
            result.setWarning("Ambiente de Desenvolvimento: Seu código OTP é " + authorizationCode);
        }

        return result;
    }

    /**
     * Step 1 (end) / Step 2 (start): validates the current e-mail code, checks the
     * availability of the new e-mail and sends the activation code to the new address.
     */
    public CodeDeliveryResultDTO confirmEmailChange(String authenticatedEmail, ConfirmEmailChangeDTO dto) {
        if (dto == null || dto.getCurrentCode() == null || dto.getCurrentCode().trim().isBlank()) {
            throw new IllegalArgumentException("O código de autorização do e-mail atual é obrigatório.");
        }
        if (dto.getNewEmail() == null || dto.getNewEmail().trim().isBlank()) {
            throw new IllegalArgumentException("O novo e-mail é obrigatório.");
        }

        String newEmail = dto.getNewEmail().trim().toLowerCase();
        User user = userRepo.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + authenticatedEmail));

        if (user.getEmail().equalsIgnoreCase(newEmail)) {
            throw new IllegalArgumentException("O novo e-mail não pode ser idêntico ao e-mail atual.");
        }

        if (user.getCurrentEmailChangeToken() == null || !user.getCurrentEmailChangeToken().equals(dto.getCurrentCode().trim())) {
            throw new IllegalArgumentException("Código de autorização do e-mail atual incorreto.");
        }

        if (user.getCurrentEmailChangeTokenExpiresAt() != null && user.getCurrentEmailChangeTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("O código de autorização expirou. Solicite um novo envio no aplicativo.");
        }

        if (userRepo.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("O novo e-mail informado já está vinculado a outra conta no SpotMeet.");
        }

        // Activation code for the new e-mail
        String newEmailCode = String.format("%06d", secureRandom.nextInt(1_000_000));
        user.setPendingNewEmail(newEmail);
        user.setNewEmailToken(newEmailCode);
        user.setNewEmailTokenExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES));
        userRepo.save(user);

        boolean delivered = emailService.sendNewEmailConfirmationCode(newEmail, user.getName(), newEmailCode);

        try {
            auditLogRepo.save(new AuditLog(user, "LGPD_EMAIL_CHANGE_STEP1_CONFIRMED", "Código enviado para novo endereço pendente: " + newEmail + " | Entregue=" + delivered));
        } catch (Exception e) {
            // Ignored
        }

        CodeDeliveryResultDTO result = new CodeDeliveryResultDTO();
        result.setUser(user);
        result.setDelivered(delivered);
        result.setChannel("EMAIL");
        result.setDestination(newEmail);
        result.setOtpCode(newEmailCode);

        if (delivered) {
            result.setMessage("Código de ativação enviado com sucesso para o novo e-mail (" + newEmail + ").");
        } else {
            result.setMessage("Código enviado para o novo e-mail.");
            result.setWarning("Ambiente de Desenvolvimento: Seu código OTP para o novo e-mail é " + newEmailCode);
        }

        return result;
    }

    /**
     * Step 2 (end): validates the code received on the new e-mail, updates the e-mail,
     * clears temporary tokens and records the LGPD audit entry.
     */
    public User completeEmailChange(String authenticatedEmail, CompleteEmailChangeDTO dto) {
        if (dto == null || dto.getNewEmailCode() == null || dto.getNewEmailCode().trim().isBlank()) {
            throw new IllegalArgumentException("O código enviado ao novo e-mail é obrigatório.");
        }

        User user = userRepo.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + authenticatedEmail));

        String newEmail = user.getPendingNewEmail();
        if (newEmail == null || newEmail.isBlank()) {
            throw new IllegalArgumentException("Nenhuma solicitação de troca de e-mail pendente encontrada. Inicie o processo novamente.");
        }

        if (user.getNewEmailToken() == null || !user.getNewEmailToken().equals(dto.getNewEmailCode().trim())) {
            throw new IllegalArgumentException("Código de ativação do novo e-mail incorreto.");
        }

        if (user.getNewEmailTokenExpiresAt() != null && user.getNewEmailTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("O código de ativação do novo e-mail expirou. Por favor, reinicie a solicitação.");
        }

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
}
