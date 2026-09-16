package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.AuthResponseDTO;
import com.spotmeet.backend.dto.CodeDeliveryResultDTO;
import com.spotmeet.backend.dto.ForgotPasswordDTO;
import com.spotmeet.backend.dto.LoginRequestDTO;
import com.spotmeet.backend.dto.RegisterRequestDTO;
import com.spotmeet.backend.dto.ResendVerificationDTO;
import com.spotmeet.backend.dto.ResetPasswordDTO;
import com.spotmeet.backend.dto.VerifyRecoveryCodeDTO;
import com.spotmeet.backend.dto.VerifyEmailDTO;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.security.JwtUtil;
import com.spotmeet.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication controller, public routes (/api/auth/**).
 * Registration with confirmation code, secure login, e-mail verification
 * and password recovery with BCrypt.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * POST /api/auth/register
     * Registers a new user and sends the confirmation code.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDTO dto) {
        try {
            CodeDeliveryResultDTO result = userService.registerUser(dto);
            User newUser = result.getUser();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", result.getMessage());
            response.put("email", newUser.getEmail());
            response.put("name", newUser.getName());
            response.put("channel", "EMAIL");
            response.put("delivered", result.isDelivered());
            response.put("emailVerified", false);

            if (!result.isDelivered()) {
                response.put("devCode", result.getOtpCode());
                response.put("warning", result.getWarning());
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
    }

    /**
     * POST /api/auth/login
     * Authenticates the user; blocks accounts whose e-mail is not verified yet.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO dto) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword())
            );

            User user = (User) authentication.getPrincipal();
            String token = jwtUtil.generateToken(user.getEmail(), user.getRole());

            return ResponseEntity.ok(new AuthResponseDTO(
                    token,
                    user.getRole(),
                    user.getName(),
                    user.getEmail(),
                    user.getId(),
                    user.isEmailVerified()
            ));

        } catch (DisabledException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "EMAIL_NOT_VERIFIED");
            errorResponse.put("message", "Sua conta ainda não foi verificada. Digite o código enviado para o seu e-mail para ativá-la.");
            errorResponse.put("email", dto.getEmail());
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(errorResponse);

        } catch (BadCredentialsException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "BAD_CREDENTIALS");
            errorResponse.put("message", "E-mail ou senha incorretos.");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse);
        }
    }

    /**
     * POST /api/auth/verify-email
     * Validates the code received by the user and activates the account.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailDTO dto) {
        try {
            String message = userService.verifyEmail(dto);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", message);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * POST /api/auth/resend-verification
     * Generates and resends a new verification code to the given e-mail.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationDTO dto) {
        CodeDeliveryResultDTO result = userService.resendVerification(dto);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", result.getMessage());
        response.put("channel", result.getChannel());
        response.put("delivered", result.isDelivered());

        if (!result.isDelivered() && result.getOtpCode() != null) {
            response.put("devCode", result.getOtpCode());
            response.put("warning", result.getWarning());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/forgot-password
     * Starts the recovery flow: generates a temporary code and sends it by e-mail
     * without revealing whether the account exists.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto) {
        CodeDeliveryResultDTO result = userService.forgotPassword(dto);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", result.getMessage());
        response.put("channel", result.getChannel());
        response.put("delivered", result.isDelivered());

        if (!result.isDelivered() && result.getOtpCode() != null) {
            response.put("devCode", result.getOtpCode());
            response.put("warning", result.getWarning());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/verify-recovery-code
     * Validates the recovery code without consuming it, so the app can advance from the
     * code step to the new-password step only when the code is really valid.
     */
    @PostMapping("/verify-recovery-code")
    public ResponseEntity<?> verifyRecoveryCode(@Valid @RequestBody VerifyRecoveryCodeDTO dto) {
        try {
            String message = userService.verifyRecoveryCode(dto);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", message);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * POST /api/auth/reset-password
     * Validates the code and updates the password with BCrypt.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        try {
            String message = userService.resetPassword(dto);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", message);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
}
