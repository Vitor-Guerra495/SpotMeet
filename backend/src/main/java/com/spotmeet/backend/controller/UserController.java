package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.*;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.security.JwtUtil;
import com.spotmeet.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Profile controller: profile data, bio, availability, preferences
 * and the secure e-mail change flow (LGPD).
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * GET /api/users/me
     * Returns the full profile of the authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            UserProfileResponseDTO profile = userService.getMyProfile(authentication.getName());
            return ResponseEntity.ok(profile);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao buscar perfil: " + e.getMessage());
        }
    }

    /**
     * PUT /api/users/me
     * Updates name, bio, availability status and preferred theme of the authenticated user.
     */
    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(
            @Valid @RequestBody UpdateUserProfileDTO dto,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            UserProfileResponseDTO updated = userService.updateMyProfile(authentication.getName(), dto);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao atualizar perfil: " + e.getMessage());
        }
    }

    /**
     * PUT /api/users/me/password
     * Changes the password of the authenticated user (requires the current password).
     */
    @PutMapping("/me/password")
    public ResponseEntity<?> changeMyPassword(
            @Valid @RequestBody ChangePasswordDTO dto,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        Map<String, Object> response = new HashMap<>();
        try {
            String message = userService.changePassword(authentication.getName(), dto);
            response.put("success", true);
            response.put("message", message);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Erro ao alterar senha: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * POST /api/users/me/email-change/request
     * Step 1: sends a security code to the current e-mail of the authenticated user.
     */
    @PostMapping("/me/email-change/request")
    public ResponseEntity<?> requestEmailChange(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            CodeDeliveryResultDTO result = userService.requestEmailChange(authentication.getName());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", result.getMessage());
            response.put("currentEmail", result.getDestination());
            if (!result.isDelivered() && result.getOtpCode() != null) {
                response.put("devCode", result.getOtpCode());
                response.put("warning", result.getWarning());
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Erro ao processar solicitação de troca de e-mail: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * POST /api/users/me/email-change/confirm
     * Step 1 (end) / Step 2 (start): validates the current e-mail code and sends the activation code to the new e-mail.
     */
    @PostMapping("/me/email-change/confirm")
    public ResponseEntity<?> confirmEmailChange(
            @Valid @RequestBody ConfirmEmailChangeDTO dto,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            CodeDeliveryResultDTO result = userService.confirmEmailChange(authentication.getName(), dto);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", result.getMessage());
            response.put("newEmail", result.getDestination());
            if (!result.isDelivered() && result.getOtpCode() != null) {
                response.put("devCode", result.getOtpCode());
                response.put("warning", result.getWarning());
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Erro ao confirmar etapa 1 da troca de e-mail: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * POST /api/users/me/email-change/complete
     * Step 2 (end): validates the new e-mail code, saves it and returns a new JWT.
     */
    @PostMapping("/me/email-change/complete")
    public ResponseEntity<?> completeEmailChange(
            @Valid @RequestBody CompleteEmailChangeDTO dto,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            User updatedUser = userService.completeEmailChange(authentication.getName(), dto);
            String newToken = jwtUtil.generateToken(updatedUser.getEmail(), updatedUser.getRole());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "E-mail alterado com sucesso!");
            response.put("newEmail", updatedUser.getEmail());
            response.put("token", newToken);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Erro ao concluir alteração de e-mail: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
